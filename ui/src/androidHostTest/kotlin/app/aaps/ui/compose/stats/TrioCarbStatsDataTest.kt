package app.aaps.ui.compose.stats

import app.aaps.core.data.model.CA
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TrioCarbStatsDataTest {

    @Test
    fun `carb entries are bucketed by hour for short ranges`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val carbs = listOf(
            carb(timestamp = start + T.hours(8).msecs(), amount = 20.0),
            carb(timestamp = start + T.hours(8).msecs() + T.mins(20).msecs(), amount = 10.0),
            carb(timestamp = start + T.hours(12).msecs(), amount = 45.0)
        )

        val data = calculateTrioCarbStatsData(carbs, TrioStatsRange.TODAY)

        assertThat(data.points).hasSize(2)
        assertThat(data.points.first().carbs).isEqualTo(30.0)
        assertThat(data.points.last().carbs).isEqualTo(45.0)
        assertThat(data.entryCount).isEqualTo(3)
    }

    @Test
    fun `carb entries are bucketed by day for long ranges`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val carbs = listOf(
            carb(timestamp = start + T.hours(8).msecs(), amount = 20.0),
            carb(timestamp = start + T.hours(18).msecs(), amount = 30.0),
            carb(timestamp = start + T.days(1).msecs() + T.hours(9).msecs(), amount = 60.0)
        )

        val data = calculateTrioCarbStatsData(carbs, TrioStatsRange.DAYS_7)

        assertThat(data.points).hasSize(2)
        assertThat(data.points.first().carbs).isEqualTo(50.0)
        assertThat(data.points.last().carbs).isEqualTo(60.0)
    }

    @Test
    fun `the average counts only days that have carb entries`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val carbs = listOf(
            carb(timestamp = start + T.hours(8).msecs(), amount = 40.0),
            carb(timestamp = start + T.days(3).msecs() + T.hours(8).msecs(), amount = 60.0)
        )

        val data = calculateTrioCarbStatsData(carbs, TrioStatsRange.DAYS_7)

        assertThat(data.total).isEqualTo(100.0)
        assertThat(data.averagePerDay).isEqualTo(50.0)
    }

    @Test
    fun `invalid and empty entries are left out`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val carbs = listOf(
            carb(timestamp = start + T.hours(8).msecs(), amount = 40.0),
            carb(timestamp = start + T.hours(9).msecs(), amount = 20.0, isValid = false),
            carb(timestamp = start + T.hours(10).msecs(), amount = 0.0)
        )

        val data = calculateTrioCarbStatsData(carbs, TrioStatsRange.TODAY)

        assertThat(data.entryCount).isEqualTo(1)
        assertThat(data.total).isEqualTo(40.0)
    }

    @Test
    fun `no carb entries give empty data`() {
        val data = calculateTrioCarbStatsData(emptyList(), TrioStatsRange.DAYS_30)

        assertThat(data.points).isEmpty()
        assertThat(data.total).isEqualTo(0.0)
        assertThat(data.averagePerDay).isEqualTo(0.0)
    }

    private fun carb(timestamp: Long, amount: Double, isValid: Boolean = true) = CA(
        timestamp = timestamp,
        duration = 0L,
        amount = amount,
        isValid = isValid
    )
}
