package com.example.karoo.aqi.core

import com.example.karoo.aqi.core.AqiMath.Segment

class UsEpaAqiCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.US

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val sub = linkedMapOf<Pollutant, Int>()

        // PM2.5: truncate to 0.1
        val pm25 = pollutantsUgM3.pm25?.let(AqiMath::truncateTo1Decimal)
        AqiMath.piecewiseIndex(
            pm25,
            segments = listOf(
                Segment(0.0, 9.0, 0, 50),
                Segment(9.1, 35.4, 51, 100),
                Segment(35.5, 55.4, 101, 150),
                Segment(55.5, 125.4, 151, 200),
                Segment(125.5, 225.4, 201, 300),
                Segment(225.5, 325.4, 301, 400),
                Segment(325.5, 500.4, 401, 500),
            ),
        )?.let { sub[Pollutant.PM25] = it }

        // PM10: integer
        val pm10 = pollutantsUgM3.pm10?.let { AqiMath.roundDownToInt(it).toDouble() }
        AqiMath.piecewiseIndex(
            pm10,
            segments = listOf(
                Segment(0.0, 54.0, 0, 50),
                Segment(55.0, 154.0, 51, 100),
                Segment(155.0, 254.0, 101, 150),
                Segment(255.0, 354.0, 151, 200),
                Segment(355.0, 424.0, 201, 300),
                Segment(425.0, 504.0, 301, 400),
                Segment(505.0, 604.0, 401, 500),
            ),
        )?.let { sub[Pollutant.PM10] = it }

        // O3: ppb, truncate to integer before matching.
        //
        // Per provided rules (OWM provides an hourly snapshot, not rolling 8h):
        // 1) Compute 8-hour table AQI (caps at 200 ppb / 300 AQI).
        // 2) Compute 1-hour table AQI (defined from 125 ppb upward).
        // 3) Use max(8h, 1h).
        val o3PpbInt = UnitConverters.ugM3ToPpbO3(pollutantsUgM3.o3)
            ?.let { AqiMath.roundDownToInt(it).toDouble() }
        val o3Aqi8h = AqiMath.piecewiseIndex(
            o3PpbInt?.let { if (it > 200.0) 200.0 else it },
            segments = listOf(
                Segment(0.0, 54.0, 0, 50),
                Segment(55.0, 70.0, 51, 100),
                Segment(71.0, 85.0, 101, 150),
                Segment(86.0, 105.0, 151, 200),
                Segment(106.0, 200.0, 201, 300),
            ),
        )
        val o3Aqi1h = if (o3PpbInt != null && o3PpbInt >= 125.0) {
            AqiMath.piecewiseIndex(
                o3PpbInt,
                segments = listOf(
                    Segment(125.0, 164.0, 101, 150),
                    Segment(165.0, 204.0, 151, 200),
                    Segment(205.0, 404.0, 201, 300),
                    Segment(405.0, 504.0, 301, 400),
                    Segment(505.0, 604.0, 401, 500),
                ),
                allowExtrapolateLast = true,
            )
        } else null
        val o3Aqi = listOfNotNull(o3Aqi8h, o3Aqi1h).maxOrNull()
        if (o3Aqi != null) sub[Pollutant.O3] = o3Aqi

        // NO2: ppb, integer (1h)
        val no2Ppb = UnitConverters.ugM3ToPpbNo2(pollutantsUgM3.no2)
            ?.let { AqiMath.roundDownToInt(it).toDouble() }
        AqiMath.piecewiseIndex(
            no2Ppb,
            segments = listOf(
                Segment(0.0, 53.0, 0, 50),
                Segment(54.0, 100.0, 51, 100),
                Segment(101.0, 360.0, 101, 150),
                Segment(361.0, 649.0, 151, 200),
                Segment(650.0, 1249.0, 201, 300),
                Segment(1250.0, 1649.0, 301, 400),
                Segment(1650.0, 2049.0, 401, 500),
            ),
        )?.let { sub[Pollutant.NO2] = it }

        // SO2: ppb, integer (1h) only up to 304/200
        val so2Ppb = UnitConverters.ugM3ToPpbSo2(pollutantsUgM3.so2)
            ?.let { AqiMath.roundDownToInt(it).toDouble() }
        AqiMath.piecewiseIndex(
            so2Ppb,
            segments = listOf(
                Segment(0.0, 35.0, 0, 50),
                Segment(36.0, 75.0, 51, 100),
                Segment(76.0, 185.0, 101, 150),
                Segment(186.0, 304.0, 151, 200),
            ),
        )?.let { sub[Pollutant.SO2] = it }

        // CO: ppm, truncate to 0.1 then index
        val coPpm = UnitConverters.ugM3ToPpmCo(pollutantsUgM3.co)?.let(AqiMath::truncateTo1Decimal)
        AqiMath.piecewiseIndex(
            coPpm,
            segments = listOf(
                Segment(0.0, 4.4, 0, 50),
                Segment(4.5, 9.4, 51, 100),
                Segment(9.5, 12.4, 101, 150),
                Segment(12.5, 15.4, 151, 200),
                Segment(15.5, 30.4, 201, 300),
                Segment(30.5, 40.4, 301, 400),
                Segment(40.5, 50.4, 401, 500),
            ),
        )?.let { sub[Pollutant.CO] = it }

        val dom = AqiMath.dominantNumeric(sub) ?: return null
        return AqiCalculation(
            standard = standard,
            index = dom.second,
            category = null,
            dominant = dom.first,
            subIndices = sub,
            subCategories = emptyMap(),
        )
    }
}

class ChinaMepAqiCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.CN

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val sub = linkedMapOf<Pollutant, Int>()

        AqiMath.piecewiseIndex(
            pollutantsUgM3.pm25,
            segments = listOf(
                Segment(0.0, 35.0, 0, 50),
                Segment(35.0, 75.0, 51, 100),
                Segment(75.0, 115.0, 101, 150),
                Segment(115.0, 150.0, 151, 200),
                Segment(150.0, 250.0, 201, 300),
                Segment(250.0, 350.0, 301, 400),
                Segment(350.0, 500.0, 401, 500),
            ),
        )?.let { sub[Pollutant.PM25] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.pm10,
            segments = listOf(
                Segment(0.0, 50.0, 0, 50),
                Segment(50.0, 150.0, 51, 100),
                Segment(150.0, 250.0, 101, 150),
                Segment(250.0, 350.0, 151, 200),
                Segment(350.0, 420.0, 201, 300),
                Segment(420.0, 500.0, 301, 400),
                Segment(500.0, 600.0, 401, 500),
            ),
        )?.let { sub[Pollutant.PM10] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.o3,
            segments = listOf(
                Segment(0.0, 160.0, 0, 50),
                Segment(160.0, 200.0, 51, 100),
                Segment(200.0, 300.0, 101, 150),
                Segment(300.0, 400.0, 151, 200),
                Segment(400.0, 800.0, 201, 300),
                Segment(800.0, 1000.0, 301, 400),
                Segment(1000.0, 1200.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.O3] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.no2,
            segments = listOf(
                Segment(0.0, 40.0, 0, 50),
                Segment(40.0, 80.0, 51, 100),
                Segment(80.0, 180.0, 101, 150),
                Segment(180.0, 280.0, 151, 200),
                Segment(280.0, 565.0, 201, 300),
                Segment(565.0, 750.0, 301, 400),
                Segment(750.0, 940.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.NO2] = it }

        val dom = AqiMath.dominantNumeric(sub) ?: return null
        return AqiCalculation(
            standard = standard,
            index = dom.second,
            category = null,
            dominant = dom.first,
            subIndices = sub,
            subCategories = emptyMap(),
        )
    }
}

class IndiaCpcbAqiCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.IN

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val sub = linkedMapOf<Pollutant, Int>()

        // Note: CPCB bands include some wide jumps (e.g. 101-200). We interpolate within each band.
        AqiMath.piecewiseIndex(
            pollutantsUgM3.pm25,
            segments = listOf(
                Segment(0.0, 30.0, 0, 50),
                Segment(31.0, 60.0, 51, 100),
                Segment(61.0, 90.0, 101, 200),
                Segment(91.0, 120.0, 201, 300),
                Segment(121.0, 250.0, 301, 400),
                Segment(250.0, 1000.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.PM25] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.pm10,
            segments = listOf(
                Segment(0.0, 50.0, 0, 50),
                Segment(51.0, 100.0, 51, 100),
                Segment(101.0, 250.0, 101, 200),
                Segment(251.0, 350.0, 201, 300),
                Segment(351.0, 430.0, 301, 400),
                Segment(430.0, 2000.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.PM10] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.no2,
            segments = listOf(
                Segment(0.0, 40.0, 0, 50),
                Segment(41.0, 80.0, 51, 100),
                Segment(81.0, 180.0, 101, 200),
                Segment(181.0, 280.0, 201, 300),
                Segment(281.0, 400.0, 301, 400),
                Segment(400.0, 2000.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.NO2] = it }

        AqiMath.piecewiseIndex(
            pollutantsUgM3.o3,
            segments = listOf(
                Segment(0.0, 50.0, 0, 50),
                Segment(51.0, 100.0, 51, 100),
                Segment(101.0, 168.0, 101, 200),
                Segment(169.0, 208.0, 201, 300),
                Segment(209.0, 748.0, 301, 400),
                Segment(748.0, 2000.0, 401, 500),
            ),
            allowExtrapolateLast = true,
        )?.let { sub[Pollutant.O3] = it }

        val dom = AqiMath.dominantNumeric(sub) ?: return null
        return AqiCalculation(
            standard = standard,
            index = dom.second,
            category = null,
            dominant = dom.first,
            subIndices = sub,
            subCategories = emptyMap(),
        )
    }
}

class UkDaqiCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.UK

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val sub = linkedMapOf<Pollutant, Int>()

        fun band(v: Double?, pollutant: Pollutant, ranges: List<Pair<ClosedFloatingPointRange<Double>, Int>>) {
            if (v == null) return
            val b = ranges.firstOrNull { v >= it.first.start && v <= it.first.endInclusive }?.second
                ?: ranges.lastOrNull()?.second
            if (b != null) sub[pollutant] = b
        }

        band(
            pollutantsUgM3.pm25,
            Pollutant.PM25,
            listOf(
                (0.0..3.0) to 1, (4.0..7.0) to 2, (8.0..11.0) to 3, (12.0..19.0) to 4, (20.0..27.0) to 5,
                (28.0..35.0) to 6, (36.0..41.0) to 7, (42.0..47.0) to 8, (48.0..53.0) to 9, (54.0..1e9) to 10,
            ),
        )
        band(
            pollutantsUgM3.pm10,
            Pollutant.PM10,
            listOf(
                (0.0..16.0) to 1, (17.0..33.0) to 2, (34.0..50.0) to 3, (51.0..66.0) to 4, (67.0..83.0) to 5,
                (84.0..100.0) to 6, (101.0..116.0) to 7, (117.0..133.0) to 8, (134.0..150.0) to 9, (151.0..1e9) to 10,
            ),
        )
        band(
            pollutantsUgM3.o3,
            Pollutant.O3,
            listOf(
                (0.0..33.0) to 1, (34.0..66.0) to 2, (67.0..100.0) to 3, (101.0..120.0) to 4, (121.0..139.0) to 5,
                (140.0..159.0) to 6, (160.0..187.0) to 7, (188.0..213.0) to 8, (214.0..239.0) to 9, (240.0..1e9) to 10,
            ),
        )
        band(
            pollutantsUgM3.no2,
            Pollutant.NO2,
            listOf(
                (0.0..67.0) to 1, (68.0..134.0) to 2, (135.0..200.0) to 3, (201.0..267.0) to 4, (268.0..334.0) to 5,
                (335.0..400.0) to 6, (401.0..467.0) to 7, (468.0..534.0) to 8, (535.0..600.0) to 9, (601.0..1e9) to 10,
            ),
        )

        val dom = sub.maxByOrNull { it.value } ?: return null
        return AqiCalculation(
            standard = standard,
            index = dom.value,
            category = null,
            dominant = dom.key,
            subIndices = sub,
            subCategories = emptyMap(),
        )
    }
}

class EuropeCaqiCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.EU

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val sub = linkedMapOf<Pollutant, Int>()

        fun gridIndex(v: Double?, grid: List<Pair<Double, Int>>): Int? {
            if (v == null) return null
            if (grid.isEmpty()) return null
            if (v <= grid.first().first) return grid.first().second
            for (idx in 0 until grid.size - 1) {
                val (c1, i1) = grid[idx]
                val (c2, i2) = grid[idx + 1]
                if (v >= c1 && v <= c2) {
                    val denom = (c2 - c1)
                    if (denom <= 0.0) return null
                    val raw = ((i2 - i1).toDouble() / denom) * (v - c1) + i1.toDouble()
                    return kotlin.math.floor(raw).toInt()
                }
            }
            // allow >100+
            val (cLast1, iLast1) = grid[grid.size - 2]
            val (cLast2, iLast2) = grid.last()
            val denom = (cLast2 - cLast1)
            if (denom <= 0.0) return iLast2
            val raw = ((iLast2 - iLast1).toDouble() / denom) * (v - cLast1) + iLast1.toDouble()
            return kotlin.math.floor(raw).toInt()
        }

        gridIndex(
            pollutantsUgM3.pm25,
            listOf(0.0 to 0, 15.0 to 25, 30.0 to 50, 55.0 to 75, 110.0 to 100),
        )?.let { sub[Pollutant.PM25] = it }

        gridIndex(
            pollutantsUgM3.pm10,
            listOf(0.0 to 0, 25.0 to 25, 50.0 to 50, 90.0 to 75, 180.0 to 100),
        )?.let { sub[Pollutant.PM10] = it }

        gridIndex(
            pollutantsUgM3.o3,
            listOf(0.0 to 0, 60.0 to 25, 120.0 to 50, 180.0 to 75, 240.0 to 100),
        )?.let { sub[Pollutant.O3] = it }

        gridIndex(
            pollutantsUgM3.no2,
            listOf(0.0 to 0, 50.0 to 25, 100.0 to 50, 200.0 to 75, 400.0 to 100),
        )?.let { sub[Pollutant.NO2] = it }

        val dom = AqiMath.dominantNumeric(sub) ?: return null
        return AqiCalculation(
            standard = standard,
            index = dom.second,
            category = null,
            dominant = dom.first,
            subIndices = sub,
            subCategories = emptyMap(),
        )
    }
}

class AustraliaNepmCategoryCalculator : AqiCalculator {
    override val standard: AqiStandard = AqiStandard.AU

    override fun calculate(pollutantsUgM3: Pollutants): AqiCalculation? {
        val cats = linkedMapOf<Pollutant, String>()

        fun cat(level: String): String = level

        fun nepmCategoryPm25(v: Double?): String? = when {
            v == null -> null
            v < 12.5 -> cat("Good")
            v < 25.0 -> cat("Fair")
            v < 50.0 -> cat("Poor")
            v < 150.0 -> cat("Very Poor")
            else -> cat("Extremely Poor")
        }

        fun nepmCategoryPm10(v: Double?): String? = when {
            v == null -> null
            v < 40.0 -> cat("Good")
            v < 80.0 -> cat("Fair")
            v < 120.0 -> cat("Poor")
            v < 300.0 -> cat("Very Poor")
            else -> cat("Extremely Poor")
        }

        fun nepmCategoryO3Ppb(vUgM3: Double?): String? {
            val v = UnitConverters.ugM3ToPpbO3(vUgM3)
            return when {
                v == null -> null
                v < 50.0 -> cat("Good")
                v < 100.0 -> cat("Fair")
                v < 150.0 -> cat("Poor")
                v < 300.0 -> cat("Very Poor")
                else -> cat("Extremely Poor")
            }
        }

        fun nepmCategoryNo2Ppb(vUgM3: Double?): String? {
            val v = UnitConverters.ugM3ToPpbNo2(vUgM3)
            return when {
                v == null -> null
                v < 40.0 -> cat("Good")
                v < 80.0 -> cat("Fair")
                v < 120.0 -> cat("Poor")
                v < 240.0 -> cat("Very Poor")
                else -> cat("Extremely Poor")
            }
        }

        nepmCategoryPm25(pollutantsUgM3.pm25)?.let { cats[Pollutant.PM25] = it }
        nepmCategoryPm10(pollutantsUgM3.pm10)?.let { cats[Pollutant.PM10] = it }
        nepmCategoryO3Ppb(pollutantsUgM3.o3)?.let { cats[Pollutant.O3] = it }
        nepmCategoryNo2Ppb(pollutantsUgM3.no2)?.let { cats[Pollutant.NO2] = it }

        if (cats.isEmpty()) return null

        fun severity(c: String): Int = when (c) {
            "Good" -> 1
            "Fair" -> 2
            "Poor" -> 3
            "Very Poor" -> 4
            "Extremely Poor" -> 5
            else -> 0
        }

        val dom = cats.maxByOrNull { severity(it.value) }!!

        return AqiCalculation(
            standard = standard,
            index = null,
            category = dom.value,
            dominant = dom.key,
            subIndices = emptyMap(),
            subCategories = cats,
        )
    }
}

