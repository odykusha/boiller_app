package com.boiller.monitor.api

import android.content.Context
import com.boiller.monitor.shared.api.ApiService
import com.boiller.monitor.shared.settings.Settings

object ApiClient {
    private var apiService: ApiService? = null
    private var currentServerUrl: String? = null
    
    fun getApiService(context: Context): ApiService {
        val settings = Settings(context)
        val serverUrl = settings.getServerUrl()
        
        // Пересоздаємо сервіс, якщо URL змінився
        if (apiService == null || currentServerUrl != serverUrl) {
            apiService = ApiService(serverUrl)
            currentServerUrl = serverUrl
        }
        
        return apiService!!
    }
    
    fun getServerUrl(context: Context): String {
        return Settings(context).getServerUrl()
    }
    
    fun setServerUrl(context: Context, url: String) {
        Settings(context).setServerUrl(url)
        // Скидаємо apiService для пересоздання з новим URL
        apiService = null
    }
    
    fun getNotificationStartHour(context: Context): Int {
        return Settings(context).getNotificationStartHour()
    }
    
    fun getNotificationEndHour(context: Context): Int {
        return Settings(context).getNotificationEndHour()
    }
    
    fun setNotificationHours(context: Context, startHour: Int, endHour: Int) {
        Settings(context).setNotificationHours(startHour, endHour)
    }
}
