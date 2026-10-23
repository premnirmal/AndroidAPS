package app.aaps.ui.compose.stats

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TDD
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TrioInsulinStatsDataTest {

    @Test
    fun `insulin data groups daily TDD and boluses by type`() {
        val day = MidnightTime.calc(1_700_000_000_000L)
        val data = calculateTrioInsulinStatsData(
            tdds = listOf(
                TDD(timestamp = day, basalAmount = 10.0, bolusAmount = 5.0, totalAmount = 15.0),
                TDD(timestamp = day + T.hours(4).msecs(), basalAmount = 1.0, bolusAmount = 2.0, totalAmount = 3.0)
            ),
            boluses = listOf(
                bolus(day + T.hours(1).msecs(), 2.0, BS.Type.NORMAL),
                bolus(day + T.hours(2).msecs(), 1.0, BS.Type.SMB),
                bolus(day + T.hours(3).msecs(), 3.0, BS.Type.PRIMING)
            ),
            range = TrioStatsRange.DAYS_7
        )

        assertThat(data.tddPoints).hasSize(1)
        assertThat(data.tddPoints.single().total).isEqualTo(18.0)
        assertThat(data.tddPoints.single().basal).isEqualTo(11.0)
        assertThat(data.tddPoints.single().bolus).isEqualTo(7.0)
        assertThat(data.bolusPoints).hasSize(1)
        assertThat(data.bolusPoints.single().manualBolus).isEqualTo(2.0)
        assertThat(data.bolusPoints.single().smbBolus).isEqualTo(1.0)
    }

    @Test
    fun `insulin day data keeps hourly buckets separate`() {
        val day = MidnightTime.calc(1_700_000_000_000L)
        val data = calculateTrioInsulinStatsData(
            tdds = listOf(
                TDD(timestamp = day + T.hours(1).msecs(), totalAmount = 1.0),
                TDD(timestamp = day + T.hours(2).msecs(), totalAmount = 2.0)
            ),
            boluses = emptyList(),
            range = TrioStatsRange.TODAY
        )

        assertThat(data.tddPoints).hasSize(2)
        assertThat(data.tddPoints.map(TrioInsulinPoint::total)).containsExactly(1.0, 2.0).inOrder()
    }

    private fun bolus(timestamp: Long, amount: Double, type: BS.Type) = BS(
        timestamp = timestamp,
        amount = amount,
        type = type,
        iCfg = ICfg("Rapid", 75, 6.0, 1.0)
    )
}
