package com.example.karoo.aqi.core

/**
 * "Garmin-ready" models: pure Kotlin, no Android/Karoo imports.
 */

enum class AqiStandard { US, CN, IN, UK, EU, AU }

enum class DisplayMode {
    AQI,
    PM25,
    PM10,
    O3,
    NO2,
    SO2,
    CO,
}

data class AqiConfig(
    val apiKey: String,
    val standard: AqiStandard,
    val displayMode: DisplayMode,
    val alertThreshold: Int,
    val alertEnabled: Boolean,
)

/**
 * A geo point in degrees.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

/**
 * Concentrations in µg/m³ unless otherwise noted by the upstream API.
 *
 * IMPORTANT: Missing pollutant values are represented as null (never 0 by default).
 * If the API explicitly provides 0, it is preserved as 0.0.
 */
data class Pollutants(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val co: Double? = null,
)

enum class Pollutant {
    PM25,
    PM10,
    O3,
    NO2,
    SO2,
    CO,
}

fun Pollutants.get(p: Pollutant): Double? = when (p) {
    Pollutant.PM25 -> pm25
    Pollutant.PM10 -> pm10
    Pollutant.O3 -> o3
    Pollutant.NO2 -> no2
    Pollutant.SO2 -> so2
    Pollutant.CO -> co
}

/**
 * A lightweight forecast indicator intended for a compact "secondary" UI value.
 * More detailed forecast support can be added later without changing the core contract.
 */
data class ForecastIndicator(
    val trend: Trend,
    val deltaAqi: Int? = null,
    val horizonMinutes: Int? = null,
) {
    enum class Trend { UP, DOWN, FLAT, UNKNOWN }
}

data class AqiReading(
    val standard: AqiStandard,
    val aqi: Int?,
    val pollutants: Pollutants,
    val forecast: ForecastIndicator? = null,
    val observedAtEpochMs: Long? = null,
)

/**
 * Centralized helpers to enforce "zero vs null" semantics.
 */
object ZeroVsNull {
    /**
     * Returns null if the value is missing (null). Returns 0.0 if the API explicitly provided 0.
     */
    fun concentrationOrNull(value: Double?): Double? = value
}

