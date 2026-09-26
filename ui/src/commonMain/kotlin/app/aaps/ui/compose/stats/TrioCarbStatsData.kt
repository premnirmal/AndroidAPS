package app.aaps.ui.compose.stats

import app.aaps.core.data.model.CA
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime

/**
 * One bar of the carbohydrate chart.
 *
 * The bucket is one hour for the short ranges and one day for the longer ones, the same split Trio
 * uses on iOS for its "Total Meals" chart.
 */
data class TrioCarbPoint(
    val timestamp: Long,
    val carbs: Double
)

/**
 * Carbohydrate data for the Meals section of the Trio statistics screen.
 *
 * Trio on iOS shows an average per day above the chart, and it only counts days that have at least
 * one entry, so a week with food on two days reads as the average of those two days and not as the
 * weekly total divided by seven. [averagePerDay] keeps that behaviour.
 */
data class TrioCarbStatsData(
    val points: List<TrioCarbPoint> = emptyList(),
    val averagePerDay: Double = 0.0,
    val total: Double = 0.0,
    val entryCount: Int = 0
)

/**
 * Turn carb records into the bars and the summary of the Meals section.
 *
 * Entries that are invalid or hold no carbs are dropped. [range] decides the bucket: one hour for
 * Today and the last 24 hours, one day for the longer ranges.
 */
internal fun calculateTrioCarbStatsData(
    carbs: List<CA>,
    range: TrioStatsRange
): TrioCarbStatsData {
    val valid = carbs.filter { it.isValid && it.amount > 0.0 }
    if (valid.isEmpty()) return TrioCarbStatsData()

    val points = valid
        .groupBy { it.timestamp.bucket(range) }
        .map { (timestamp, values) -> TrioCarbPoint(timestamp = timestamp, carbs = values.sumOf(CA::amount)) }
        .sortedBy(TrioCarbPoint::timestamp)

    val dailyTotals = valid
        .groupBy { MidnightTime.calc(it.timestamp) }
        .map { (_, values) -> values.sumOf(CA::amount) }

    return TrioCarbStatsData(
        points = points,
        averagePerDay = dailyTotals.average(),
        total = valid.sumOf(CA::amount),
        entryCount = valid.size
    )
}

private fun Long.bucket(range: TrioStatsRange): Long =
    if (range.usesHourlyBuckets) this - this % T.hours(1).msecs() else MidnightTime.calc(this)
