package com.boiller.monitor.shared.settings

import android.content.Context
import android.content.SharedPreferences

actual class Settings(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("boiller_prefs", Context.MODE_PRIVATE)
    }
    
    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_NOTIFICATION_START_HOUR = "notification_start_hour"
        const val KEY_NOTIFICATION_END_HOUR = "notification_end_hour"
        const val DEFAULT_SERVER_URL = "http://192.168.50.100:8080/"
        const val DEFAULT_NOTIFICATION_START_HOUR = 8
        const val DEFAULT_NOTIFICATION_END_HOUR = 22
    }
    
    actual fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    actual fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    actual fun getNotificationStartHour(): Int {
        return prefs.getInt(KEY_NOTIFICATION_START_HOUR, DEFAULT_NOTIFICATION_START_HOUR)
    }
    
    actual fun getNotificationEndHour(): Int {
        return prefs.getInt(KEY_NOTIFICATION_END_HOUR, DEFAULT_NOTIFICATION_END_HOUR)
    }
    
    actual fun setNotificationHours(startHour: Int, endHour: Int) {
        prefs.edit()
            .putInt(KEY_NOTIFICATION_START_HOUR, startHour)
            .putInt(KEY_NOTIFICATION_END_HOUR, endHour)
            .apply()
    }
}
