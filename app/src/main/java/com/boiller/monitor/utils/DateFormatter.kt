package com.boiller.monitor.utils

object DateFormatter {
    fun formatTime(timestamp: String): String {
        // Просто витягуємо час з timestamp без зміни часового поясу
        return try {
            if (timestamp.contains("T")) {
                val timePart = timestamp.substringAfter("T").substringBefore(".")
                if (timePart.length >= 8) {
                    timePart.substring(0, 8) // HH:mm:ss
                } else if (timePart.length >= 5) {
                    timePart.substring(0, 5) + ":00" // HH:mm -> HH:mm:ss
                } else {
                    timestamp
                }
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }
}
