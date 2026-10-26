package app.aaps.trio.ui.compose.overview

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.ui.compose.overview.graphs.graphClockChanges
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import java.time.Instant

class ChartClockChangesTest {

    private val london = TimeZone.of("Europe/London")

    @Test
    fun `marks the repeated hour when clocks move back`() {
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z"))
            .containsExactly(millis("2026-10-25T01:00:00Z"))
    }

    @Test
    fun `marks the missing hour when clocks move forward`() {
        assertThat(changes("2026-03-29T00:00:00Z", "2026-03-29T04:00:00Z"))
            .containsExactly(millis("2026-03-29T01:00:00Z"))
    }

    @Test
    fun `marks half hour changes at their exact instant`() {
        assertThat(changes("2026-04-04T14:00:00Z", "2026-04-04T16:00:00Z", TimeZone.of("Australia/Lord_Howe")))
            .containsExactly(millis("2026-04-04T15:00:00Z"))
    }

    @Test
    fun `includes a change at either viewport edge`() {
        val transition = "2026-10-25T01:00:00Z"
        assertThat(changes(transition, "2026-10-25T02:00:00Z")).containsExactly(millis(transition))
        assertThat(changes("2026-10-25T00:00:00Z", transition)).containsExactly(millis(transition))
    }

    @Test
    fun `does not mark changes outside the viewport`() {
        assertThat(changes("2026-10-25T01:00:00.001Z", "2026-10-25T04:00:00Z")).isEmpty()
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T00:59:59.999Z")).isEmpty()
    }

    @Test
    fun `does not mark an ordinary day or a fixed offset zone`() {
        assertThat(changes("2026-10-24T00:00:00Z", "2026-10-24T06:00:00Z")).isEmpty()
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", TimeZone.UTC)).isEmpty()
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", TimeZone.of("Asia/Kolkata"))).isEmpty()
    }

    @Test
    fun `includes each change once in time order`() {
        assertThat(changes("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z"))
            .containsExactly(millis("2026-03-29T01:00:00Z"), millis("2026-10-25T01:00:00Z"))
            .inOrder()
    }

    @Test
    fun `marks stored time zone changes even when the earlier reading is outside the viewport`() {
        val readings = listOf(
            reading("2026-10-24T23:55:00Z", 60),
            reading("2026-10-25T00:05:00Z", -240)
        )
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", TimeZone.UTC, readings))
            .containsExactly(millis("2026-10-25T00:05:00Z"))
    }

    @Test
    fun `stored daylight saving changes do not add a second marker`() {
        val readings = listOf(
            reading("2026-10-25T00:55:00Z", 60),
            reading("2026-10-25T01:05:00Z", 0)
        )
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", london, readings))
            .containsExactly(millis("2026-10-25T01:00:00Z"))
    }

    private fun reading(time: String, offsetMinutes: Int) =
        BgDataPoint(millis(time), 100.0, BgRange.IN_RANGE, BgType.REGULAR, utcOffset = offsetMinutes * 60_000L)

    private fun changes(start: String, end: String, zone: TimeZone = london, readings: List<BgDataPoint> = emptyList()): List<Long> =
        graphClockChanges(millis(start), millis(end), zone, readings).map { it.timestamp }

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()
}
