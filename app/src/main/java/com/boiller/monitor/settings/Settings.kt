package com.boiller.monitor.settings

import android.content.Context
import android.content.SharedPreferences

class Settings(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("boiller_prefs", Context.MODE_PRIVATE)
    }
    
    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_NOTIFICATION_START_HOUR = "notification_start_hour"
        const val KEY_NOTIFICATION_END_HOUR = "notification_end_hour"
        const val DEFAULT_SERVER_URL = "http://82.193.113.73:8080/"
        const val DEFAULT_NOTIFICATION_START_HOUR = 8
        const val DEFAULT_NOTIFICATION_END_HOUR = 22
    }
    
    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getNotificationStartHour(): Int {
        return prefs.getInt(KEY_NOTIFICATION_START_HOUR, DEFAULT_NOTIFICATION_START_HOUR)
    }
    
    fun getNotificationEndHour(): Int {
        return prefs.getInt(KEY_NOTIFICATION_END_HOUR, DEFAULT_NOTIFICATION_END_HOUR)
    }
    
    fun setNotificationHours(startHour: Int, endHour: Int) {
        prefs.edit()
            .putInt(KEY_NOTIFICATION_START_HOUR, startHour)
            .putInt(KEY_NOTIFICATION_END_HOUR, endHour)
            .apply()
    }
}
