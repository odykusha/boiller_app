package com.boiller.monitor.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale("uk", "UA"))
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale("uk", "UA"))
    
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
    
    fun formatDateTime(timestamp: String): String {
        return try {
            val instant = when {
                timestamp.contains("T") -> {
                    try {
                        Instant.parse(timestamp)
                    } catch (e: DateTimeParseException) {
                        val cleaned = timestamp.substringBefore(".") + "Z"
                        Instant.parse(cleaned)
                    }
                }
                else -> Instant.parse(timestamp)
            }
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dateTime.format(dateTimeFormatter)
        } catch (e: Exception) {
            timestamp
        }
    }
}
