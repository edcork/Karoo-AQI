package com.example.karoo.aqi.core

/**
 * AQI calculator contract (pure Kotlin).
 *
 * Implementations must:
 * - compute sub-indices per available pollutant (null pollutant concentration => no sub-index)
 * - choose DOMINANT pollutant = max sub-index
 * - return the final index and dominant pollutant
 */
interface AqiCalculator {
    val standard: AqiStandard

    /**
     * @return a result if at least one sub-index/category could be computed, otherwise null.
     */
    fun calculate(pollutantsUgM3: Pollutants): AqiCalculation?
}

data class AqiCalculation(
    val standard: AqiStandard,
    val index: Int?,
    val category: String?,
    val dominant: Pollutant,
    val subIndices: Map<Pollutant, Int>,
    val subCategories: Map<Pollutant, String>,
)

