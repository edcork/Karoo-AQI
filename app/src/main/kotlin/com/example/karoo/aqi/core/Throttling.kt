package com.example.karoo.aqi.core

import kotlin.math.*

/**
 * Decides when an API fetch is allowed based on time and distance deltas.
 *
 * Rules:
 * - Fetch if no previous successful fetch
 * - OR moved >= 1km from last fetch location
 * - OR >= 10 minutes since last fetch time
 */
class FetchThrottler(
    private val minDistanceMeters: Double = 1_000.0,
    private val minElapsedMs: Long = 10 * 60 * 1_000L,
) {
    data class Snapshot(
        val fetchedAtEpochMs: Long,
        val fetchedAt: GeoPoint,
    )

    sealed class Decision {
        data object Fetch : Decision()
        data class Skip(
            val elapsedMs: Long,
            val distanceMeters: Double,
            val remainingMs: Long,
            val remainingMeters: Double,
        ) : Decision()
    }

    private var last: Snapshot? = null

    fun lastSnapshot(): Snapshot? = last

    /**
     * Evaluate if we should fetch at [nowEpochMs] from [current].
     */
    fun shouldFetch(nowEpochMs: Long, current: GeoPoint): Decision {
        val prev = last ?: return Decision.Fetch
        val elapsed = nowEpochMs - prev.fetchedAtEpochMs
        val distance = haversineMeters(prev.fetchedAt, current)

        val timeOk = elapsed >= minElapsedMs
        val distOk = distance >= minDistanceMeters

        return if (timeOk || distOk) {
            Decision.Fetch
        } else {
            Decision.Skip(
                elapsedMs = elapsed,
                distanceMeters = distance,
                remainingMs = (minElapsedMs - elapsed).coerceAtLeast(0),
                remainingMeters = (minDistanceMeters - distance).coerceAtLeast(0.0),
            )
        }
    }

    /**
     * Call this only when an API fetch succeeds (per your requirement).
     */
    fun recordSuccess(nowEpochMs: Long, at: GeoPoint) {
        last = Snapshot(fetchedAtEpochMs = nowEpochMs, fetchedAt = at)
    }

    companion object {
        /**
         * Great-circle distance (meters) using the haversine formula.
         */
        fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
            val r = 6_371_000.0
            val lat1 = a.lat.toRadians()
            val lat2 = b.lat.toRadians()
            val dLat = (b.lat - a.lat).toRadians()
            val dLon = (b.lon - a.lon).toRadians()

            val sinDLat = sin(dLat / 2.0)
            val sinDLon = sin(dLon / 2.0)
            val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
            val c = 2.0 * asin(min(1.0, sqrt(h)))
            return r * c
        }

        private fun Double.toRadians(): Double = this * (Math.PI / 180.0)
    }
}

