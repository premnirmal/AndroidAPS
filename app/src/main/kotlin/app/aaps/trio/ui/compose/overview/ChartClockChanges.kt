package app.aaps.trio.ui.compose.overview

import java.time.Instant
import java.time.ZoneId

internal fun clockChangeTimestamps(startMillis: Long, endMillis: Long, zone: ZoneId): List<Long> {
    val rules = zone.rules
    val changes = mutableListOf<Long>()
    var transition = rules.nextTransition(Instant.ofEpochMilli(startMillis).minusNanos(1))
    while (transition != null && transition.instant.toEpochMilli() <= endMillis) {
        changes.add(transition.instant.toEpochMilli())
        transition = rules.nextTransition(transition.instant)
    }
    return changes
}
