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
import kotlinx.coroutines.*
import java.util.*

class NotificationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastGridLoad: Int? = null // Зберігаємо останнє значення grid_load для визначення зміни
    private var updateJob: Job? = null
    private var consecutiveErrors = 0
    private val TAG = "NotificationService"
    
    companion object {
        private const val NOTIFICATION_ID_STATUS = 1  // ID для поточного стану (без звуку)
        private const val NOTIFICATION_ID_CHANGE = 2   // ID для зміни статусу (зі звуком)
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
                // Android 14+ (API 34+) - потрібно вказати тип для specialUse
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID_STATUS, notification, serviceType)
            } else {
                // Android 13 та нижче
                startForeground(NOTIFICATION_ID_STATUS, notification)
            }
        } catch (e: Exception) {
            // Fallback для випадків, коли щось пішло не так
            startForeground(NOTIFICATION_ID_STATUS, notification)
            e.printStackTrace()
        }
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Канал для поточного стану (без звуку)
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
            
            // Канал для зміни статусу (зі звуком)
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
                    Log.d(TAG, "Оновлення даних...")
                    val data = fetchLatestData()
                    if (data != null) {
                        consecutiveErrors = 0
                        Log.d(TAG, "Дані отримано: ${data}")
                        
                        // Ініціалізуємо час зміни статусу при першому запуску
                        if (lastGridLoad == null) {
                            Log.d(TAG, "Ініціалізація часу зміни статусу при першому запуску")
                            initializeStatusChangeTime(data)
                        }
                        
                        updateNotification(data)
                        checkGridLoadChange(data)
                    } else {
                        consecutiveErrors++
                        Log.w(TAG, "Не вдалося отримати дані (помилка #$consecutiveErrors)")
                        if (consecutiveErrors >= 3) {
                            // Після 3 помилок підряд показуємо помилку в нотифікації
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
                delay(60000) // Оновлення кожні 60 секунд
            }
        }
    }
    
    private suspend fun fetchLatestData(): DataRecord? = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService(this@NotificationService)
            val response = apiService.getLatest()
            if (response.isSuccessful && response.body() != null) {
                response.body()
            } else {
                Log.w(TAG, "Неуспішна відповідь API: ${response.code()}, ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при запиті до API", e)
            null
        }
    }
    
    private suspend fun fetchDataHistory(): List<DataRecord>? = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService(this@NotificationService)
            val response = apiService.getData()
            if (response.isSuccessful && response.body() != null) {
                response.body()?.data
            } else {
                Log.w(TAG, "Неуспішна відповідь API для історії: ${response.code()}, ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при запиті історії до API", e)
            null
        }
    }
    
    /**
     * Знаходить останню зміну grid_load в історії даних
     * Повертає timestamp останньої зміни (коли grid_load змінився з 0 на >0 або навпаки)
     */
    private suspend fun findLastGridLoadChange(currentGridLoad: Int): String? = withContext(Dispatchers.IO) {
        try {
            val history = fetchDataHistory()
            if (history == null || history.isEmpty()) {
                Log.w(TAG, "Історія даних порожня або не отримана")
                return@withContext null
            }
            
            // Сортуємо за timestamp (від старого до нового)
            val sortedHistory = history.sortedBy { it.timestamp }
            
            // Шукаємо останню зміну grid_load
            // Проходимо з кінця (найновіші дані) до початку
            var lastChangeTimestamp: String? = null
            
            for (i in sortedHistory.size - 1 downTo 1) {
                val current = sortedHistory[i]
                val previous = sortedHistory[i - 1]
                
                val currentHasLight = current.gridLoad > 0
                val previousHasLight = previous.gridLoad > 0
                
                // Якщо статус змінився (з 0 на >0 або з >0 на 0)
                if (currentHasLight != previousHasLight) {
                    lastChangeTimestamp = current.timestamp
                    Log.d(TAG, "Знайдено останню зміну grid_load: ${current.timestamp}, grid_load змінився з ${previous.gridLoad} на ${current.gridLoad}")
                    break
                }
            }
            
            // Якщо зміни не знайдено, використовуємо найстаріший запис з поточним статусом
            if (lastChangeTimestamp == null) {
                val currentHasLight = currentGridLoad > 0
                val matchingRecord = sortedHistory.findLast { (it.gridLoad > 0) == currentHasLight }
                if (matchingRecord != null) {
                    lastChangeTimestamp = matchingRecord.timestamp
                    Log.d(TAG, "Зміни не знайдено, використовуємо найстаріший запис з поточним статусом: ${matchingRecord.timestamp}")
                }
            }
            
            lastChangeTimestamp
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при пошуку останньої зміни grid_load", e)
            null
        }
    }
    
    /**
     * Визначає чи є світло на основі grid_load
     */
    private fun hasLight(gridLoad: Int): Boolean {
        return gridLoad > 0
    }
    
    private fun updateNotification(data: DataRecord) {
        try {
            val notification = createStatusNotification(data)
            val notificationManager = NotificationManagerCompat.from(this)
            // Примусово оновлюємо нотифікацію навіть якщо текст не змінився
            notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
            Log.d(TAG, "Нотифікація оновлена успішно, статус: ${data.gridStatus}, timestamp: ${data.timestamp}")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при оновленні нотифікації", e)
        }
    }
    
    private fun showErrorNotification(errorMessage: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
                .setContentTitle("⚠️ Помилка підключення")
                .setContentText(errorMessage)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при показі помилки в нотифікації", e)
        }
    }
    
    // Нотифікація поточного стану (без звуку)
    private fun createStatusNotification(data: DataRecord?): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        if (data == null) {
            return NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
                .setContentTitle("Світлячок")
                .setContentText("Завантаження даних...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true) // Без звуку
                .build()
        }
        
        val hasLightNow = hasLight(data.gridLoad)
        val gridStatusEmoji = if (hasLightNow) "💡" else "🕯️"
        val infoText = "🔋 ${data.batterySoc}% | ⚡ ${data.gridLoad} Вт | 🏠 ${data.homeLoad} Вт"
        
        // Отримуємо час зникнення/повернення світла для title
        val statusChangeText = getStatusChangeText(hasLightNow)
        
        val title = "$gridStatusEmoji $statusChangeText"
        Log.d(TAG, "Створюємо нотифікацію: title='$title', text='$infoText'")
        
        return NotificationCompat.Builder(this, CHANNEL_ID_STATUS)
            .setContentTitle(title)
            .setContentText(infoText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true) // Без звуку
            .setWhen(System.currentTimeMillis()) // Додаємо поточний час для примусового оновлення
            .setShowWhen(false) // Не показуємо час в UI
            .build()
    }
    
    private fun checkGridLoadChange(data: DataRecord) {
        val currentGridLoad = data.gridLoad
        val previousGridLoad = lastGridLoad
        
        val currentHasLight = hasLight(currentGridLoad)
        val previousHasLight = previousGridLoad?.let { hasLight(it) }
        
        if (previousGridLoad != null && previousHasLight != currentHasLight) {
            // Статус світла змінився - зберігаємо час зміни
            Log.d(TAG, "Статус світла змінився: grid_load змінився з $previousGridLoad на $currentGridLoad")
            saveStatusChangeTime(currentHasLight, data.timestamp)
            
            if (isNotificationTimeAllowed()) {
                showStatusChangeNotification(data, currentHasLight)
            }
        }
        
        lastGridLoad = currentGridLoad
    }
    
    private fun saveStatusChangeTime(hasLight: Boolean, timestamp: String) {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (hasLight) {
            // Світло з'явилося
            prefs.edit().putString(KEY_LIGHT_APPEARED_TIME, timestamp).apply()
            Log.d(TAG, "Збережено час появи світла: $timestamp")
        } else {
            // Світло зникло
            prefs.edit().putString(KEY_LIGHT_DISAPPEARED_TIME, timestamp).apply()
            Log.d(TAG, "Збережено час зникнення світла: $timestamp")
        }
    }
    
    private suspend fun initializeStatusChangeTime(data: DataRecord) {
        // При першому запуску шукаємо останню зміну grid_load в історії
        val hasLightNow = hasLight(data.gridLoad)
        val lastChangeTimestamp = findLastGridLoadChange(data.gridLoad)
        
        if (lastChangeTimestamp != null) {
            // Знайшли останню зміну в історії - зберігаємо її
            Log.d(TAG, "Ініціалізація: знайдено останню зміну grid_load в історії: $lastChangeTimestamp")
            saveStatusChangeTime(hasLightNow, lastChangeTimestamp)
        } else {
            // Якщо не знайшли в історії, використовуємо поточний час
            Log.d(TAG, "Ініціалізація: зміни в історії не знайдено, використовуємо поточний час: ${data.timestamp}")
            saveStatusChangeTime(hasLightNow, data.timestamp)
        }
        
        // Встановлюємо поточне значення grid_load
        lastGridLoad = data.gridLoad
    }
    
    private fun getStatusChangeText(hasLight: Boolean): String {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = if (hasLight) {
            // Світло є - показуємо коли з'явилося
            val appearedTime = prefs.getString(KEY_LIGHT_APPEARED_TIME, null)
            if (appearedTime != null) {
                val formattedTime = DateFormatter.formatTime(appearedTime)
                "Світло з'явилося о $formattedTime"
            } else {
                "Є світло"
            }
        } else {
            // Світла немає - показуємо коли зникло
            val disappearedTime = prefs.getString(KEY_LIGHT_DISAPPEARED_TIME, null)
            if (disappearedTime != null) {
                val formattedTime = DateFormatter.formatTime(disappearedTime)
                "Світло зникло о $formattedTime"
            } else {
                "Немає світла"
            }
        }
        Log.d(TAG, "getStatusChangeText: hasLight=$hasLight, result='$result'")
        return result
    }
    
    private fun isNotificationTimeAllowed(): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val startHour = ApiClient.getNotificationStartHour(this)
        val endHour = ApiClient.getNotificationEndHour(this)
        return currentHour >= startHour && currentHour < endHour
    }
    
    // Нотифікація про зміну статусу (зі звуком)
    private fun showStatusChangeNotification(data: DataRecord, hasLight: Boolean) {
        val title = if (hasLight) 
            "💡 Світло з'явилося!" 
        else 
            "🕯️ Світло зникло!"
        val text = "🔋 ${data.batterySoc}% | ⚡ ${data.gridLoad} Вт | 🏠 ${data.homeLoad} Вт"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHANGE)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID_CHANGE, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }
}
