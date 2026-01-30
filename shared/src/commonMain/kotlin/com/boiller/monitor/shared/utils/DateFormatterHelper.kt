package com.boiller.monitor.shared.utils

/**
 * Helper функції для зручного виклику з Swift/iOS
 * Top-level функції експортуються простіше ніж object
 */
fun formatTime(timestamp: String): String {
    return DateFormatter.formatTime(timestamp)
}

fun formatTimeDifference(laterTimestamp: String, earlierTimestamp: String): String? {
    return DateFormatter.formatTimeDifference(laterTimestamp, earlierTimestamp)
}
