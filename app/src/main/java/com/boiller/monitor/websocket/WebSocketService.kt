package com.boiller.monitor.websocket

import android.content.Context
import android.util.Log
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.shared.api.DataRecord
import com.boiller.monitor.utils.LightChangeHistory
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class WebSocketService private constructor(private val context: Context) {
    private var socket: Socket? = null
    private var isConnected = false
    
    companion object {
        private const val TAG = "WebSocketService"
        @Volatile
        private var INSTANCE: WebSocketService? = null
        
        fun getInstance(context: Context): WebSocketService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun connect(baseUrl: String) {
        try {
            val url = baseUrl.removeSuffix("/")
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = -1 // Нескінченні спроби
                reconnectionDelay = 5000 // 5 секунд між спробами
            }
            
            socket = IO.socket(url, opts)
            setupHandlers()
            socket?.connect()
            Log.d(TAG, "Підключення до WebSocket: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка підключення до WebSocket", e)
        }
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket = null
        isConnected = false
        Log.d(TAG, "WebSocket відключено")
    }
    
    private fun setupHandlers() {
        socket?.on(Socket.EVENT_CONNECT) {
            isConnected = true
            Log.d(TAG, "WebSocket підключено")
        }
        
        socket?.on(Socket.EVENT_DISCONNECT) {
            isConnected = false
            Log.d(TAG, "WebSocket відключено")
        }
        
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "WebSocket помилка підключення: ${args[0]}")
            isConnected = false
        }
        
        // Оновлення даних
        socket?.on("data_update") { args ->
            try {
                val data = args[0] as? JSONObject ?: return@on
                val timestamp = data.getString("timestamp")
                val batterySoc = data.getInt("battery_soc")
                val gridLoad = data.getInt("grid_load")
                val homeLoad = data.getInt("home_load")
                val hasLight = gridLoad > 0
                
                val dataRecord = DataRecord(
                    timestamp = timestamp,
                    batterySoc = batterySoc,
                    gridLoad = gridLoad,
                    homeLoad = homeLoad,
                    gridStatus = hasLight
                )
                
                // Оновлюємо дані в основному додатку через BroadcastReceiver
                notifyDataUpdate(dataRecord)
                
                Log.d(TAG, "Оновлено дані через WebSocket: батарея=$batterySoc%, мережа=$gridLoadВт")
            } catch (e: Exception) {
                Log.e(TAG, "Помилка обробки data_update", e)
            }
        }
        
        // Зміна статусу світла
        socket?.on("status_change") { args ->
            try {
                val data = args[0] as? JSONObject ?: return@on
                val hasLight = data.getBoolean("hasLight")
                val timestamp = data.getString("timestamp")
                val dataRecordObj = data.getJSONObject("data")
                
                val batterySoc = dataRecordObj.getInt("battery_soc")
                val gridLoad = dataRecordObj.getInt("grid_load")
                val homeLoad = dataRecordObj.getInt("home_load")
                
                val dataRecord = DataRecord(
                    timestamp = timestamp,
                    batterySoc = batterySoc,
                    gridLoad = gridLoad,
                    homeLoad = homeLoad,
                    gridStatus = hasLight
                )
                
                // Додаємо до історії
                LightChangeHistory.add(context, hasLight, timestamp)
                
                // Оновлюємо дані
                notifyDataUpdate(dataRecord)
                
                // Відправляємо повідомлення про зміну статусу
                notifyStatusChange(hasLight, timestamp, dataRecord)
                
                Log.d(TAG, "Зміна статусу світла через WebSocket: hasLight=$hasLight")
            } catch (e: Exception) {
                Log.e(TAG, "Помилка обробки status_change", e)
            }
        }
    }
    
    private fun notifyDataUpdate(data: DataRecord) {
        // Відправляємо Broadcast для оновлення UI
        val intent = android.content.Intent("com.boiller.monitor.DATA_UPDATE").apply {
            putExtra("timestamp", data.timestamp)
            putExtra("batterySoc", data.batterySoc)
            putExtra("gridLoad", data.gridLoad)
            putExtra("homeLoad", data.homeLoad)
            putExtra("hasLight", data.gridStatus)
        }
        context.sendBroadcast(intent)
    }
    
    private fun notifyStatusChange(hasLight: Boolean, timestamp: String, data: DataRecord) {
        // Відправляємо Broadcast для обробки зміни статусу
        val intent = android.content.Intent("com.boiller.monitor.STATUS_CHANGE").apply {
            putExtra("hasLight", hasLight)
            putExtra("timestamp", timestamp)
            putExtra("batterySoc", data.batterySoc)
            putExtra("gridLoad", data.gridLoad)
            putExtra("homeLoad", data.homeLoad)
        }
        context.sendBroadcast(intent)
    }
}
