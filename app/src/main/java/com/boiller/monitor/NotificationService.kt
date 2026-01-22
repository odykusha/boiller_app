package com.boiller.monitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.api.DataRecord
import com.boiller.monitor.utils.DateFormatter
import kotlinx.coroutines.*
import java.util.*

class NotificationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastGridStatus: Boolean? = null
    private var updateJob: Job? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "boiller_monitor_channel"
        private const val CHANNEL_NAME = "Бойлер Монітор"
        
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
        createNotificationChannel()
        val notification = createNotification(null)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+ (API 34+) - потрібно вказати тип для specialUse
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                // Android 13 та нижче
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback для випадків, коли щось пішло не так
            startForeground(NOTIFICATION_ID, notification)
            e.printStackTrace()
        }
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Моніторинг інвертера"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startMonitoring() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                try {
                    val data = fetchLatestData()
                    if (data != null) {
                        updateNotification(data)
                        checkGridStatusChange(data)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(60000) // Оновлення кожні 60 секунд
            }
        }
    }
    
    private suspend fun fetchLatestData(): DataRecord? = withContext(Dispatchers.IO) {
        try {
            val apiService = ApiClient.getApiService(this@NotificationService)
            val response = apiService.getLatest()
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateNotification(data: DataRecord) {
        val notification = createNotification(data)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(data: DataRecord?): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        if (data == null) {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Бойлер Монітор")
                .setContentText("Завантаження даних...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
        
        val gridStatusEmoji = if (data.gridStatus) "💡" else "🕯️"
        val updateTime = DateFormatter.formatTime(data.timestamp)
        val gridStatusText = if (data.gridStatus) "Є світло" else "Немає світла"
        val infoText = "🔋 ${data.batterySoc}% | ⚡ ${data.gridLoad} Вт | 🏠 ${data.homeLoad} Вт"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$gridStatusEmoji $gridStatusText | $updateTime\n$infoText")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    private fun checkGridStatusChange(data: DataRecord) {
        val currentStatus = data.gridStatus
        val previousStatus = lastGridStatus
        
        if (previousStatus != null && currentStatus != previousStatus) {
            // Статус змінився
            if (isNotificationTimeAllowed()) {
                showStatusChangeNotification(data, currentStatus)
                playSound()
            }
        }
        
        lastGridStatus = currentStatus
    }
    
    private fun isNotificationTimeAllowed(): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val startHour = ApiClient.getNotificationStartHour(this)
        val endHour = ApiClient.getNotificationEndHour(this)
        return currentHour >= startHour && currentHour < endHour
    }
    
    private fun showStatusChangeNotification(data: DataRecord, hasLight: Boolean) {
        val title = if (hasLight) "💡 Світло з'явилося! 🔋${data.batterySoc}%" else "🕯️ Світло зникло! 🔋${data.batterySoc}%"
//        val text = if (hasLight)
//            "Мережа: ${data.gridLoad} Вт | Батарея: ${data.batterySoc}%"
//        else
//            "Мережа: ${data.gridLoad} Вт | Батарея: ${data.batterySoc}%"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
//            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(2, notification) // Інший ID для окремої нотифікації
    }
    
    private fun playSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }
}
