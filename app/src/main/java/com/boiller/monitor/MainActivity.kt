package com.boiller.monitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.boiller.monitor.api.ApiClient
import com.boiller.monitor.api.DataRecord
import com.boiller.monitor.databinding.ActivityMainBinding
import com.boiller.monitor.databinding.CardStatBinding
import com.boiller.monitor.utils.DateFormatter
import com.boiller.monitor.websocket.WebSocketService
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var refreshTimer: Timer? = null
    private lateinit var toolbarUpdateTime: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        // Додаємо TextView для дати оновлення в toolbar
        toolbarUpdateTime = TextView(this).apply {
            text = "-"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
            layoutParams = androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                marginEnd = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
            }
        }
        binding.toolbar.addView(toolbarUpdateTime)
        
        setupCharts()
        setupClickListeners()
        loadData()
        
        // Автоматичне оновлення кожні 60 секунд (fallback)
        startAutoRefresh()
        
        // Підключаємося до WebSocket для real-time оновлень
        val serverUrl = ApiClient.getServerUrl(this)
        WebSocketService.getInstance(this).connect(serverUrl)
        
        // Реєструємо BroadcastReceiver для оновлення UI з WebSocket
        registerWebSocketReceiver()
        
        // Перевіряємо дозволи для нотифікацій
        checkNotificationPermission()
        
        // Запускаємо сервіс нотифікацій
        NotificationService.startService(this)
    }
    
    private fun registerWebSocketReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction("com.boiller.monitor.DATA_UPDATE")
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.boiller.monitor.DATA_UPDATE") {
                    val timestamp = intent.getStringExtra("timestamp") ?: return
                    val batterySoc = intent.getIntExtra("batterySoc", 0)
                    val gridLoad = intent.getIntExtra("gridLoad", 0)
                    val homeLoad = intent.getIntExtra("homeLoad", 0)
                    
                    val data = DataRecord(
                        timestamp = timestamp,
                        batterySoc = batterySoc,
                        gridLoad = gridLoad,
                        homeLoad = homeLoad,
                        gridStatus = gridLoad > 0
                    )
                    
                    // Оновлюємо UI
                    lifecycleScope.launch {
                        updateUIWithData(data)
                    }
                }
            }
        }, filter, flags)
    }
    
    private suspend fun updateUIWithData(data: DataRecord) {
        // Оновлюємо статистику
        updateStats(data)
        
        // Оновлюємо час в toolbar
        toolbarUpdateTime.text = DateFormatter.formatTime(data.timestamp)
        
        // Для повного оновлення графіків потрібно завантажити історію
        // Але для швидкого оновлення можна просто оновити статистику
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
        setupChart(binding.batteryChart, "Батарея", getColor(R.color.battery_color))
        setupChart(binding.gridChart, "Мережа", getColor(R.color.grid_color))
        setupChart(binding.homeChart, "Дім", getColor(R.color.home_color))
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
        leftAxis.axisMinimum = 0f // Мінімальне значення - 0, графік не буде йти нижче нуля
        
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false
        
        chart.setNoDataText("Немає даних")
        chart.setNoDataTextColor(getColor(R.color.text_secondary))
        
    }
    
    private fun hideMarkersOnOtherCharts(activeChart: com.github.mikephil.charting.charts.LineChart) {
        val items = listOf(
            Triple(binding.batteryChart, binding.batteryMarkerOverlay, "%"),
            Triple(binding.gridChart, binding.gridMarkerOverlay, "Вт"),
            Triple(binding.homeChart, binding.homeMarkerOverlay, "Вт"),
        )
        items.forEach { (chart, overlay, _) ->
            if (chart != activeChart) {
                chart.highlightValue(null, false)
                overlay.visibility = View.GONE
            }
        }
    }

    private fun showOverlayForSelection(
        chart: com.github.mikephil.charting.charts.LineChart,
        overlay: TextView,
        labels: List<String>,
        unit: String,
        e: Entry?,
        h: com.github.mikephil.charting.highlight.Highlight?
    ) {
        if (e == null || h == null) return

        val index = e.x.toInt()
        val value = e.y.toInt()
        val time = if (index in labels.indices) labels[index] else ""
        overlay.text = "$time\n$value $unit"

        // Клік справа -> оверлей зліва, клік зліва -> оверлей справа (в межах viewport)
        val vp = chart.viewPortHandler
        val center = vp.contentLeft() + vp.contentWidth() / 2f
        val isClickOnRight = h.xPx > center
        val gravity = if (isClickOnRight) {
            Gravity.BOTTOM or Gravity.START
        } else {
            Gravity.BOTTOM or Gravity.END
        }

        val lp = (overlay.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        lp.gravity = gravity
        overlay.layoutParams = lp

        overlay.visibility = View.VISIBLE
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
                toolbarUpdateTime.text = "Завантаження..."
                val apiService = ApiClient.getApiService(this@MainActivity)
                val response = apiService.getData()
                
                if (response.data.isNotEmpty()) {
                    updateCharts(response.data)
                    updateStats(response.data.last())
                    toolbarUpdateTime.text = DateFormatter.formatTime(response.data.last().timestamp)
                } else {
                    toolbarUpdateTime.text = "Немає даних"
                }
            } catch (e: Exception) {
                toolbarUpdateTime.text = "Помилка підключення"
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
        val batteryColor = android.graphics.Color.parseColor("#4CAF50") // Зелений
        val batteryDataSet = LineDataSet(batteryEntries, "Батарея").apply {
            color = batteryColor
            setCircleColor(batteryColor)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = batteryColor
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
        binding.batteryChart.marker = null
        binding.batteryChart.setDrawMarkers(false)
        binding.batteryChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                hideMarkersOnOtherCharts(binding.batteryChart)
                showOverlayForSelection(binding.batteryChart, binding.batteryMarkerOverlay, labels, "%", e, h)
            }

            override fun onNothingSelected() {
                binding.batteryMarkerOverlay.visibility = View.GONE
            }
        })
        binding.batteryChart.notifyDataSetChanged()
        binding.batteryChart.invalidate()
        
        // Grid Chart
        val gridColor = android.graphics.Color.parseColor("#F44336") // Червоний
        val gridDataSet = LineDataSet(gridEntries, "Мережа").apply {
            color = gridColor
            setCircleColor(gridColor)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = gridColor
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
        binding.gridChart.marker = null
        binding.gridChart.setDrawMarkers(false)
        binding.gridChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                hideMarkersOnOtherCharts(binding.gridChart)
                showOverlayForSelection(binding.gridChart, binding.gridMarkerOverlay, labels, "Вт", e, h)
            }

            override fun onNothingSelected() {
                binding.gridMarkerOverlay.visibility = View.GONE
            }
        })
        binding.gridChart.notifyDataSetChanged()
        binding.gridChart.invalidate()
        
        // Home Chart
        val homeColor = android.graphics.Color.parseColor("#2196F3") // Синій
        val homeDataSet = LineDataSet(homeEntries, "Дім").apply {
            color = homeColor
            setCircleColor(homeColor)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawFilled(true)
            fillColor = homeColor
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
        binding.homeChart.marker = null
        binding.homeChart.setDrawMarkers(false)
        binding.homeChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                hideMarkersOnOtherCharts(binding.homeChart)
                showOverlayForSelection(binding.homeChart, binding.homeMarkerOverlay, labels, "Вт", e, h)
            }

            override fun onNothingSelected() {
                binding.homeMarkerOverlay.visibility = View.GONE
            }
        })
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
        // Відключаємо WebSocket при закритті додатку
        WebSocketService.getInstance(this).disconnect()
        // Не зупиняємо сервіс - він має працювати постійно
    }
}
