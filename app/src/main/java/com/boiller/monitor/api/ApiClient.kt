package com.boiller.monitor.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "boiller_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_NOTIFICATION_START_HOUR = "notification_start_hour"
    private const val KEY_NOTIFICATION_END_HOUR = "notification_end_hour"
    private const val DEFAULT_SERVER_URL = "http://192.168.50.100:8080/"
    private const val DEFAULT_NOTIFICATION_START_HOUR = 8
    private const val DEFAULT_NOTIFICATION_END_HOUR = 22
    
    private var retrofit: Retrofit? = null
    
    fun getApiService(context: Context): ApiService {
        val serverUrl = getServerUrl(context)
        
        if (retrofit == null || (retrofit?.baseUrl()?.toString() != serverUrl)) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            retrofit = Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        
        return retrofit!!.create(ApiService::class.java)
    }
    
    fun getServerUrl(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    fun setServerUrl(context: Context, url: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        // Скидаємо retrofit для пересоздання з новим URL
        retrofit = null
    }
    
    fun getNotificationStartHour(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NOTIFICATION_START_HOUR, DEFAULT_NOTIFICATION_START_HOUR)
    }
    
    fun getNotificationEndHour(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NOTIFICATION_END_HOUR, DEFAULT_NOTIFICATION_END_HOUR)
    }
    
    fun setNotificationHours(context: Context, startHour: Int, endHour: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_NOTIFICATION_START_HOUR, startHour)
            .putInt(KEY_NOTIFICATION_END_HOUR, endHour)
            .apply()
    }
}
