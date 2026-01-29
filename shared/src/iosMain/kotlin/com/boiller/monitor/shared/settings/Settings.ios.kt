package com.boiller.monitor.shared.settings

import platform.Foundation.NSUserDefaults

actual class Settings {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    
    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_NOTIFICATION_START_HOUR = "notification_start_hour"
        const val KEY_NOTIFICATION_END_HOUR = "notification_end_hour"
        const val DEFAULT_SERVER_URL = "http://192.168.50.100:8080/"
        const val DEFAULT_NOTIFICATION_START_HOUR = 8
        const val DEFAULT_NOTIFICATION_END_HOUR = 22
    }
    
    actual fun getServerUrl(): String {
        return userDefaults.stringForKey(KEY_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    actual fun setServerUrl(url: String) {
        userDefaults.setObject(url, forKey = KEY_SERVER_URL)
    }
    
    actual fun getNotificationStartHour(): Int {
        val value = userDefaults.objectForKey(KEY_NOTIFICATION_START_HOUR)
        return if (value != null) {
            (value as? NSNumber)?.intValue ?: DEFAULT_NOTIFICATION_START_HOUR
        } else {
            DEFAULT_NOTIFICATION_START_HOUR
        }
    }
    
    actual fun getNotificationEndHour(): Int {
        val value = userDefaults.objectForKey(KEY_NOTIFICATION_END_HOUR)
        return if (value != null) {
            (value as? NSNumber)?.intValue ?: DEFAULT_NOTIFICATION_END_HOUR
        } else {
            DEFAULT_NOTIFICATION_END_HOUR
        }
    }
    
    actual fun setNotificationHours(startHour: Int, endHour: Int) {
        userDefaults.setObject(startHour, forKey = KEY_NOTIFICATION_START_HOUR)
        userDefaults.setObject(endHour, forKey = KEY_NOTIFICATION_END_HOUR)
    }
}
