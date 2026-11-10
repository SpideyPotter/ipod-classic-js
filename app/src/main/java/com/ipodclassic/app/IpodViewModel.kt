package com.ipodclassic.app

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal enum class Page { HOME, MUSIC, LIBRARY, DETAIL, SEARCH, NOW_PLAYING, SOURCES, SETTINGS, ABOUT, COVER_FLOW, LYRICS }
internal data class Route(val page: Page, val title: String, val kind: MediaKind? = null, val item: MediaItem? = null)
internal data class AppState(
    val route: Route = Route(Page.HOME, "iPod Classic"),
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val next: String? = null,
    val error: String? = null,
    val signedIn: Boolean = false,
    val clientId: String = "",
    val account: String? = null,
    val playback: Playback = Playback(),
    val query: String = "",
    val darkShell: Boolean = true,
    val haptics: Boolean = true,
    val authBusy: Boolean = false,
)

internal class IpodViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("ipod_settings", 0)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).build()
    private val auth = SpotifyAuth(application, http)
    private val repository = SpotifyRepository(auth, http)
    private val mutable = MutableStateFlow(AppState(signedIn = auth.signedIn, clientId = auth.clientId,
        darkShell = preferences.getBoolean("dark", true), haptics = preferences.getBoolean("haptics", true)))
    val state = mutable.asStateFlow()
    private val stack = mutableListOf<Route>()
    val canGoBack: Boolean get() = stack.isNotEmpty()
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var authJob: Job? = null
    private var authGeneration = 0
    private var wantsRemote = false
    private val remote = SpotifyRemote(application,
        onState = { playback -> mutable.update { it.copy(playback = playback) } },
        onError = { message -> mutable.update { it.copy(error = message) } })

    init { if (auth.signedIn) loadProfile() }
    fun navigate(route: Route) {
        stack.add(mutable.value.route)
        show(route)
    }
    private fun show(route: Route) {
        loadGeneration++
        loadJob?.cancel()
        mutable.update { it.copy(route = route, items = emptyList(), next = null, loading = false, error = null) }
        if (route.page in listOf(Page.LIBRARY, Page.DETAIL, Page.COVER_FLOW)) load()
    }
    fun back() { if (stack.isNotEmpty()) show(stack.removeAt(stack.lastIndex)) }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun reportError(message: String) { mutable.update { it.copy(error = message) } }

    fun configure(clientId: String) {
        try {
            require(clientId.trim().matches(Regex("[a-fA-F0-9]{32}"))) { "Enter the 32-character client ID from your Spotify developer app." }
            val changed = clientId.trim() != auth.clientId
            if (changed) { authJob?.cancel(); authGeneration++; loadGeneration++; loadJob?.cancel(); wantsRemote = false; remote.disconnect() }
            auth.configure(clientId.trim())
            mutable.update { it.copy(clientId = auth.clientId, signedIn = auth.signedIn, account = if (changed) null else it.account,
                items = if (changed) emptyList() else it.items, next = if (changed) null else it.next, authBusy = false, error = null) }
        } catch (error: Exception) { reportError(error.message ?: "Invalid Spotify configuration.") }
    }
    fun authorizationUri(): Uri? = try { auth.authorize() } catch (error: Exception) {
        reportError(error.message ?: "Unable to start Spotify sign-in."); null
    }
    fun callback(uri: Uri) {
        if (mutable.value.authBusy) return
        val generation = ++authGeneration
        loadGeneration++; loadJob?.cancel()
        mutable.update { it.copy(loading = false, authBusy = true, error = null) }
        authJob = viewModelScope.launch {
            try {
                auth.callback(uri)
                if (generation != authGeneration) return@launch
                wantsRemote = false; remote.disconnect()
                mutable.update { it.copy(signedIn = true, account = null, items = emptyList(), next = null) }
                loadProfile()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportError(error.message ?: "Spotify sign-in failed.")
            } finally { if (generation == authGeneration) mutable.update { it.copy(authBusy = false) } }
        }
    }
    private fun loadProfile() {
        val generation = authGeneration
        viewModelScope.launch {
            try {
                val account = repository.profile()
                if (generation == authGeneration) mutable.update { it.copy(account = account) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == authGeneration) reportError(error.message ?: "Could not load Spotify account.")
            }
        }
    }
    fun signOut() {
        authGeneration++; loadGeneration++; authJob?.cancel(); loadJob?.cancel(); wantsRemote = false; remote.disconnect(); auth.signOut()
        stack.clear()
        stack.add(Route(Page.HOME, "iPod Classic"))
        mutable.update { it.copy(route = Route(Page.SOURCES, "Music Sources"), signedIn = false, account = null,
            items = emptyList(), next = null, loading = false, authBusy = false, error = null) }
    }
    fun setQuery(value: String) { mutable.update { it.copy(query = value) } }
    fun load(more: Boolean = false) {
        val snapshot = mutable.value
        if (snapshot.loading || (more && snapshot.next == null)) return
        if (!snapshot.signedIn) { reportError("Sign in to Spotify in Music Sources to browse your library."); return }
        loadJob?.cancel()
        val generation = ++loadGeneration
        val route = snapshot.route
        val query = snapshot.query.trim()
        val next = if (more) snapshot.next else null
        if (route.page == Page.SEARCH && query.isBlank()) return
        mutable.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = when (route.page) {
                    Page.LIBRARY -> repository.library(requireNotNull(route.kind), next)
                    Page.COVER_FLOW -> repository.library(MediaKind.ALBUM, next)
                    Page.DETAIL -> repository.detail(requireNotNull(route.item), next)
                    Page.SEARCH -> repository.search(query, next)
                    else -> return@launch
                }
                if (generation != loadGeneration) return@launch
                mutable.update { it.copy(items = if (more) it.items + result.items else result.items, next = result.next) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == loadGeneration) reportError(error.message ?: "Unable to fetch Spotify content.")
            } finally { if (generation == loadGeneration) mutable.update { it.copy(loading = false) } }
        }
    }
    private fun playbackReady(): Boolean {
        if (mutable.value.playback.connected) return true
        navigate(Route(Page.SOURCES, "Music Sources"))
        reportError("Connect Spotify playback before playing music.")
        return false
    }
    fun select(item: MediaItem) {
        if (item.kind == MediaKind.TRACK && !playbackReady()) return
        if (item.kind == MediaKind.TRACK) { remote.play(item.uri); navigate(Route(Page.NOW_PLAYING, "Now Playing")) }
        else navigate(Route(Page.DETAIL, item.title, item = item))
    }
    fun playContext(item: MediaItem) { if (!playbackReady()) return; remote.play(item.uri); navigate(Route(Page.NOW_PLAYING, "Now Playing")) }
    fun connectPlayback() { wantsRemote = true; remote.connect(auth.clientId) }
    fun onForeground() { remote.onForeground(); if (wantsRemote) remote.connect(auth.clientId, showAuth = false) }
    fun onBackground() { remote.onBackground() }
    fun togglePlayback() = remote.toggle()
    fun nextTrack() = remote.next()
    fun previousTrack() = remote.previous()
    fun seek(position: Long) = remote.seek(position)
    fun toggleDark() {
        val value = !mutable.value.darkShell
        preferences.edit { putBoolean("dark", value) }; mutable.update { it.copy(darkShell = value) }
    }
    fun toggleHaptics() {
        val value = !mutable.value.haptics
        preferences.edit { putBoolean("haptics", value) }; mutable.update { it.copy(haptics = value) }
    }
    override fun onCleared() { remote.disconnect(); super.onCleared() }
}
