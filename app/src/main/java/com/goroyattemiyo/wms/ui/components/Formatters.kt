package com.goroyattemiyo.wms.ui.components

fun formatDuration(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

fun formatPlaybackTime(milliseconds: Long): String =
    formatDuration((milliseconds.coerceAtLeast(0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

fun formatFileSize(bytes: Long): String = "%.1f MB".format(bytes.coerceAtLeast(0L) / 1_048_576.0)
