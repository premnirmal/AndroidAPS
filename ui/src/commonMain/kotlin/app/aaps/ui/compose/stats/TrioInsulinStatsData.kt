package app.aaps.ui.compose.stats

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TDD
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime

enum class TrioStatsSection {
    GLUCOSE,
    INSULIN
}

enum class TrioInsulinChart {
    TOTAL_DAILY_DOSE,
    BOLUS_DISTRIBUTION
}

enum class TrioInsulinRange {
    DAY,
    WEEK,
    MONTH,
    THREE_MONTHS;

    fun startTime(now: Long): Long = when (this) {
        DAY          -> MidnightTime.calc(now)
        WEEK         -> now - T.days(7).msecs()
        MONTH        -> now - T.days(30).msecs()
        THREE_MONTHS -> now - T.days(90).msecs()
    }

    val usesHourlyBuckets: Boolean
        get() = this == DAY
}

data class TrioInsulinPoint(
    val timestamp: Long,
    val total: Double,
    val basal: Double = 0.0,
    val bolus: Double = 0.0,
    val manualBolus: Double = 0.0,
    val smbBolus: Double = 0.0
)

data class TrioInsulinStatsData(
    val tddPoints: List<TrioInsulinPoint> = emptyList(),
    val bolusPoints: List<TrioInsulinPoint> = emptyList()
) {
    val averageTdd: Double
        get() = tddPoints.map(TrioInsulinPoint::total).averageOrZero()
    val totalTdd: Double
        get() = tddPoints.sumOf(TrioInsulinPoint::total)
    val averageManualBolus: Double
        get() = bolusPoints.map(TrioInsulinPoint::manualBolus).averageOrZero()
    val averageSmbBolus: Double
        get() = bolusPoints.map(TrioInsulinPoint::smbBolus).averageOrZero()
    val totalBolus: Double
        get() = bolusPoints.sumOf { it.manualBolus + it.smbBolus }
}

internal fun calculateTrioInsulinStatsData(
    tdds: List<TDD>,
    boluses: List<BS>,
    range: TrioInsulinRange
): TrioInsulinStatsData {
    val tddPoints = tdds
        .groupBy { it.timestamp.bucket(range) }
        .map { (timestamp, values) ->
            TrioInsulinPoint(
                timestamp = timestamp,
                total = values.sumOf { it.totalAmount },
                basal = values.sumOf { it.basalAmount },
                bolus = values.sumOf { it.bolusAmount }
            )
        }
        .sortedBy(TrioInsulinPoint::timestamp)
    val bolusPoints = boluses
        .filter { it.type != BS.Type.PRIMING }
        .groupBy { it.timestamp.bucket(range) }
        .map { (timestamp, values) ->
            TrioInsulinPoint(
                timestamp = timestamp,
                total = values.sumOf(BS::amount),
                manualBolus = values.filter { it.type == BS.Type.NORMAL }.sumOf(BS::amount),
                smbBolus = values.filter { it.type == BS.Type.SMB }.sumOf(BS::amount)
            )
        }
        .sortedBy(TrioInsulinPoint::timestamp)
    return TrioInsulinStatsData(tddPoints = tddPoints, bolusPoints = bolusPoints)
}

private fun Long.bucket(range: TrioInsulinRange): Long =
    if (range.usesHourlyBuckets) this - this % T.hours(1).msecs() else MidnightTime.calc(this)

private fun List<Double>.averageOrZero(): Double = ifEmpty { listOf(0.0) }.average()
