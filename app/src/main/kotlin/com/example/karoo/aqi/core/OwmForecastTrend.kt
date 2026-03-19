package com.example.karoo.aqi.core

import kotlin.math.abs

/**
 * Forecast trend classification for a single "primary" pollutant.
 *
 * Looks ahead between [minLookaheadHours] and [maxLookaheadHours] inclusive, relative to [nowEpochMs],
 * and compares the average concentration in that window against the current concentration.
 */
object OwmForecastTrend {
    enum class Trend { IMPROVING, WORSENING, STABLE, UNKNOWN }

    data class Result(
        val trend: Trend,
        val primary: Pollutant,
        val nowValue: Double?,
        val futureAvgValue: Double?,
        val horizonHours: IntRange,
    )

    /**
     * @param nowPollutants "current" values (from current endpoint's list[0].components)
     * @param primary pollutant to evaluate trend on (UI can choose based on display_mode or dominant pollutant later)
     */
    fun classify(
        nowEpochMs: Long,
        nowPollutants: Pollutants,
        forecast: OwmForecast,
        primary: Pollutant,
        minLookaheadHours: Int = 3,
        maxLookaheadHours: Int = 6,
        stableAbsUgM3: Double = 1.0,
        stableRelative: Double = 0.05,
    ): Result {
        val nowVal = nowPollutants.get(primary)
        val windowStart = nowEpochMs + minLookaheadHours * 60 * 60 * 1_000L
        val windowEnd = nowEpochMs + maxLookaheadHours * 60 * 60 * 1_000L

        val futureVals = forecast.entries
            .asSequence()
            .filter { it.dtEpochMs in windowStart..windowEnd }
            .map { it.pollutants.get(primary) }
            .filterNotNull()
            .toList()

        val futureAvg = if (futureVals.isNotEmpty()) futureVals.average() else null

        val trend = when {
            nowVal == null || futureAvg == null -> Trend.UNKNOWN
            isStable(nowVal, futureAvg, stableAbsUgM3, stableRelative) -> Trend.STABLE
            futureAvg > nowVal -> Trend.WORSENING
            else -> Trend.IMPROVING
        }

        return Result(
            trend = trend,
            primary = primary,
            nowValue = nowVal,
            futureAvgValue = futureAvg,
            horizonHours = minLookaheadHours..maxLookaheadHours,
        )
    }

    private fun isStable(now: Double, future: Double, absThreshold: Double, relThreshold: Double): Boolean {
        val absDelta = abs(future - now)
        if (absDelta <= absThreshold) return true
        val denom = kotlin.math.max(abs(now), 1e-9)
        return (absDelta / denom) <= relThreshold
    }
}

