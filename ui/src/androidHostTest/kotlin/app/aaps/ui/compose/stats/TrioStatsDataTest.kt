package app.aaps.ui.compose.stats

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
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
            previousReadings = emptyList(),
            startTime = 0L,
            endTime = 4_000L,
            lowMgdl = 72.0,
            highMgdl = 180.0
        )

        assertThat(data.tir.inRange).isWithin(0.01).of(66.67)
        assertThat(data.tir.high).isWithin(0.01).of(33.33)
        assertThat(data.tir.veryHigh).isEqualTo(0.0)
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
