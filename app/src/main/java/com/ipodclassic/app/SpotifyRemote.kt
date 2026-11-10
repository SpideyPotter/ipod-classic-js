package com.ipodclassic.app

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Empty
import com.spotify.protocol.types.PlayerState

internal data class Playback(
    val connected: Boolean = false,
    val title: String = "Nothing playing",
    val artist: String = "Connect to the Spotify app",
    val paused: Boolean = true,
    val position: Long = 0,
    val duration: Long = 0,
    val artwork: android.graphics.Bitmap? = null,
)

internal class SpotifyRemote(
    private val context: Context,
    private val onState: (Playback) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var remote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null
    private var playback = Playback()
    private var generation = 0
    private var connecting = false
    private var foreground = true
    private var artworkUri: String? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (remote?.isConnected != true) return
            if (!playback.paused) {
                playback = playback.copy(position = (playback.position + 1000).coerceAtMost(playback.duration))
                onState(playback)
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun connect(clientId: String, showAuth: Boolean = true) {
        if (remote?.isConnected == true || connecting) return
        if (clientId.isBlank()) { onError("Set your Spotify client ID in Music Sources."); return }
        if (!SpotifyAppRemote.isSpotifyInstalled(context)) { onError("Install the Spotify Android app before connecting playback."); return }
        connecting = true
        val requestGeneration = ++generation
        val params = ConnectionParams.Builder(clientId).setRedirectUri(REDIRECT_URI).showAuthView(showAuth).build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                if (requestGeneration != generation) { SpotifyAppRemote.disconnect(appRemote); return }
                connecting = false
                if (!foreground) { SpotifyAppRemote.disconnect(appRemote); return }
                remote = appRemote
                playback = playback.copy(connected = true)
                onState(playback)
                handler.removeCallbacks(progressTick)
                handler.postDelayed(progressTick, 1000)
                subscription = appRemote.playerApi.subscribeToPlayerState().setEventCallback { state ->
                    if (requestGeneration != generation) return@setEventCallback
                    playback = Playback(true, state.track?.name ?: "Nothing playing", state.track?.artist?.name.orEmpty(),
                        state.isPaused, state.playbackPosition, state.track?.duration ?: 0,
                        if (artworkUri == state.track?.imageUri?.raw) playback.artwork else null)
                    onState(playback)
                    val image = state.track?.imageUri
                    val imageKey = image?.raw
                    if (image != null && artworkUri != imageKey) {
                        artworkUri = imageKey
                        appRemote.imagesApi.getImage(image).setResultCallback { bitmap ->
                            if (requestGeneration == generation && artworkUri == imageKey) {
                                playback = playback.copy(artwork = bitmap)
                                onState(playback)
                            }
                        }
                    }
                }
                subscription?.setErrorCallback { onError("Spotify playback updates stopped. Reconnect in Music Sources.") }
            }
            override fun onFailure(error: Throwable) {
                if (requestGeneration != generation) return
                connecting = false
                remote = null
                playback = playback.copy(connected = false)
                onState(playback)
                onError(playbackConnectionError(error))
            }
        })
    }
    private fun command(action: (SpotifyAppRemote) -> CallResult<Empty>) {
        val current = remote
        if (current?.isConnected != true) { onError("Connect playback in Music Sources first."); return }
        action(current).setErrorCallback { onError("Spotify could not complete playback. Check account capabilities and reconnect if needed.") }
    }
    fun play(uri: String) = command { it.playerApi.play(uri) }
    fun toggle() = command { if (playback.paused) it.playerApi.resume() else it.playerApi.pause() }
    fun next() = command { it.playerApi.skipNext() }
    fun previous() = command { it.playerApi.skipPrevious() }
    fun seek(position: Long) = command { it.playerApi.seekTo(position.coerceIn(0, playback.duration)) }
    fun onForeground() { foreground = true }
    fun onBackground() {
        foreground = false
        // Spotify's authorization screen can temporarily background our Activity.
        // Keep the in-flight connection alive so its authorization result is delivered.
        if (!connecting) disconnect()
    }
    fun disconnect() {
        generation++
        connecting = false
        artworkUri = null
        handler.removeCallbacks(progressTick)
        subscription?.cancel(); subscription = null
        remote?.let { SpotifyAppRemote.disconnect(it) }; remote = null
        playback = playback.copy(connected = false)
        onState(playback)
    }
}
