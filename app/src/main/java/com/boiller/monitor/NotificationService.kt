package com.boiller.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.api.DataRecord
import com.boiller.monitor.utils.DateFormatter
import com.boiller.monitor.utils.LightChangeHistory
import com.boiller.monitor.utils.LightChangeEvent
import kotlinx.coroutines.*
import java.util.*

class NotificationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastGridLoad: Int? = null
    private var updateJob: Job? = null
    private var consecutiveErrors = 0
    private var changeNotificationCounter = NOTIFICATION_ID_CHANGE
    
    companion object {
        private const val TAG = "NotificationService"
        private const val NOTIFICATION_ID_STATUS = 1
        private const val NOTIFICATION_ID_CHANGE = 2
        private const val CHANNEL_ID_STATUS = "boiller_monitor_status_channel"
        private const val CHANNEL_ID_CHANGE = "boiller_monitor_change_channel"
        private const val CHANNEL_NAME_STATUS = "Теперішній статус"
        private const val CHANNEL_NAME_CHANGE = "Зміни по світлу"
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_LIGHT_DISAPPEARED_TIME = "light_disappeared_time"
        private const val KEY_LIGHT_APPEARED_TIME = "light_appeared_time"
        
        fun startService(context: Context) {
            val intent = Intent(context, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, NotificationService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val notification = createStatusNotification(null)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID_STATUS, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID_STATUS, notification)
            }
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID_STATUS, notification)
        }
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                CHANNEL_NAME_STATUS,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поточний стан системи"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val changeChannel = NotificationChannel(
                CHANNEL_ID_CHANGE,
                CHANNEL_NAME_CHANGE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Повідомлення про зміну статусу світла"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(changeChannel)
        }
    }
    
    private fun startMonitoring() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                try {
                    val data = fetchLatestData()
                    if (data != null) {
                        consecutiveErrors = 0
                        
                        if (lastGridLoad == null) {
                            initializeStatusChangeTime(data)
                        }
                        
                        checkGridLoadChange(data)
                        updateNotification(data)
                    } else {
                        consecutiveErrors++
                        if (consecutiveErrors >= 3) {
                            showErrorNotification("Помилка підключення до сервера")
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Помилка при оновленні даних", e)
                    if (consecutiveErrors >= 3) {
                        showErrorNotification("Помилка: ${e.message}")
                    }
                }
                delay(60000)
            }
        }
    }
    
    private suspend fun fetchLatestData(): DataRecord? = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService(this@NotificationService)
            val response = apiService.getLatest()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при запиті до API", e)
            null
        }
    }
    
    private suspend fun fetchDataHistory(): List<DataRecord>? = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService(this@NotificationService)
            val response = apiService.getData()
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при запиті історії", e)
            null
        }
    }
    
    private suspend fun findGridLoadChangeTime(fromGridLoad: Int, toGridLoad: Int): String? = withContext(Dispatchers.IO) {
        try {
            val history = fetchDataHistory() ?: return@withContext null
            if (history.isEmpty()) return@withContext null
            
            val sortedHistory = history.sortedBy { it.timestamp }
            val fromHasLight = hasLight(fromGridLoad)
            val toHasLight = hasLight(toGridLoad)
            
            for (i in sortedHistory.size - 1 downTo 1) {
                val current = sortedHistory[i]
                val previous = sortedHistory[i - 1]
                
                if (hasLight(previous.gridLoad) == fromHasLight && hasLight(current.gridLoad) == toHasLight) {
                    return@withContext current.timestamp
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при пошуку часу зміни", e)
            null
        }
    }
    
    private suspend fun findLastGridLoadChange(currentGridLoad: Int): String? = withContext(Dispatchers.IO) {
        try {
            val history = fetchDataHistory() ?: return@withContext null
            if (history.isEmpty()) return@withContext null
            
            val sortedHistory = history.sortedBy { it.timestamp }
            
            for (i in sortedHistory.size - 1 downTo 1) {
                val current = sortedHistory[i]
                val previous = sortedHistory[i - 1]
                
                if ((current.gridLoad > 0) != (previous.gridLoad > 0)) {
                    return@withContext current.timestamp
                }
            }
            
            val currentHasLight = currentGridLoad > 0
            sortedHistory.findLast { (it.gridLoad > 0) == currentHasLight }?.timestamp
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при пошуку останньої зміни", e)
            null
        }
    }
    
    private fun hasLight(gridLoad: Int): Boolean = gridLoad > 0
    
    private fun updateNotification(data: DataRecord) {
        try {
            val notification = createStatusNotification(data)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_STATUS, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при оновленні нотифікації", e)
        }
    }
    
    private fun showErrorNotification(errorMessage: String) {
        try {
            val pendingIntent = createPendingIntent(0)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
                .setContentTitle("⚠️ Помилка підключення")
                .setContentText(errorMessage)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
            
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_STATUS, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при показі помилки", e)
        }
    }
    
    private fun createPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private fun createStatusNotification(data: DataRecord?): Notification {
        val pendingIntent = createPendingIntent(0)
        
        if (data == null) {
            return NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
                .setContentTitle("Світлячок")
                .setContentText("Завантаження даних...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
        }
        
        val hasLightNow = hasLight(data.gridLoad)
        val gridStatusEmoji = if (hasLightNow) "💡" else "🕯️"
        val infoText = "🔋 ${data.batterySoc}% | ⚡ ${data.gridLoad} Вт | 🏠 ${data.homeLoad} Вт"
        val statusChangeText = getStatusChangeText(hasLightNow)
        
        return NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
            .setContentTitle("$gridStatusEmoji $statusChangeText")
            .setContentText(infoText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)
            .build()
    }
    
    private suspend fun checkGridLoadChange(data: DataRecord) {
        val currentGridLoad = data.gridLoad
        val previousGridLoad = lastGridLoad
        
        val currentHasLight = hasLight(currentGridLoad)
        val previousHasLight = previousGridLoad?.let { hasLight(it) }
        
        if (previousGridLoad != null && previousHasLight != currentHasLight) {
            val exactChangeTimestamp = findGridLoadChangeTime(previousGridLoad, currentGridLoad)
            
            val changeTimestamp = if (exactChangeTimestamp != null && exactChangeTimestamp >= data.timestamp) {
                exactChangeTimestamp
            } else {
                data.timestamp
            }
            
            saveStatusChangeTime(currentHasLight, changeTimestamp)
            LightChangeHistory.add(this@NotificationService, currentHasLight, changeTimestamp)
            
            val isTimeAllowed = isNotificationTimeAllowed()
            showStatusChangeNotification(currentHasLight, changeTimestamp, isTimeAllowed)
        }
        
        lastGridLoad = currentGridLoad
    }
    
    private fun saveStatusChangeTime(hasLight: Boolean, timestamp: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (hasLight) KEY_LIGHT_APPEARED_TIME else KEY_LIGHT_DISAPPEARED_TIME
        prefs.edit().putString(key, timestamp).apply()
    }
    
    private suspend fun initializeStatusChangeTime(data: DataRecord) {
        seedLightHistoryIfNeeded()
        val hasLightNow = hasLight(data.gridLoad)
        val lastChangeTimestamp = findLastGridLoadChange(data.gridLoad) ?: data.timestamp
        saveStatusChangeTime(hasLightNow, lastChangeTimestamp)
        lastGridLoad = data.gridLoad
    }

    private suspend fun seedLightHistoryIfNeeded() {
        if (LightChangeHistory.isSeeded(this@NotificationService)) return

        val history = fetchDataHistory()?.sortedBy { it.timestamp }.orEmpty()
        if (history.size < 2) {
            LightChangeHistory.markSeeded(this@NotificationService)
            return
        }

        val events = ArrayList<LightChangeEvent>()
        for (i in 1 until history.size) {
            val prev = history[i - 1]
            val cur = history[i]
            val prevHas = hasLight(prev.gridLoad)
            val curHas = hasLight(cur.gridLoad)
            if (prevHas != curHas) {
                events.add(LightChangeEvent(timestamp = cur.timestamp, hasLight = curHas))
            }
        }

        // зберігаємо максимум 10 останніх
        LightChangeHistory.replaceAll(this@NotificationService, events)
        LightChangeHistory.markSeeded(this@NotificationService)
    }
    
    private fun getStatusChangeText(hasLight: Boolean): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (hasLight) KEY_LIGHT_APPEARED_TIME else KEY_LIGHT_DISAPPEARED_TIME
        val time = prefs.getString(key, null)
        
        return if (time != null) {
            val formattedTime = DateFormatter.formatTime(time)
            if (hasLight) "Світло з'явилося о $formattedTime" else "Світло зникло о $formattedTime"
        } else {
            if (hasLight) "Є світло" else "Немає світла"
        }
    }
    
    private fun isNotificationTimeAllowed(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val startHour = ApiClient.getNotificationStartHour(this)
        val endHour = ApiClient.getNotificationEndHour(this)
        return currentHour >= startHour && currentHour < endHour
    }
    
    private fun showStatusChangeNotification(hasLight: Boolean, changeTimestamp: String, withSound: Boolean) {
        val formattedTime = DateFormatter.formatTime(changeTimestamp)
        val title = if (hasLight) "💡 Світло з'явилося о $formattedTime" else "🕯️ Світло зникло о $formattedTime"
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_CHANGE)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(1))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        if (!withSound) {
            notificationBuilder.setSilent(true)
        }
        
        val uniqueNotificationId = changeNotificationCounter++
        if (changeNotificationCounter > Int.MAX_VALUE - 1000) {
            changeNotificationCounter = NOTIFICATION_ID_CHANGE
        }
        
        NotificationManagerCompat.from(this).notify(uniqueNotificationId, notificationBuilder.build())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }
}
