package com.example.karoo.aqi.core

/**
 * OpenWeatherMap Air Pollution API models (core/pure Kotlin).
 *
 * Current: `/data/2.5/air_pollution`
 * Forecast: `/data/2.5/air_pollution/forecast`
 *
 * NOTE: OWM provides concentrations in µg/m³ for components.
 */

data class OwmCurrent(
    val coord: GeoPoint?,
    val dtEpochMs: Long,
    val owmAqi: Int?, // OWM's own 1..5 scale if present
    val pollutants: Pollutants,
    val standard: AqiStandard,
)

data class OwmForecastEntry(
    val dtEpochMs: Long,
    val pollutants: Pollutants,
)

data class OwmForecast(
    val coord: GeoPoint?,
    val entries: List<OwmForecastEntry>,
)

