package com.boiller.monitor.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration

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
    
    /**
     * Підпис для довгих періодів (7d): dd.MM HH:mm
     */
    fun formatDateTime(timestamp: String): String {
        val parsed = parseTimestamp(timestamp) ?: return formatTime(timestamp)
        val day = parsed.dayOfMonth.toString().padStart(2, '0')
        val month = parsed.monthNumber.toString().padStart(2, '0')
        val hour = parsed.hour.toString().padStart(2, '0')
        val minute = parsed.minute.toString().padStart(2, '0')
        return "$day.$month $hour:$minute"
    }

    /**
     * Скільки хвилин минуло від timestamp до зараз (null, якщо не розпарсився)
     */
    fun minutesSince(timestamp: String): Long? {
        val parsed = parseTimestamp(timestamp) ?: return null
        val instant = parsed.toInstant(TimeZone.currentSystemDefault())
        val now = kotlinx.datetime.Clock.System.now()
        return (now - instant).inWholeMinutes
    }

    /**
     * Текст попередження, якщо дані старші за 5 хвилин, інакше null
     */
    fun staleWarning(timestamp: String): String? {
        val minutes = minutesSince(timestamp) ?: return null
        if (minutes < 5) return null
        val age = when {
            minutes < 60 -> "$minutes хв"
            minutes < 24 * 60 -> "${minutes / 60} год"
            else -> "${minutes / 60 / 24} дн"
        }
        return "⚠ $age"
    }

    /**
     * Обчислює різницю часу між двома timestamp у форматі hh:mm:ss
     */
    fun formatTimeDifference(laterTimestamp: String, earlierTimestamp: String): String? {
        return try {
            val later = parseTimestamp(laterTimestamp) ?: return null
            val earlier = parseTimestamp(earlierTimestamp) ?: return null
            
            val timeZone = TimeZone.currentSystemDefault()
            val laterInstant = later.toInstant(timeZone)
            val earlierInstant = earlier.toInstant(timeZone)
            val duration = laterInstant - earlierInstant
            
            if (duration.inWholeSeconds < 0) {
                return null
            }
            
            val totalSeconds = duration.inWholeSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTimestamp(timestamp: String): LocalDateTime? {
        return try {
            val cleanTimestamp = if (timestamp.contains(".")) {
                timestamp.substringBefore(".")
            } else {
                timestamp
            }
            
            // Парсимо формат yyyy-MM-ddTHH:mm:ss
            val parts = cleanTimestamp.split("T")
            if (parts.size != 2) return null
            
            val dateParts = parts[0].split("-")
            val timeParts = parts[1].split(":")
            
            if (dateParts.size != 3 || timeParts.size < 2) return null
            
            LocalDateTime(
                year = dateParts[0].toInt(),
                monthNumber = dateParts[1].toInt(),
                dayOfMonth = dateParts[2].toInt(),
                hour = timeParts[0].toInt(),
                minute = timeParts[1].toInt(),
                second = if (timeParts.size >= 3) timeParts[2].toInt() else 0
            )
        } catch (e: Exception) {
            null
        }
    }
}
