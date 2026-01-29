package com.boiller.monitor

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.databinding.ActivitySettingsBinding
import com.boiller.monitor.shared.utils.DateFormatter
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
                gravity = Gravity.CENTER
            }
            container.addView(tv)
            return
        }

        // Створюємо таблицю
        val tableLayout = TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            isStretchAllColumns = true
            // Додаємо зовнішні границі до таблиці
            background = getDrawable(R.drawable.table_border)
        }

        // Заголовок таблиці
        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val headerEvent = createTableCell("Подія", true)
        val headerTime = createTableCell("Час", true)
        val headerDiff = createTableCell("Різниця", true)

        headerRow.addView(headerEvent)
        headerRow.addView(headerTime)
        headerRow.addView(headerDiff)
        tableLayout.addView(headerRow)

        // Рядки з даними
        events.forEachIndexed { index, ev ->
            val row = TableRow(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val emoji = if (ev.hasLight) "💡" else "🕯️"
            val eventText = if (ev.hasLight) "З'явилось" else "Зникло"
            val eventCellText = "$emoji $eventText"
            val time = DateFormatter.formatTime(ev.timestamp)
            
            // Обчислюємо різницю часу
            val timeDiff = if (index < events.size - 1) {
                val previousEvent = events[index + 1]
                DateFormatter.formatTimeDifference(ev.timestamp, previousEvent.timestamp) ?: "-"
            } else {
                "-"
            }

            val eventCell = createTableCell(eventCellText, false)
            val timeCell = createTableCell(time, false)
            val diffCell = createTableCell(timeDiff, false)

            row.addView(eventCell)
            row.addView(timeCell)
            row.addView(diffCell)
            tableLayout.addView(row)
        }

        container.addView(tableLayout)
    }

    private fun createTableCell(text: String, isHeader: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(if (isHeader) getColor(R.color.primary_blue) else getColor(R.color.white))
            textSize = if (isHeader) 14f else 13f
            setTypeface(null, if (isHeader) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(
                (resources.displayMetrics.density * 8).toInt(),
                (resources.displayMetrics.density * 8).toInt(),
                (resources.displayMetrics.density * 8).toInt(),
                (resources.displayMetrics.density * 8).toInt()
            )
            // Додаємо границі до комірок
            background = getDrawable(R.drawable.table_cell_border)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
