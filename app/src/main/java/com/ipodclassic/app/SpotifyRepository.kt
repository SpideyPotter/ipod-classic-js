package com.ipodclassic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal enum class MediaKind { TRACK, ALBUM, ARTIST, PLAYLIST }
internal data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: MediaKind,
    val uri: String,
    val artwork: String? = null,
)
internal data class MediaPage(val items: List<MediaItem>, val next: String?)
internal class SpotifyHttpException(val status: Int, message: String) : Exception(message)

internal class SpotifyRepository(private val auth: SpotifyAuth, private val http: OkHttpClient) {
    suspend fun library(kind: MediaKind, next: String? = null): MediaPage {
        val endpoint = when (kind) {
            MediaKind.PLAYLIST -> "me/playlists?limit=20"
            MediaKind.ALBUM -> "me/albums?limit=20"
            MediaKind.ARTIST -> "me/following?type=artist&limit=20"
            MediaKind.TRACK -> "me/tracks?limit=20"
        }
        val root = get(next ?: endpoint)
        val data = if (kind == MediaKind.ARTIST) root.getJSONObject("artists") else root
        return page(data, kind, if (kind == MediaKind.ALBUM) "album" else if (kind == MediaKind.TRACK) "track" else null)
    }

    suspend fun detail(item: MediaItem, next: String? = null): MediaPage {
        if (next != null) return page(get(next), if (item.kind == MediaKind.ARTIST) MediaKind.ALBUM else MediaKind.TRACK,
            if (item.kind == MediaKind.PLAYLIST) "track" else null)
        return when (item.kind) {
            MediaKind.PLAYLIST -> {
                val data = get("playlists/${item.id}")
                page(data.optJSONObject("tracks") ?: data.optJSONObject("items") ?: error("Spotify did not provide playlist tracks. Access may be restricted."), MediaKind.TRACK, "track")
            }
            MediaKind.ALBUM -> page(get("albums/${item.id}/tracks?limit=50"), MediaKind.TRACK)
            MediaKind.ARTIST -> page(get("artists/${item.id}/albums?limit=20"), MediaKind.ALBUM)
            MediaKind.TRACK -> MediaPage(emptyList(), null)
        }
    }

    suspend fun search(query: String, next: String? = null): MediaPage {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val root = get(next ?: "search?q=$encoded&type=track&limit=10")
        return page(root.getJSONObject("tracks"), MediaKind.TRACK)
    }

    suspend fun profile(): String = get("me").let { it.optString("display_name").ifBlank { it.getString("id") } }

    private suspend fun get(endpoint: String): JSONObject {
        val url = (if (endpoint.startsWith("https://")) endpoint else "https://api.spotify.com/v1/$endpoint").toHttpUrl()
        require(url.scheme == "https" && url.host == "api.spotify.com" && url.port == 443 && url.encodedPath.startsWith("/v1/")) { "Invalid Spotify pagination URL." }
        var token = auth.accessToken()
        for (attempt in 0..1) {
            val result = withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use {
                    Triple(it.code, it.body?.string().orEmpty(), it.header("Retry-After"))
                }
            }
            if (result.first == 401 && attempt == 0) { token = auth.accessToken(forceRefresh = true); continue }
            if (result.first !in 200..299) throw SpotifyHttpException(result.first, when (result.first) {
                401 -> "Spotify session expired. Sign in again."
                403 -> "Spotify denied access. Check account access, app permissions and Spotify developer restrictions."
                404 -> "This Spotify item is unavailable or inaccessible to this app."
                429 -> "Spotify request limit reached. Try again in ${result.third ?: "a few"} seconds."
                else -> "Spotify request failed (${result.first}). Please retry."
            })
            return JSONObject(result.second)
        }
        error("Unable to authenticate with Spotify.")
    }

    private fun page(data: JSONObject, kind: MediaKind, wrapper: String? = null): MediaPage {
        val array = data.optJSONArray("items") ?: error("Spotify returned an unexpected library response. Please retry or check API access.")
        val items = (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val value = if (wrapper != null) raw.optJSONObject(wrapper)
                ?: raw.optJSONObject("item") ?: return@mapNotNull null else raw
            if (value.optBoolean("is_local") || value.optString("id").isBlank()) return@mapNotNull null
            if (kind == MediaKind.TRACK && value.optString("type", "track") != "track") return@mapNotNull null
            val artists = value.optJSONArray("artists")
            val subtitle = when (kind) {
                MediaKind.PLAYLIST -> value.optJSONObject("owner")?.optString("display_name").orEmpty()
                MediaKind.ARTIST -> "Artist"
                else -> (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it)?.optString("name") }.joinToString(", ")
            }
            val images = value.optJSONArray("images") ?: value.optJSONObject("album")?.optJSONArray("images")
            MediaItem(value.getString("id"), value.optString("name", "Untitled"), subtitle, kind,
                value.optString("uri", "spotify:${kind.name.lowercase()}:${value.getString("id")}"),
                images?.optJSONObject(0)?.optString("url")?.takeIf { it.startsWith("https://") })
        }
        return MediaPage(items, data.optString("next").takeIf { it.isNotBlank() && it != "null" })
    }
}
