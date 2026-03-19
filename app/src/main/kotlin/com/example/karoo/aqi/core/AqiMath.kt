package com.example.karoo.aqi.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object AqiMath {
    data class Segment(
        val cLow: Double,
        val cHigh: Double,
        val iLow: Int,
        val iHigh: Int,
    )

    /**
     * Piecewise linear interpolation:
     * \( I = (I_{hi}-I_{lo})/(C_{hi}-C_{lo}) * (C - C_{lo}) + I_{lo} \)
     *
     * Returns null if [c] is null or outside all provided segments and [allowExtrapolateLast] is false.
     */
    fun piecewiseIndex(
        c: Double?,
        segments: List<Segment>,
        allowExtrapolateLast: Boolean = false,
    ): Int? {
        if (c == null) return null
        if (segments.isEmpty()) return null

        val seg = segments.firstOrNull { c >= it.cLow && c <= it.cHigh }
            ?: if (allowExtrapolateLast && c > segments.last().cHigh) segments.last() else null
            ?: return null

        val denom = (seg.cHigh - seg.cLow)
        if (denom <= 0.0) return null
        val raw = ((seg.iHigh - seg.iLow).toDouble() / denom) * (c - seg.cLow) + seg.iLow.toDouble()
        return floor(raw).toInt().coerceIn(min(seg.iLow, seg.iHigh), max(seg.iLow, seg.iHigh))
    }

    fun dominantNumeric(sub: Map<Pollutant, Int>): Pair<Pollutant, Int>? {
        val best = sub.maxByOrNull { it.value } ?: return null
        return best.key to best.value
    }

    fun truncateTo1Decimal(v: Double): Double = floor(v * 10.0) / 10.0

    fun roundDownToInt(v: Double): Int = floor(v).toInt()

    fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))
}

