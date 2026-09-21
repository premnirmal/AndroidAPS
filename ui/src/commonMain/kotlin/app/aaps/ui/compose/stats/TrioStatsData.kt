package app.aaps.ui.compose.stats

import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class TrioStatsRange {
    TODAY,
    HOURS_24,
    DAYS_7,
    DAYS_30,
    DAYS_90;

    fun startTime(now: Long): Long = when (this) {
        TODAY    -> MidnightTime.calc(now)
        HOURS_24 -> now - T.hours(24).msecs()
        DAYS_7   -> now - T.days(7).msecs()
        DAYS_30  -> now - T.days(30).msecs()
        DAYS_90  -> now - T.days(90).msecs()
    }
}

data class TrioTirBreakdown(
    val veryLow: Double = 0.0,
    val low: Double = 0.0,
    val inRange: Double = 0.0,
    val high: Double = 0.0,
    val veryHigh: Double = 0.0
)

data class TrioHourlyPercentile(
    val hour: Int,
    val p10Mgdl: Double,
    val p25Mgdl: Double,
    val medianMgdl: Double,
    val p75Mgdl: Double,
    val p90Mgdl: Double
)

data class TrioStatsData(
    val readingCount: Int = 0,
    val availableDays: Double = 0.0,
    val averageMgdl: Double = 0.0,
    val medianMgdl: Double = 0.0,
    val standardDeviationMgdl: Double = 0.0,
    val coefficientOfVariation: Double = 0.0,
    val eA1cPercent: Double = 0.0,
    val gmiPercent: Double = 0.0,
    val tightRangePercent: Double = 0.0,
    val tir: TrioTirBreakdown = TrioTirBreakdown(),
    val hourlyPercentiles: List<TrioHourlyPercentile> = emptyList()
)

internal fun calculateTrioStatsData(
    readings: List<GV>,
    lowMgdl: Double,
    highMgdl: Double
): TrioStatsData {
    val valid = readings.filter { it.value >= MIN_VALID_BG }.sortedBy { it.timestamp }
    if (valid.isEmpty()) return TrioStatsData()

    val values = valid.map { it.value }
    val summary = calculateSummary(values, lowMgdl, highMgdl)

    return TrioStatsData(
        readingCount = valid.size,
        availableDays = calculateAvailableSampleDays(valid),
        averageMgdl = summary.average,
        medianMgdl = summary.median,
        standardDeviationMgdl = summary.standardDeviation,
        coefficientOfVariation = summary.cv,
        eA1cPercent = (summary.average + 46.7) / 28.7,
        gmiPercent = 3.31 + 0.02392 * summary.average,
        tightRangePercent = percentage(values.count { it in TIGHT_LOW_MGDL..TIGHT_HIGH_MGDL }, values.size),
        tir = summary.tir,
        hourlyPercentiles = calculateHourlyPercentiles(valid)
    )
}

private data class Summary(
    val average: Double,
    val median: Double,
    val standardDeviation: Double,
    val cv: Double,
    val tir: TrioTirBreakdown
)

private fun calculateSummary(values: List<Double>, lowMgdl: Double, highMgdl: Double): Summary {
    val sorted = values.sorted()
    val average = values.average()
    val standardDeviation = sqrt(values.sumOf { value -> (value - average) * (value - average) } / values.size)
    return Summary(
        average = average,
        median = percentile(sorted, 0.5),
        standardDeviation = standardDeviation,
        cv = if (average > 0.0) standardDeviation / average * 100.0 else 0.0,
        tir = calculateTir(values, lowMgdl, highMgdl)
    )
}

private fun calculateTir(values: List<Double>, lowMgdl: Double, highMgdl: Double): TrioTirBreakdown {
    if (values.isEmpty()) return TrioTirBreakdown()
    return TrioTirBreakdown(
        veryLow = percentage(values.count { it < VERY_LOW_MGDL }, values.size),
        low = percentage(values.count { it >= VERY_LOW_MGDL && it < lowMgdl }, values.size),
        inRange = percentage(values.count { it in lowMgdl..highMgdl }, values.size),
        high = percentage(values.count { it > highMgdl && it <= VERY_HIGH_MGDL }, values.size),
        veryHigh = percentage(values.count { it > VERY_HIGH_MGDL }, values.size)
    )
}

private fun calculateAvailableSampleDays(readings: List<GV>): Double {
    if (readings.isEmpty()) return 0.0
    val cadence = medianCadence(readings)
    val sampleDays = readings.size * cadence.toDouble() / DAY_MS
    val spanDays = if (readings.size < 2) {
        sampleDays
    } else {
        (readings.last().timestamp - readings.first().timestamp + cadence).coerceAtLeast(0L).toDouble() / DAY_MS
    }
    return minOf(sampleDays, spanDays)
}

private fun medianCadence(readings: List<GV>): Long {
    val gaps = readings.zipWithNext { first, second -> second.timestamp - first.timestamp }
        .filter { it in MIN_CADENCE_MS..MAX_CADENCE_MS }
        .sorted()
    return gaps.getOrNull(gaps.size / 2) ?: DEFAULT_CADENCE_MS
}

private fun calculateHourlyPercentiles(readings: List<GV>): List<TrioHourlyPercentile> {
    val zone = TimeZone.currentSystemDefault()
    return readings
        .groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).hour }
        .entries
        .sortedBy { it.key }
        .map { (hour, readingsForHour) ->
            val values = readingsForHour.map { it.value }.sorted()
            TrioHourlyPercentile(
                hour = hour,
                p10Mgdl = percentile(values, 0.10),
                p25Mgdl = percentile(values, 0.25),
                medianMgdl = percentile(values, 0.50),
                p75Mgdl = percentile(values, 0.75),
                p90Mgdl = percentile(values, 0.90)
            )
        }
}

private fun percentile(sorted: List<Double>, fraction: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val position = fraction.coerceIn(0.0, 1.0) * sorted.lastIndex
    val lower = position.toInt()
    val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
    val weight = position - lower
    return sorted[lower] + (sorted[upper] - sorted[lower]) * weight
}

private fun percentage(count: Int, total: Int): Double =
    if (total == 0) 0.0 else count * 100.0 / total

private const val MIN_VALID_BG = 39.0
private const val VERY_LOW_MGDL = 54.0
private const val VERY_HIGH_MGDL = 250.0
private const val TIGHT_LOW_MGDL = 70.0
private const val TIGHT_HIGH_MGDL = 140.0
private const val MINUTE_MS = 60_000L
private const val DEFAULT_CADENCE_MS = 5 * MINUTE_MS
private const val MIN_CADENCE_MS = MINUTE_MS
private const val MAX_CADENCE_MS = 30 * MINUTE_MS
private const val DAY_MS = 24L * 60L * 60L * 1000L
