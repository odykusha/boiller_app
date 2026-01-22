package com.boiller.monitor.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale("uk", "UA"))
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale("uk", "UA"))
    
    fun formatTime(timestamp: String): String {
        return try {
            val instant = Instant.parse(timestamp)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dateTime.format(timeFormatter)
        } catch (e: Exception) {
            timestamp
        }
    }
    
    fun formatDateTime(timestamp: String): String {
        return try {
            val instant = Instant.parse(timestamp)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dateTime.format(dateTimeFormatter)
        } catch (e: Exception) {
            timestamp
        }
    }
}
