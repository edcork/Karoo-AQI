package com.example.karoo.aqi.core

/**
 * Parsers live in core but remain platform-agnostic (pure Kotlin).
 *
 * For now we parse:
 * - `aqi_config.json` schema (BYOK).
 * - A minimal, provider-agnostic AQI response shape (can be adapted once API is selected).
 */
object CoreParsers {

    /**
     * Expected schema:
     * {
     *   "api_key": "String",
     *   "standard": "String" (US, CN, IN, UK, EU, AU),
     *   "display_mode": "String" (AQI, PM2.5, PM10, O3, NO2),
     *   "alert_threshold": "Int",
     *   "alert_enabled": "Boolean"
     * }
     */
    fun parseConfig(json: String): Result<AqiConfig> = runCatching {
        val root = MiniJson.parseToAny(json).asObject("config root")
        val apiKey = root.string("api_key")?.trim().orEmpty()

        val standard = root.string("standard")
            ?.trim()
            ?.uppercase()
            ?.let { AqiStandard.valueOf(it) }
            ?: AqiStandard.US

        val displayMode = parseDisplayMode(root.string("display_mode")) ?: DisplayMode.AQI

        val alertThreshold = root.int("alert_threshold") ?: 100
        val alertEnabled = root.bool("alert_enabled") ?: true

        AqiConfig(
            apiKey = apiKey,
            standard = standard,
            displayMode = displayMode,
            alertThreshold = alertThreshold,
            alertEnabled = alertEnabled,
        )
    }

    /**
     * OpenWeatherMap current endpoint response:
     * `/data/2.5/air_pollution`
     *
     * Example shape (simplified):
     * {
     *   "coord": {"lon": 50, "lat": 50},
     *   "list": [{
     *     "dt": 1605182400,
     *     "main": {"aqi": 3},
     *     "components": {"pm2_5": 1.92, "pm10": 2.11, "o3": 68.0, "no2": 0.5, "so2": 0.2, "co": 201.0}
     *   }]
     * }
     *
     * CRITICAL: If a pollutant key is missing from `components`, we store null (not 0.0).
     */
    fun parseOwmCurrent(json: String, standard: AqiStandard): Result<OwmCurrent> = runCatching {
        val root = MiniJson.parseToAny(json).asObject("OWM current root")
        val coord = root.obj("coord")
        val lon = coord?.number("lon")
        val lat = coord?.number("lat")

        val list = root.array("list")
        val first = (list?.firstOrNull() as Any?).asObject("OWM list[0]")
        val dtSeconds = first.number("dt")?.toLong() ?: error("Missing list[0].dt")
        val main = first.obj("main")
        val owmAqi = main?.number("aqi")?.toInt()

        val components = first.obj("components") ?: emptyMap()
        val pollutants = Pollutants(
            pm25 = ZeroVsNull.concentrationOrNull(components.number("pm2_5")),
            pm10 = ZeroVsNull.concentrationOrNull(components.number("pm10")),
            o3 = ZeroVsNull.concentrationOrNull(components.number("o3")),
            no2 = ZeroVsNull.concentrationOrNull(components.number("no2")),
            so2 = ZeroVsNull.concentrationOrNull(components.number("so2")),
            co = ZeroVsNull.concentrationOrNull(components.number("co")),
        )

        OwmCurrent(
            coord = if (lat != null && lon != null) GeoPoint(lat = lat, lon = lon) else null,
            dtEpochMs = dtSeconds * 1_000L,
            owmAqi = owmAqi,
            pollutants = pollutants,
            standard = standard,
        )
    }

    private fun parseDisplayMode(s: String?): DisplayMode? {
        val raw = s?.trim()?.uppercase() ?: return null
        return when (raw) {
            "AQI" -> DisplayMode.AQI
            "PM2.5", "PM25", "PM_25", "PM2_5" -> DisplayMode.PM25
            "PM10", "PM_10" -> DisplayMode.PM10
            "O3" -> DisplayMode.O3
            "NO2" -> DisplayMode.NO2
            "SO2" -> DisplayMode.SO2
            "CO" -> DisplayMode.CO
            else -> null
        }
    }
}

internal fun Any?.asObject(context: String): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: error("Expected JSON object for $context")

internal fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }

internal fun Map<String, Any?>.array(key: String): List<Any?>? = this[key] as? List<Any?>

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

internal fun Map<String, Any?>.number(key: String): Double? {
    val v = this[key] ?: return null
    return when (v) {
        is Double -> v
        is Float -> v.toDouble()
        is Long -> v.toDouble()
        is Int -> v.toDouble()
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}

internal fun Map<String, Any?>.int(key: String): Int? {
    val v = number(key) ?: return null
    return v.toInt()
}

