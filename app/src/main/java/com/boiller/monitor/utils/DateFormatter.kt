package com.boiller.monitor.utils

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateFormatter {
    private val isoFormatter = DateTimeFormatter.ISO_DATE_TIME
    
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
    
    /**
     * Обчислює різницю часу між двома timestamp у форматі hh:mm:ss
     * @param laterTimestamp більш пізній timestamp
     * @param earlierTimestamp більш ранній timestamp
     * @return різниця у форматі hh:mm:ss або null якщо не вдалося обчислити
     */
    fun formatTimeDifference(laterTimestamp: String, earlierTimestamp: String): String? {
        return try {
            val later = parseTimestamp(laterTimestamp) ?: return null
            val earlier = parseTimestamp(earlierTimestamp) ?: return null
            
            val duration = Duration.between(earlier, later)
            val totalSeconds = duration.seconds
            
            if (totalSeconds < 0) {
                return null // Якщо laterTimestamp раніше за earlierTimestamp
            }
            
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTimestamp(timestamp: String): LocalDateTime? {
        return try {
            // Видаляємо мілісекунди та мікросекунди, якщо вони є
            val cleanTimestamp = if (timestamp.contains(".")) {
                timestamp.substringBefore(".")
            } else {
                timestamp
            }
            
            // Парсимо формат yyyy-MM-ddTHH:mm:ss
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime.parse(cleanTimestamp, formatter)
        } catch (e: Exception) {
            try {
                // Спробуємо ISO формат як fallback
                LocalDateTime.parse(timestamp.substringBefore("."), isoFormatter)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
