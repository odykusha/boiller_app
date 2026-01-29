package com.boiller.monitor.shared.settings

expect class Settings {
    fun getServerUrl(): String
    fun setServerUrl(url: String)
    fun getNotificationStartHour(): Int
    fun getNotificationEndHour(): Int
    fun setNotificationHours(startHour: Int, endHour: Int)
}
