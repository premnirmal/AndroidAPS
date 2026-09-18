package app.aaps.ui.compose.stats

import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class TrioStatsRange {
    TODAY,
    HOURS_12,
    DAYS_7,
    DAYS_14,
    DAYS_30,
    DAYS_90,
    ALL;

    fun startTime(now: Long): Long = when (this) {
        TODAY   -> MidnightTime.calc(now)
        HOURS_12 -> now - T.hours(12).msecs()
        DAYS_7  -> now - T.days(7).msecs()
        DAYS_14 -> now - T.days(14).msecs()
        DAYS_30 -> now - T.days(30).msecs()
        DAYS_90 -> now - T.days(90).msecs()
        ALL     -> 0L
    }
}

data class TrioTirBreakdown(
    val veryLow: Double = 0.0,
    val low: Double = 0.0,
    val inRange: Double = 0.0,
    val high: Double = 0.0,
    val veryHigh: Double = 0.0
)

data class TrioPatternRow(
    val key: String,
    val label: String,
    val averageMgdl: Double,
    val tir: TrioTirBreakdown,
    val readingCount: Int,
    val timestamp: Long = 0L,
    val dayOfWeek: DayOfWeek? = null
)

data class TrioStatsComparison(
    val tirDelta: Double,
    val averageDeltaMgdl: Double,
    val cvDelta: Double
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
    val coveragePercent: Double = 0.0,
    val averageMgdl: Double = 0.0,
    val medianMgdl: Double = 0.0,
    val p25Mgdl: Double = 0.0,
    val p75Mgdl: Double = 0.0,
    val standardDeviationMgdl: Double = 0.0,
    val coefficientOfVariation: Double = 0.0,
    val gmiPercent: Double = 0.0,
    val minimumMgdl: Double = 0.0,
    val maximumMgdl: Double = 0.0,
    val tightRangePercent: Double = 0.0,
    val gvi: Double = 0.0,
    val psgTrendPercent: Double = 0.0,
    val psgConfidencePercent: Double = 0.0,
    val mageMgdl: Double = 0.0,
    val moddMgdl: Double = 0.0,
    val dawnRiseMgdl: Double = 0.0,
    val bestStreakDays: Int = 0,
    val tir: TrioTirBreakdown = TrioTirBreakdown(),
    val hourlyPercentiles: List<TrioHourlyPercentile> = emptyList(),
    val hourly: List<TrioPatternRow> = emptyList(),
    val daily: List<TrioPatternRow> = emptyList(),
    val weekdays: List<TrioPatternRow> = emptyList(),
    val comparison: TrioStatsComparison? = null
)

internal fun calculateTrioStatsData(
    readings: List<GV>,
    previousReadings: List<GV>,
    startTime: Long,
    endTime: Long,
    lowMgdl: Double,
    highMgdl: Double
): TrioStatsData {
    val valid = readings.filter { it.value >= MIN_VALID_BG }.sortedBy { it.timestamp }
    if (valid.isEmpty()) return TrioStatsData()

    val values = valid.map { it.value }
    val summary = calculateSummary(values, lowMgdl, highMgdl)
    val previous = previousReadings.filter { it.value >= MIN_VALID_BG }.map { it.value }
        .takeIf { it.isNotEmpty() }
        ?.let { calculateSummary(it, lowMgdl, highMgdl) }
    val effectiveStart = maxOf(startTime, valid.first().timestamp)
    val daily = calculateDaily(valid, lowMgdl, highMgdl)

    return TrioStatsData(
        readingCount = valid.size,
        availableDays = calculateAvailableDays(valid),
        coveragePercent = calculateCoverage(valid, effectiveStart, endTime),
        averageMgdl = summary.average,
        medianMgdl = summary.median,
        p25Mgdl = summary.p25,
        p75Mgdl = summary.p75,
        standardDeviationMgdl = summary.standardDeviation,
        coefficientOfVariation = summary.cv,
        gmiPercent = 3.31 + 0.02392 * summary.average,
        minimumMgdl = values.min(),
        maximumMgdl = values.max(),
        tightRangePercent = percentage(values.count { it in TIGHT_LOW_MGDL..TIGHT_HIGH_MGDL }, values.size),
        gvi = calculateGvi(valid, summary.average, summary.standardDeviation),
        psgTrendPercent = calculatePsgTrend(valid),
        psgConfidencePercent = (
            valid.size.coerceAtMost(MAX_PSG_CONFIDENCE_SAMPLES) * 100.0 / MAX_PSG_CONFIDENCE_SAMPLES *
                (100.0 - summary.cv).coerceIn(0.0, 100.0) / 100.0
            ).coerceIn(0.0, 100.0),
        mageMgdl = calculateMage(valid),
        moddMgdl = calculateModd(valid),
        dawnRiseMgdl = calculateDawnRise(valid),
        bestStreakDays = calculateBestStreak(daily),
        tir = summary.tir,
        hourlyPercentiles = calculateHourlyPercentiles(valid),
        hourly = calculateHourly(valid, lowMgdl, highMgdl),
        daily = daily,
        weekdays = calculateWeekdays(valid, lowMgdl, highMgdl),
        comparison = previous?.let {
            TrioStatsComparison(
                tirDelta = summary.tir.inRange - it.tir.inRange,
                averageDeltaMgdl = summary.average - it.average,
                cvDelta = summary.cv - it.cv
            )
        }
    )
}

private data class Summary(
    val average: Double,
    val median: Double,
    val p25: Double,
    val p75: Double,
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
        p25 = percentile(sorted, 0.25),
        p75 = percentile(sorted, 0.75),
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

private fun calculateCoverage(readings: List<GV>, startTime: Long, endTime: Long): Double {
    if (readings.size < 2 || endTime <= startTime) return 0.0
    val cadence = medianCadence(readings)
    val expected = ((endTime - startTime) / cadence + 1L).coerceAtLeast(1L)
    return (readings.size * 100.0 / expected).coerceIn(0.0, 100.0)
}

private fun calculateAvailableDays(readings: List<GV>): Double =
    if (readings.isEmpty()) 0.0 else readings.size * medianCadence(readings).toDouble() / DAY_MS

private fun medianCadence(readings: List<GV>): Long {
    val gaps = readings.zipWithNext { first, second -> second.timestamp - first.timestamp }
        .filter { it in MIN_CADENCE_MS..MAX_CADENCE_MS }
        .sorted()
    return gaps.getOrNull(gaps.size / 2) ?: DEFAULT_CADENCE_MS
}

private fun calculateGvi(readings: List<GV>, average: Double, standardDeviation: Double): Double {
    if (readings.size < 2 || average <= 0.0) return 0.0
    var totalDelta = 0.0
    var rateOfChange = 0.0
    var rateSamples = 0
    readings.zipWithNext { first, second ->
        val delta = abs(second.value - first.value)
        val elapsedMinutes = (second.timestamp - first.timestamp).toDouble() / MINUTE_MS
        totalDelta += delta
        if (elapsedMinutes in 0.1..30.0) {
            rateOfChange += delta / elapsedMinutes
            rateSamples++
        }
    }
    val meanDelta = totalDelta / readings.lastIndex
    val cvFactor = standardDeviation / average
    val normalizedDelta = (meanDelta / average).coerceIn(0.0, 1.2)
    val normalizedRate = ((if (rateSamples > 0) rateOfChange / rateSamples else 0.0) / 3.5).coerceIn(0.0, 1.0)
    return (1.0 + cvFactor * 1.1 + normalizedDelta * 0.9 + normalizedRate * 0.6).coerceIn(0.8, 3.0)
}

private fun calculatePsgTrend(readings: List<GV>): Double {
    if (readings.size < 2) return 0.0
    val half = readings.size / 2
    val firstAverage = readings.take(half).map { it.value }.average()
    val secondAverage = readings.drop(half).map { it.value }.average()
    return if (secondAverage > 0.0) ((firstAverage - secondAverage) / secondAverage * 100.0).coerceIn(-100.0, 100.0) else 0.0
}

private fun calculateMage(readings: List<GV>): Double {
    if (readings.size < 5) return 0.0
    val values = readings.map { it.value }
    val average = values.average()
    val standardDeviation = sqrt(values.sumOf { (it - average) * (it - average) } / values.size)
    if (standardDeviation <= 0.0) return 0.0
    val extrema = mutableListOf(values.first())
    for (index in 1 until values.lastIndex) {
        val previous = values[index - 1]
        val current = values[index]
        val next = values[index + 1]
        if ((current > previous && current >= next) || (current < previous && current <= next)) extrema += current
    }
    extrema += values.last()
    val amplitudes = extrema.zipWithNext { first, second -> abs(second - first) }
        .filter { it > standardDeviation }
    return amplitudes.takeIf { it.isNotEmpty() }?.average() ?: 0.0
}

private fun calculateModd(readings: List<GV>): Double {
    if (readings.size < 2) return 0.0
    var candidateIndex = 0
    var total = 0.0
    var count = 0
    readings.forEach { reading ->
        val target = reading.timestamp - DAY_MS
        while (candidateIndex < readings.lastIndex && readings[candidateIndex + 1].timestamp <= target) {
            candidateIndex++
        }
        val candidate = readings[candidateIndex]
        if (abs(candidate.timestamp - target) <= DEFAULT_CADENCE_MS) {
            total += abs(reading.value - candidate.value)
            count++
        }
    }
    return if (count > 0) total / count else 0.0
}

private fun calculateDawnRise(readings: List<GV>): Double {
    val zone = TimeZone.currentSystemDefault()
    val rises = readings
        .groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).date }
        .mapNotNull { (_, dayReadings) ->
            val nadir = dayReadings
                .filter { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).hour in 0..5 }
                .minByOrNull { it.value } ?: return@mapNotNull null
            val peak = dayReadings
                .filter {
                    Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).hour in 4..9 &&
                        it.timestamp > nadir.timestamp
                }
                .maxByOrNull { it.value } ?: return@mapNotNull null
            (peak.value - nadir.value).takeIf { it > 0.0 }
        }
        .sorted()
    return rises.getOrNull(rises.size / 2) ?: 0.0
}

private fun calculateBestStreak(days: List<TrioPatternRow>): Int {
    var best = 0
    var current = 0
    var previousTimestamp: Long? = null
    days.forEach { day ->
        val consecutive = previousTimestamp?.let { day.timestamp - it in 20L * 60L * 60L * 1000L..28L * 60L * 60L * 1000L } == true
        current = if (day.tir.inRange >= 70.0) {
            if (consecutive) current + 1 else 1
        } else {
            0
        }
        best = maxOf(best, current)
        previousTimestamp = day.timestamp
    }
    return best
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

private fun calculateHourly(readings: List<GV>, lowMgdl: Double, highMgdl: Double): List<TrioPatternRow> {
    val zone = TimeZone.currentSystemDefault()
    return readings.groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).hour }
        .entries
        .sortedBy { it.key }
        .map { (hour, values) ->
            patternRow(
                key = hour.toString(),
                label = hour.toString().padStart(2, '0') + ":00",
                values = values,
                lowMgdl = lowMgdl,
                highMgdl = highMgdl
            )
        }
}

private fun calculateDaily(readings: List<GV>, lowMgdl: Double, highMgdl: Double): List<TrioPatternRow> {
    val zone = TimeZone.currentSystemDefault()
    return readings.groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).date }
        .entries
        .sortedBy { it.key }
        .map { (date, values) ->
            patternRow(
                key = date.toString(),
                label = date.toString(),
                values = values,
                lowMgdl = lowMgdl,
                highMgdl = highMgdl,
                timestamp = MidnightTime.calc(values.first().timestamp)
            )
        }
}

private fun calculateWeekdays(readings: List<GV>, lowMgdl: Double, highMgdl: Double): List<TrioPatternRow> {
    val zone = TimeZone.currentSystemDefault()
    return readings.groupBy { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(zone).dayOfWeek }
        .entries
        .sortedBy { it.key.ordinal }
        .map { (day, values) ->
            patternRow(
                key = day.name,
                label = day.name,
                values = values,
                lowMgdl = lowMgdl,
                highMgdl = highMgdl,
                dayOfWeek = day
            )
        }
}

private fun patternRow(
    key: String,
    label: String,
    values: List<GV>,
    lowMgdl: Double,
    highMgdl: Double,
    timestamp: Long = 0L,
    dayOfWeek: DayOfWeek? = null
) = TrioPatternRow(
    key = key,
    label = label,
    averageMgdl = values.map { it.value }.average(),
    tir = calculateTir(values.map { it.value }, lowMgdl, highMgdl),
    readingCount = values.size,
    timestamp = timestamp,
    dayOfWeek = dayOfWeek
)

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
private const val MAX_PSG_CONFIDENCE_SAMPLES = 288
