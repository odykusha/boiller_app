package com.boiller.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.api.DataRecord
import com.boiller.monitor.databinding.ActivityMainBinding
import com.boiller.monitor.databinding.CardStatBinding
import com.boiller.monitor.databinding.DialogSettingsBinding
import com.boiller.monitor.utils.DateFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var refreshTimer: Timer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        setupCharts()
        setupClickListeners()
        loadData()
        
        // Автоматичне оновлення кожні 60 секунд
        startAutoRefresh()
        
        // Перевіряємо дозволи для нотифікацій
        checkNotificationPermission()
        
        // Запускаємо сервіс нотифікацій
        NotificationService.startService(this)
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }
    }
    
    private fun setupCharts() {
        setupChart(binding.batteryChart, "Батарея", android.graphics.Color.parseColor("#00FF96"))
        setupChart(binding.gridChart, "Мережа", android.graphics.Color.parseColor("#FF6B6B"))
        setupChart(binding.homeChart, "Дім", android.graphics.Color.parseColor("#00D4FF"))
    }
    
    private fun setupChart(chart: com.github.mikephil.charting.charts.LineChart, label: String, color: Int) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = false
        
        // X Axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = getColor(R.color.text_secondary)
        xAxis.setDrawGridLines(false)
        xAxis.setLabelCount(5, true)
        xAxis.granularity = 1f
        xAxis.setAvoidFirstLastClipping(true)
        
        // Y Axis
        val leftAxis = chart.axisLeft
        leftAxis.textColor = getColor(R.color.text_secondary)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = getColor(R.color.text_secondary)
        leftAxis.axisLineColor = getColor(R.color.text_secondary)
        
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false
        
        chart.setNoDataText("Немає даних")
        chart.setNoDataTextColor(getColor(R.color.text_secondary))
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupClickListeners() {
        binding.fabRefresh.setOnClickListener {
            loadData()
        }
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            try {
                binding.statusText.text = "Завантаження..."
                val apiService = ApiClient.getApiService(this@MainActivity)
                val response = apiService.getData()
                
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    if (data.isNotEmpty()) {
                        updateCharts(data)
                        updateStats(data.last())
                        binding.statusText.text = "Останнє оновлення: ${DateFormatter.formatTime(data.last().timestamp)}"
                    } else {
                        binding.statusText.text = "Немає даних"
                    }
                } else {
                    binding.statusText.text = "Помилка завантаження"
                    Toast.makeText(this@MainActivity, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.statusText.text = "Помилка підключення"
                Toast.makeText(this@MainActivity, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
    
    private fun updateCharts(data: List<DataRecord>) {
        if (data.isEmpty()) return
        
        val labels = data.map { DateFormatter.formatTime(it.timestamp) }
        val batteryEntries = data.mapIndexed { index, record -> Entry(index.toFloat(), record.batterySoc.toFloat()) }
        val gridEntries = data.mapIndexed { index, record -> Entry(index.toFloat(), record.gridLoad.toFloat()) }
        val homeEntries = data.mapIndexed { index, record -> Entry(index.toFloat(), record.homeLoad.toFloat()) }
        
        // Battery Chart
        val batteryDataSet = LineDataSet(batteryEntries, "Батарея").apply {
            color = getColor(R.color.battery_color)
            setCircleColor(getColor(R.color.battery_color))
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = getColor(R.color.battery_color)
            fillAlpha = 60
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextColor = getColor(R.color.text_secondary)
            valueTextSize = 10f
        }
        binding.batteryChart.data = LineData(batteryDataSet)
        binding.batteryChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) labels[index] else ""
            }
        }
        binding.batteryChart.notifyDataSetChanged()
        binding.batteryChart.invalidate()
        
        // Grid Chart
        val gridDataSet = LineDataSet(gridEntries, "Мережа").apply {
            color = getColor(R.color.grid_color)
            setCircleColor(getColor(R.color.grid_color))
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = getColor(R.color.grid_color)
            fillAlpha = 60
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextColor = getColor(R.color.text_secondary)
            valueTextSize = 10f
        }
        binding.gridChart.data = LineData(gridDataSet)
        binding.gridChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) labels[index] else ""
            }
        }
        binding.gridChart.notifyDataSetChanged()
        binding.gridChart.invalidate()
        
        // Home Chart
        val homeDataSet = LineDataSet(homeEntries, "Дім").apply {
            color = getColor(R.color.home_color)
            setCircleColor(getColor(R.color.home_color))
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = getColor(R.color.home_color)
            fillAlpha = 60
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextColor = getColor(R.color.text_secondary)
            valueTextSize = 10f
        }
        binding.homeChart.data = LineData(homeDataSet)
        binding.homeChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) labels[index] else ""
            }
        }
        binding.homeChart.notifyDataSetChanged()
        binding.homeChart.invalidate()
    }
    
    private fun updateStats(latest: DataRecord) {
        // Battery Card
        val batteryCardView = findViewById<View>(R.id.batteryCard)
        val batteryCardBinding = CardStatBinding.bind(batteryCardView)
        batteryCardBinding.statLabel.text = "🔋 Батарея"
        batteryCardBinding.statValue.text = latest.batterySoc.toString()
        batteryCardBinding.statUnit.text = "%"
        
        // Grid Card
        val gridCardView = findViewById<View>(R.id.gridCard)
        val gridCardBinding = CardStatBinding.bind(gridCardView)
        gridCardBinding.statLabel.text = "⚡ Мережа"
        gridCardBinding.statValue.text = latest.gridLoad.toString()
        gridCardBinding.statUnit.text = "Вт"
        
        // Home Card
        val homeCardView = findViewById<View>(R.id.homeCard)
        val homeCardBinding = CardStatBinding.bind(homeCardView)
        homeCardBinding.statLabel.text = "🏠 Дім"
        homeCardBinding.statValue.text = latest.homeLoad.toString()
        homeCardBinding.statUnit.text = "Вт"
    }
    
    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        val currentUrl = ApiClient.getServerUrl(this)
        dialogBinding.serverUrlInput.setText(currentUrl)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Налаштування")
            .setView(dialogBinding.root)
            .setPositiveButton("Зберегти") { _, _ ->
                val newUrl = dialogBinding.serverUrlInput.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    val url = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
                    ApiClient.setServerUrl(this, url)
                    Toast.makeText(this, "URL оновлено", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton("Скасувати", null)
            .create()
        
        dialog.show()
    }
    
    private fun startAutoRefresh() {
        refreshTimer?.cancel()
        refreshTimer = Timer()
        refreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    loadData()
                }
            }
        }, 60000, 60000) // Кожні 60 секунд
    }
    
    override fun onDestroy() {
        super.onDestroy()
        refreshTimer?.cancel()
        // Не зупиняємо сервіс - він має працювати постійно
    }
}
