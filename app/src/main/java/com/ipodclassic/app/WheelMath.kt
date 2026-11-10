package com.ipodclassic.app

/** Shortest signed angular change, including the +/-180 degree boundary. */
internal fun angularDelta(previous: Float, current: Float): Float =
    ((current - previous + 540f) % 360f) - 180f

internal fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
