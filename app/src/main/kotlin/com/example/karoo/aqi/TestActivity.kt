package com.example.karoo.aqi

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.example.karoo.aqi.core.AqiCalculator
import com.example.karoo.aqi.core.AqiStandard
import com.example.karoo.aqi.core.AustraliaNepmCategoryCalculator
import com.example.karoo.aqi.core.ChinaMepAqiCalculator
import com.example.karoo.aqi.core.CoreParsers
import com.example.karoo.aqi.core.DisplayMode
import com.example.karoo.aqi.core.EuropeCaqiCalculator
import com.example.karoo.aqi.core.IndiaCpcbAqiCalculator
import com.example.karoo.aqi.core.OwmForecastTrend
import com.example.karoo.aqi.core.OwmParsers
import com.example.karoo.aqi.core.Pollutant
import com.example.karoo.aqi.core.UkDaqiCalculator
import com.example.karoo.aqi.core.UsEpaAqiCalculator
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class TestActivity : Activity() {
    private lateinit var textView: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var configManager: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatically create a simple scrollable text view
        textView = TextView(this).apply {
            setPadding(48, 48, 48, 48)
            textSize = 14f
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        setContentView(scrollView)

        configManager = ConfigManager(applicationContext)

        log("🚀 Starting AQI Test Activity...")

        scope.launch {
            // BYPASS FILE READING FOR EMULATOR TEST
            val cfg = com.example.karoo.aqi.core.AqiConfig(
                apiKey = "8f27087925beb5dfc72d540201901642",
                standard = AqiStandard.US,
                displayMode = DisplayMode.AQI,
                alertThreshold = 100,
                alertEnabled = true
            )

            log("✅ Hardcoded Config loaded successfully!")
            log("Standard: ${cfg.standard}")
            log("Display Mode: ${cfg.displayMode}")
            log("--------------------------------")

            // Mock GPS Coordinates (e.g., Central London)
            val lat = 51.5074
            val lon = -0.1278
            log("📡 Fetching OWM data for Lat: $lat, Lon: $lon...")

            val currentUrl = "https://api.openweathermap.org/data/2.5/air_pollution?lat=$lat&lon=$lon&appid=${cfg.apiKey}"
            val forecastUrl = "https://api.openweathermap.org/data/2.5/air_pollution/forecast?lat=$lat&lon=$lon&appid=${cfg.apiKey}"

            // 1. Fetch and Parse Current Data
            val currentJson = fetch(currentUrl)
            if (currentJson == null) {
                log("❌ ERROR: Failed to fetch current conditions. Check API key and internet.")
                return@launch
            }

            log("RAW JSON: $currentJson")

            val current = CoreParsers.parseOwmCurrent(currentJson, cfg.standard).getOrNull()
            if (current == null) {
                log("❌ ERROR: Failed to parse current conditions.")
                return@launch
            }
            log("✅ Current OWM JSON parsed.")

            // 2. Run the Calculator
            val calculator = calculatorFor(cfg.standard)
            val calc = calculator.calculate(current.pollutants)

            if (calc == null) {
                log("❌ ERROR: Calculator returned null (missing vital pollutant data).")
                return@launch
            }

            log("🧮 Calculation Success!")
            log("Dominant Pollutant: ${calc.dominant}")
            log("Calculated Index/Category: ${calc.index ?: calc.category}")
            log("Sub-indices calculated: ${calc.subIndices.keys.joinToString()}")
            log("--------------------------------")

            // 3. Fetch and Parse Forecast
            log("📡 Fetching 3-6 hour forecast...")
            val forecastJson = fetch(forecastUrl)
            if (forecastJson != null) {
                val forecast = OwmParsers.parseForecast(forecastJson).getOrNull()
                if (forecast != null) {
                    val primaryPol = primaryPollutantFor(cfg.displayMode, calc.dominant)
                    val trend = OwmForecastTrend.classify(
                        nowEpochMs = System.currentTimeMillis(),
                        nowPollutants = current.pollutants,
                        forecast = forecast,
                        primary = primaryPol
                    ).trend
                    log("📈 Forecast Trend: $trend")
                } else {
                    log("❌ ERROR: Failed to parse forecast JSON.")
                }
            }
        }
    }

    private fun log(message: String) {
        textView.append("$message\n\n")
        android.util.Log.d("AQI_TEST", message) // <-- Add this line
    }

    private suspend fun fetch(urlString: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream.use { it.bufferedReader().readText() }
        }.getOrNull()
    }

    private fun calculatorFor(standard: AqiStandard): AqiCalculator = when (standard) {
        AqiStandard.US -> UsEpaAqiCalculator()
        AqiStandard.CN -> ChinaMepAqiCalculator()
        AqiStandard.IN -> IndiaCpcbAqiCalculator()
        AqiStandard.UK -> UkDaqiCalculator()
        AqiStandard.EU -> EuropeCaqiCalculator()
        AqiStandard.AU -> AustraliaNepmCategoryCalculator()
    }

    private fun primaryPollutantFor(displayMode: DisplayMode, dominant: Pollutant): Pollutant = when (displayMode) {
        DisplayMode.AQI -> dominant
        DisplayMode.PM25 -> Pollutant.PM25
        DisplayMode.PM10 -> Pollutant.PM10
        DisplayMode.O3 -> Pollutant.O3
        DisplayMode.NO2 -> Pollutant.NO2
        DisplayMode.SO2 -> Pollutant.SO2
        DisplayMode.CO -> Pollutant.CO
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        configManager.stop()
    }
}