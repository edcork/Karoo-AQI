package com.example.karoo.aqi.core

/**
 * OWM-specific JSON parsing utilities (pure Kotlin).
 */
object OwmParsers {

    /**
     * Parse `/data/2.5/air_pollution/forecast`
     *
     * CRITICAL: Missing pollutant keys in `components` => null.
     */
    fun parseForecast(json: String): Result<OwmForecast> = runCatching {
        val root = MiniJson.parseToAny(json).asObject("OWM forecast root")
        val coord = root.obj("coord")
        val lon = coord?.number("lon")
        val lat = coord?.number("lat")

        val list = root.array("list") ?: emptyList()
        val entries = list.mapNotNull { any ->
            val obj = any.asObject("OWM forecast list[]")
            val dtSeconds = obj.number("dt")?.toLong() ?: return@mapNotNull null
            val components = obj.obj("components") ?: emptyMap()
            OwmForecastEntry(
                dtEpochMs = dtSeconds * 1_000L,
                pollutants = Pollutants(
                    pm25 = ZeroVsNull.concentrationOrNull(components.number("pm2_5")),
                    pm10 = ZeroVsNull.concentrationOrNull(components.number("pm10")),
                    o3 = ZeroVsNull.concentrationOrNull(components.number("o3")),
                    no2 = ZeroVsNull.concentrationOrNull(components.number("no2")),
                    so2 = ZeroVsNull.concentrationOrNull(components.number("so2")),
                    co = ZeroVsNull.concentrationOrNull(components.number("co")),
                ),
            )
        }

        OwmForecast(
            coord = if (lat != null && lon != null) GeoPoint(lat = lat, lon = lon) else null,
            entries = entries.sortedBy { it.dtEpochMs },
        )
    }
}

