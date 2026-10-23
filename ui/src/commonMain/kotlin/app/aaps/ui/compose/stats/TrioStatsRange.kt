package app.aaps.ui.compose.stats

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime

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

    val usesHourlyBuckets: Boolean
        get() = this == TODAY || this == HOURS_24
}
