package com.example.karoo.aqi

import android.content.Context
import android.widget.RemoteViews
import com.example.karoo.aqi.core.AqiStandard
import com.example.karoo.aqi.core.DisplayMode
import com.example.karoo.aqi.core.FetchThrottler
import com.example.karoo.aqi.core.GeoPoint
import com.example.karoo.aqi.core.OwmForecastTrend
import com.example.karoo.aqi.core.OwmParsers
import com.example.karoo.aqi.core.Pollutant
import com.example.karoo.aqi.core.Pollutants
import com.example.karoo.aqi.core.CoreParsers
import com.example.karoo.aqi.core.AqiCalculator
import com.example.karoo.aqi.core.AustraliaNepmCategoryCalculator
import com.example.karoo.aqi.core.ChinaMepAqiCalculator
import com.example.karoo.aqi.core.EuropeCaqiCalculator
import com.example.karoo.aqi.core.IndiaCpcbAqiCalculator
import com.example.karoo.aqi.core.UkDaqiCalculator
import com.example.karoo.aqi.core.UsEpaAqiCalculator
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class AqiDataType(
    private val karooSystem: KarooSystemService,
    extensionId: String,
    private val configManager: ConfigManager,
) : DataTypeImpl(extensionId, "aqi") {

    private val throttler = FetchThrottler()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var lastPrimaryNumeric: Double? = null
    @Volatile private var alertTriggered: Boolean = false

    override fun startStream(emitter: Emitter<StreamState>) {
        // We don't currently push numeric stream points because the field is primarily graphical.
        // Keeping this no-op avoids recording incorrect values for AU NEPM category mode.
        emitter.onNext(StreamState.Idle)
        emitter.setCancellable { /* no-op */ }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val viewJob = scope.launch {
            configManager.state.collect { state ->
                val cfg = state.config
                if (cfg == null || cfg.apiKey.isBlank()) {
                    emitter.updateView(RemoteViews(context.packageName, R.layout.widget_setup_required))
                }
            }
        }

        var fetchJob: Job? = null
        var consumerId: String? = null
        val engineJob = scope.launch {
            consumerId = karooSystem.addConsumer<OnLocationChanged>(OnLocationChanged.Params) { event ->
                val point = GeoPoint(lat = event.lat, lon = event.lng)
                
                // 1. Check constraints on the receiving thread (fast)
                val cfg = configManager.state.value.config
                if (cfg == null || cfg.apiKey.isBlank()) {
                    emitter.updateView(RemoteViews(context.packageName, R.layout.widget_setup_required))
                    return@addConsumer
                }

                val now = System.currentTimeMillis()
                val decision = throttler.shouldFetch(now, point)
                if (decision is FetchThrottler.Decision.Skip) {
                    return@addConsumer
                }

                // 2. Prevent overlapping network requests
                if (fetchJob?.isActive == true) return@addConsumer

                // 3. Move network I/O and parsing to the background thread
                fetchJob = scope.launch {
                    val currentJson = fetchOwm(
                        url = "https://api.openweathermap.org/data/2.5/air_pollution?lat=${point.lat}&lon=${point.lon}&appid=${cfg.apiKey}",
                    )
                    if (currentJson == null) return@launch

                    val current = CoreParsers.parseOwmCurrent(currentJson, cfg.standard).getOrNull()
                    if (current == null) return@launch

                    val calculator = calculatorFor(cfg.standard)
                    val calc = calculator.calculate(current.pollutants)
                    if (calc == null) {
                        updateAqiView(context, emitter, primaryText = "--", trendText = "-")
                        throttler.recordSuccess(now, point)
                        return@launch
                    }

                    val primaryPollutant = primaryPollutantFor(cfg.displayMode, calc.dominant)

                    val forecastJson = fetchOwm(
                        url = "https://api.openweathermap.org/data/2.5/air_pollution/forecast?lat=${point.lat}&lon=${point.lon}&appid=${cfg.apiKey}",
                    )
                    val trend = if (forecastJson != null) {
                        val forecast = OwmParsers.parseForecast(forecastJson).getOrNull()
                        if (forecast != null) {
                            OwmForecastTrend.classify(
                                nowEpochMs = now,
                                nowPollutants = current.pollutants,
                                forecast = forecast,
                                primary = primaryPollutant,
                            ).trend
                        } else {
                            OwmForecastTrend.Trend.UNKNOWN
                        }
                    } else OwmForecastTrend.Trend.UNKNOWN

                    val trendText = when (trend) {
                        OwmForecastTrend.Trend.WORSENING -> "↑"
                        OwmForecastTrend.Trend.IMPROVING -> "↓"
                        OwmForecastTrend.Trend.STABLE -> "-"
                        OwmForecastTrend.Trend.UNKNOWN -> "-"
                    }

                    val primary = primaryValueText(cfg.displayMode, calc, current.pollutants)
                    
                    // UI updates and Alerts are safe to call from background here because
                    // karoo-ext handles the IPC marshalling internally.
                    updateAqiView(context, emitter, primaryText = primary, trendText = trendText)
                    handleAlertsIfNeeded(context, cfg, calc, current.pollutants, trendText)

                    throttler.recordSuccess(now, point)
                }
            }
            awaitCancellation()
        }

        emitter.setCancellable {
            viewJob.cancel()
            engineJob.cancel()
            consumerId?.let { karooSystem.removeConsumer(it) }
        }
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

    private fun primaryValueText(displayMode: DisplayMode, calc: com.example.karoo.aqi.core.AqiCalculation, pollutants: Pollutants): String {
        return when (displayMode) {
            DisplayMode.AQI -> calc.index?.toString() ?: (calc.category ?: "--")
            DisplayMode.PM25 -> formatConc(pollutants.pm25)
            DisplayMode.PM10 -> formatConc(pollutants.pm10)
            DisplayMode.O3 -> formatConc(pollutants.o3)
            DisplayMode.NO2 -> formatConc(pollutants.no2)
            DisplayMode.SO2 -> formatConc(pollutants.so2)
            DisplayMode.CO -> formatConc(pollutants.co)
        }
    }

    private fun formatConc(v: Double?): String = if (v == null) "--" else String.format("%.0f", v)

    private fun updateAqiView(context: Context, emitter: ViewEmitter, primaryText: String, trendText: String) {
        val rv = RemoteViews(context.packageName, R.layout.widget_aqi)
        rv.setTextViewText(R.id.aqi_primary, primaryText)
        rv.setTextViewText(R.id.aqi_secondary, trendText)
        emitter.updateView(rv)
    }

    private fun fetchOwm(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        stream.use { it.bufferedReader().readText() }
    }.getOrNull()

    private fun handleAlertsIfNeeded(
        context: Context,
        cfg: com.example.karoo.aqi.core.AqiConfig,
        calc: com.example.karoo.aqi.core.AqiCalculation,
        pollutants: Pollutants,
        trendText: String,
    ) {
        if (!cfg.alertEnabled) {
            alertTriggered = false
            return
        }

        val numericPrimary = when (cfg.displayMode) {
            DisplayMode.AQI -> calc.index?.toDouble()
            DisplayMode.PM25 -> pollutants.pm25
            DisplayMode.PM10 -> pollutants.pm10
            DisplayMode.O3 -> pollutants.o3
            DisplayMode.NO2 -> pollutants.no2
            DisplayMode.SO2 -> pollutants.so2
            DisplayMode.CO -> pollutants.co
        }
        lastPrimaryNumeric = numericPrimary

        val threshold = cfg.alertThreshold.toDouble()
        val resetLevel = threshold * 0.9

        if (numericPrimary == null) return

        if (alertTriggered) {
            if (numericPrimary <= resetLevel) {
                alertTriggered = false
            }
            return
        }

        if (numericPrimary >= threshold) {
            alertTriggered = true
            val title = "AQI Alert"
            val detail = "Value $numericPrimary $trendText (threshold ${cfg.alertThreshold})"
            karooSystem.dispatch(
                InRideAlert(
                    id = "aqi-alert",
                    icon = R.drawable.ic_extension,
                    title = title,
                    detail = detail,
                    autoDismissMs = null, // require manual dismissal
                    backgroundColor = android.R.color.holo_red_dark,
                    textColor = android.R.color.white,
                ),
            )
        }
    }
}