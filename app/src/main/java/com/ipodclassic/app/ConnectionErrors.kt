package com.ipodclassic.app

/** Show SDK error types without exposing provider payloads, tokens or redirect codes. */
internal fun playbackConnectionError(error: Throwable): String {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && causes.size < 5 && causes.none { it === current }) {
        causes.add(current)
        current = current.cause
    }
    val names = causes.map { it.javaClass.simpleName.ifBlank { "UnknownError" } }.distinct()
    val guidance = when {
        names.any { it == "NotLoggedInException" } -> "Open Spotify and sign in, then reconnect playback."
        names.any { it == "OfflineModeException" } -> "Turn off Spotify offline mode and check your internet connection."
        names.any { it == "CouldNotFindSpotifyApp" } -> "Install or update the standard Spotify Android app, then reconnect."
        names.any { it == "UserNotAuthorizedException" || it == "AuthenticationFailedException" } ->
            "Spotify rejected playback authorization. Check the saved client ID, exact redirect URI and your developer app’s account access. Browser library sign-in is separate from playback authorization."
        names.any { it == "UnsupportedFeatureVersionException" } -> "Update the Spotify Android app and reconnect."
        else -> "Spotify playback connection failed. Open Spotify, confirm you are signed in, then reconnect."
    }
    return "$guidance\n\nSDK error: ${names.joinToString(" → ")}"
}
