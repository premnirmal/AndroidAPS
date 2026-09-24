package app.aaps.trio.ui.compose.overview

import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TrioOverviewGraphTest {

    @Test
    fun `bolus value is shown only above half a unit`() {
        assertThat(shouldShowBolusValue(0.5)).isFalse()
        assertThat(shouldShowBolusValue(0.5001)).isTrue()
    }

    @Test
    fun `bolus marker uses its time and nearest glucose height`() {
        val bolus = bolus(timestamp = 800L)
        val markers = calculateBolusMarkerPositions(
            boluses = listOf(bolus),
            history = listOf(
                glucose(timestamp = 0L, value = 100.0),
                glucose(timestamp = 1_000L, value = 200.0)
            ),
            viewportStart = 0L,
            viewportDuration = 1_000L,
            width = 1_000f,
            plotHeight = 500f,
            yRange = TrioGraphRange(min = 0.0, max = 250.0),
            markerOffset = 10f
        )

        assertThat(markers).hasSize(1)
        assertThat(markers.single().x).isWithin(0.001f).of(800f)
        assertThat(markers.single().y).isWithin(0.001f).of(90f)
    }

    @Test
    fun `invalid and zero boluses have no marker`() {
        val markers = calculateBolusMarkerPositions(
            boluses = listOf(
                bolus(timestamp = 500L, amount = 0.0),
                bolus(timestamp = 600L, isValid = false)
            ),
            history = listOf(glucose(timestamp = 500L, value = 100.0)),
            viewportStart = 0L,
            viewportDuration = 1_000L,
            width = 1_000f,
            plotHeight = 500f,
            yRange = TrioGraphRange(min = 0.0, max = 250.0),
            markerOffset = 10f
        )

        assertThat(markers).isEmpty()
    }

    @Test
    fun `tap selects the closest bolus marker within the hit area`() {
        val first = bolus(timestamp = 100L)
        val second = bolus(timestamp = 200L)
        val selected = findTappedBolus(
            markers = listOf(
                BolusMarkerPosition(first, x = 100f, y = 100f),
                BolusMarkerPosition(second, x = 120f, y = 100f)
            ),
            tapX = 116f,
            tapY = 102f,
            hitRadius = 20f
        )

        assertThat(selected).isEqualTo(second)
    }

    private fun bolus(
        timestamp: Long,
        amount: Double = 1.0,
        isValid: Boolean = true
    ) = BolusGraphPoint(
        timestamp = timestamp,
        amount = amount,
        bolusType = BolusType.NORMAL,
        isValid = isValid,
        label = amount.toString()
    )

    private fun glucose(timestamp: Long, value: Double) = BgDataPoint(
        timestamp = timestamp,
        value = value,
        type = BgType.REGULAR,
        range = BgRange.IN_RANGE
    )
}
