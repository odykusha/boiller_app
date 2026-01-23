package com.boiller.monitor

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.databinding.ActivitySettingsBinding
import com.boiller.monitor.utils.DateFormatter
import com.boiller.monitor.utils.LightChangeHistory

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Налаштування"
        
        loadSettings()
        renderLightChanges()
        
        // Оновлення тексту при зміні slider
        binding.notificationStartHourPicker.addOnChangeListener { _, value, _ ->
            binding.startHourText.text = String.format("%02d:00", value.toInt())
        }
        
        binding.notificationEndHourPicker.addOnChangeListener { _, value, _ ->
            binding.endHourText.text = String.format("%02d:00", value.toInt())
        }
        
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        renderLightChanges()
    }
    
    private fun loadSettings() {
        val serverUrl = ApiClient.getServerUrl(this)
        val startHour = ApiClient.getNotificationStartHour(this)
        val endHour = ApiClient.getNotificationEndHour(this)
        
        binding.serverUrlInput.setText(serverUrl)
        binding.notificationStartHourPicker.value = startHour.toFloat()
        binding.notificationEndHourPicker.value = endHour.toFloat()
        binding.startHourText.text = String.format("%02d:00", startHour)
        binding.endHourText.text = String.format("%02d:00", endHour)
    }
    
    private fun saveSettings() {
        val serverUrl = binding.serverUrlInput.text.toString().trim()
        val startHour = binding.notificationStartHourPicker.value.toInt()
        val endHour = binding.notificationEndHourPicker.value.toInt()
        
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Введіть URL сервера", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (startHour >= endHour) {
            Toast.makeText(this, "Час початку повинен бути менше часу закінчення", Toast.LENGTH_SHORT).show()
            return
        }
        
        val url = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        ApiClient.setServerUrl(this, url)
        ApiClient.setNotificationHours(this, startHour, endHour)
        
        Toast.makeText(this, "Налаштування збережено", Toast.LENGTH_SHORT).show()
        
        // Перезапускаємо сервіс нотифікацій
        NotificationService.startService(this)
        
        finish()
    }

    private fun renderLightChanges() {
        val container = binding.lightChangesContainer
        container.removeAllViews()

        val events = LightChangeHistory.getLast(this, 10)
        if (events.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Немає даних"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
            }
            container.addView(tv)
            return
        }

        events.forEach { ev ->
            val emoji = if (ev.hasLight) "💡" else "🕯️"
            val time = DateFormatter.formatTime(ev.timestamp)
            val text = if (ev.hasLight) "$emoji Світло з'явилось о $time" else "$emoji Світло зникло о $time"

            val tv = TextView(this).apply {
                this.text = text
                setTextColor(getColor(R.color.white))
                textSize = 14f
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.displayMetrics.density.toInt() * 8
                }
            }
            container.addView(tv)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
