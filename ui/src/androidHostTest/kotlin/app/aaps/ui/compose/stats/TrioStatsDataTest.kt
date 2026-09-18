package app.aaps.ui.compose.stats

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TrioStatsDataTest {

    @Test
    fun `mixed readings are assigned to their correct glycemic bands`() {
        val readings = listOf(
            glucose(timestamp = 1_000L, value = 90.0),
            glucose(timestamp = 2_000L, value = 120.0),
            glucose(timestamp = 3_000L, value = 200.0)
        )

        val data = calculateTrioStatsData(
            readings = readings,
            lowMgdl = 72.0,
            highMgdl = 180.0
        )

        assertThat(data.tir.inRange).isWithin(0.01).of(66.67)
        assertThat(data.tir.high).isWithin(0.01).of(33.33)
        assertThat(data.tir.veryHigh).isEqualTo(0.0)
    }

    @Test
    fun `hourly glucose percentiles are calculated for the AGP graph`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val readings = listOf(40.0, 60.0, 80.0, 100.0, 120.0).mapIndexed { index, value ->
            glucose(
                timestamp = start + T.hours(6).msecs() + T.mins(index * 5L).msecs(),
                value = value
            )
        }

        val data = calculateTrioStatsData(
            readings = readings,
            lowMgdl = 70.0,
            highMgdl = 180.0
        )

        assertThat(data.hourlyPercentiles).hasSize(1)
        with(data.hourlyPercentiles.single()) {
            assertThat(hour).isEqualTo(6)
            assertThat(p10Mgdl).isEqualTo(48.0)
            assertThat(p25Mgdl).isEqualTo(60.0)
            assertThat(medianMgdl).isEqualTo(80.0)
            assertThat(p75Mgdl).isEqualTo(100.0)
            assertThat(p90Mgdl).isEqualTo(112.0)
        }
    }

    @Test
    fun `available days uses filtered reading cadence`() {
        val start = MidnightTime.calc(1_700_000_000_000L)
        val readings = List(144) { index ->
            glucose(
                timestamp = start + T.mins(index * 5L).msecs(),
                value = 100.0
            )
        }

        val data = calculateTrioStatsData(
            readings = readings,
            lowMgdl = 70.0,
            highMgdl = 180.0
        )

        assertThat(data.availableDays).isWithin(0.01).of(0.5)
    }

    private fun glucose(timestamp: Long, value: Double) = GV(
        timestamp = timestamp,
        raw = null,
        value = value,
        trendArrow = TrendArrow.NONE,
        noise = null,
        sourceSensor = SourceSensor.UNKNOWN
    )
}
