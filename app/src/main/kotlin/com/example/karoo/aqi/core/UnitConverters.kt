package com.example.karoo.aqi.core

/**
 * Unit conversions at standard conditions: 25°C (298.15K) and 1 atm.
 *
 * Relationship: ppm = (mg/m³) * 24.45 / MW
 * Therefore: ppb = (µg/m³) * 24.45 / MW
 * And: ppm = (µg/m³) * 24.45 / (MW * 1000)
 */
object UnitConverters {
    private const val MOLAR_VOLUME_L_PER_MOL = 24.45

    private const val MW_O3 = 48.00
    private const val MW_NO2 = 46.0055
    private const val MW_SO2 = 64.066
    private const val MW_CO = 28.01

    fun ugM3ToPpbO3(ugM3: Double?): Double? = ugM3?.let { it * MOLAR_VOLUME_L_PER_MOL / MW_O3 }
    fun ugM3ToPpbNo2(ugM3: Double?): Double? = ugM3?.let { it * MOLAR_VOLUME_L_PER_MOL / MW_NO2 }
    fun ugM3ToPpbSo2(ugM3: Double?): Double? = ugM3?.let { it * MOLAR_VOLUME_L_PER_MOL / MW_SO2 }

    fun ugM3ToPpmCo(ugM3: Double?): Double? = ugM3?.let { it * MOLAR_VOLUME_L_PER_MOL / (MW_CO * 1000.0) }

    fun ugM3ToMgM3(ugM3: Double?): Double? = ugM3?.let { it / 1000.0 }
}

