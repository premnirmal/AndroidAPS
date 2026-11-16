package app.aaps.ui.compose.overview.graphs

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GraphClockChangeTest {
    private val dst = Instant.parse("2020-10-25T01:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val london = TimeZone.of("Europe/London")

    @Test
    fun fixedOffsetZonesDoNotCreateMarkers() {
        assertEquals(emptyList(), graphClockChanges(dst - minute, dst + minute, TimeZone.UTC))
        assertEquals(emptyList(), graphClockChanges(dst - minute, dst + minute, TimeZone.of("Asia/Kolkata")))
    }

    @Test
    fun fallBackUsesTheExactTransition() {
        val result = graphClockChanges(dst - minute, dst + 10 * minute, london)
        assertEquals(dst, result.single().timestamp)
        assertEquals(-60, result.single().minutes)
    }

    @Test
    fun springForwardUsesTheExactTransition() {
        val transition = Instant.parse("2020-03-29T01:00:00Z").toEpochMilliseconds()
        val result = graphClockChanges(transition - minute, transition + 10 * minute, london)
        assertEquals(transition, result.single().timestamp)
        assertEquals(60, result.single().minutes)
    }

    @Test
    fun transitionAtEitherEdgeIsIncludedButOutsideIsIgnored() {
        assertEquals(listOf(dst), graphClockChanges(dst, dst + minute, london).map { it.timestamp })
        assertEquals(listOf(dst), graphClockChanges(dst - minute, dst, london).map { it.timestamp })
        assertEquals(emptyList(), graphClockChanges(dst + 1, dst + minute, london))
        assertEquals(emptyList(), graphClockChanges(dst - minute, dst - 1, london))
    }

    @Test
    fun emptyOrReversedWindowHasNoMarkers() {
        assertEquals(emptyList(), graphClockChanges(dst, dst, london))
        assertEquals(emptyList(), graphClockChanges(dst + minute, dst - minute, london))
    }
}
