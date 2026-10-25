package app.aaps.trio.ui.compose.overview

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ChartClockChangesTest {

    private val london = ZoneId.of("Europe/London")

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
        assertThat(changes("2026-04-04T14:00:00Z", "2026-04-04T16:00:00Z", ZoneId.of("Australia/Lord_Howe")))
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
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", ZoneId.of("UTC"))).isEmpty()
        assertThat(changes("2026-10-25T00:00:00Z", "2026-10-25T04:00:00Z", ZoneId.of("Asia/Kolkata"))).isEmpty()
    }

    @Test
    fun `includes each change once in time order`() {
        assertThat(changes("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z"))
            .containsExactly(millis("2026-03-29T01:00:00Z"), millis("2026-10-25T01:00:00Z"))
            .inOrder()
    }

    private fun changes(start: String, end: String, zone: ZoneId = london): List<Long> =
        clockChangeTimestamps(millis(start), millis(end), zone)

    private fun millis(value: String): Long = Instant.parse(value).toEpochMilli()
}
