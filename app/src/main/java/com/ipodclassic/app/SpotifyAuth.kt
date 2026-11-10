package com.ipodclassic.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.core.net.toUri
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal const val REDIRECT_URI = "com.ipodclassic.app://callback"
internal val spotifyScopes = listOf(
    "playlist-read-private", "playlist-read-collaborative", "user-library-read", "user-follow-read", "user-read-private",
)

internal object Pkce {
    fun random(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let(::encode)
    fun challenge(verifier: String): String = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    private fun encode(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun validState(expected: String?, actual: String?): Boolean = !expected.isNullOrBlank() && !actual.isNullOrBlank() &&
        MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}

/** Tokens and in-flight PKCE values are encrypted with an Android Keystore key. */
internal class SecureSession(context: Context) {
    private val prefs = context.getSharedPreferences("spotify_session", Context.MODE_PRIVATE)
    private val alias = "ipod_spotify_session_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(name: String): String? {
        val value = prefs.getString(name, null) ?: return null
        return try {
            val parts = value.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { prefs.edit { remove(name) }; null }
    }
    fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit { putString(name, encoded) }
    }
    fun remove(name: String) { prefs.edit { remove(name) } }
    fun clear() { prefs.edit { clear() } }
}

internal class SpotifyAuth(context: Context, private val http: OkHttpClient) {
    private val session = SecureSession(context)
    private val config = context.getSharedPreferences("spotify_config", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private var sessionGeneration = 0
    var clientId: String
        get() = config.getString("client_id", "") ?: ""
        private set(value) { config.edit { putString("client_id", value) } }
    val signedIn: Boolean get() = session.read("tokens") != null

    fun configure(value: String) {
        require(value.matches(Regex("[a-fA-F0-9]{32}"))) { "Enter the 32-character client ID from your Spotify developer app." }
        if (clientId != value) { sessionGeneration++; session.clear(); clientId = value }
    }

    fun authorize(): Uri {
        require(clientId.isNotBlank()) { "Set your Spotify client ID first." }
        val verifier = Pkce.random()
        val state = Pkce.random()
        session.write("pending", JSONObject().put("verifier", verifier).put("state", state)
            .put("created", System.currentTimeMillis()).toString())
        return "https://accounts.spotify.com/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", spotifyScopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("show_dialog", "true").build()
    }

    suspend fun callback(uri: Uri) {
        require(uri.scheme == "com.ipodclassic.app" && uri.host == "callback" && uri.path.orEmpty().isEmpty()) { "Invalid sign-in redirect." }
        val pending = session.read("pending")?.let(::JSONObject) ?: error("Sign-in expired. Please try again.")
        // Do not let an unrelated or forged redirect consume a legitimate pending login.
        require(Pkce.validState(pending.optString("state"), uri.getQueryParameter("state"))) { "Sign-in state did not match. Please try again." }
        session.remove("pending")
        require(System.currentTimeMillis() - pending.getLong("created") in 0..600_000) { "Sign-in expired. Please try again." }
        uri.getQueryParameter("error")?.let { error("Spotify sign-in was declined. Please try again.") }
        val code = uri.getQueryParameter("code") ?: error("Spotify did not return an authorization code.")
        val generation = ++sessionGeneration
        val data = tokenRequest(FormBody.Builder().add("grant_type", "authorization_code")
            .add("code", code).add("redirect_uri", REDIRECT_URI).add("client_id", clientId)
            .add("code_verifier", pending.getString("verifier")).build())
        check(generation == sessionGeneration) { "Spotify account changed during sign-in. Please try again." }
        saveTokens(data, null)
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String = refreshLock.withLock {
        val generation = sessionGeneration
        val stored = session.read("tokens")?.let(::JSONObject) ?: error("Sign in to Spotify in Music Sources.")
        if (!forceRefresh && stored.getLong("expires_at") > System.currentTimeMillis() + 60_000) return@withLock stored.getString("access_token")
        val refresh = stored.optString("refresh_token").takeIf { it.isNotBlank() } ?: error("Please sign in again.")
        val data = tokenRequest(FormBody.Builder().add("grant_type", "refresh_token")
            .add("refresh_token", refresh).add("client_id", clientId).build())
        check(generation == sessionGeneration) { "Spotify account changed. Please retry." }
        saveTokens(data, refresh)
        data.getString("access_token")
    }

    private suspend fun tokenRequest(body: FormBody): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("Spotify authentication failed (${response.code}). Please sign in again.")
            }
            JSONObject(response.body?.string() ?: error("Spotify returned an empty sign-in response."))
        }
    }
    private fun saveTokens(data: JSONObject, previousRefresh: String?) {
        require(data.optString("access_token").isNotBlank()) { "Spotify returned no access token." }
        if (!data.has("refresh_token") && previousRefresh != null) data.put("refresh_token", previousRefresh)
        data.put("expires_at", System.currentTimeMillis() + data.getLong("expires_in") * 1000)
        session.write("tokens", data.toString())
    }
    fun signOut() { sessionGeneration++; session.clear() }
}
