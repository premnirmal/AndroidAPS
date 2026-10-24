package app.aaps.ui.compose.overview.chips

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.ui.UiStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewChipDataTest {

    @Test
    fun `chip refresh includes bolus calculation loop and timer changes`() = runTest {
        val bolusState = MutableStateFlow<BolusProgressState?>(null)
        val calculations = MutableSharedFlow<EventAutosensCalculationFinished>()
        val loopUpdates = MutableSharedFlow<EventLoopUpdateGui>()
        val bolusProgressData = mock<BolusProgressData>()
        val rxBus = mock<RxBus>()
        whenever(bolusProgressData.state).thenReturn(bolusState)
        whenever(rxBus.toFlow(EventAutosensCalculationFinished::class)).thenReturn(calculations)
        whenever(rxBus.toFlow(EventLoopUpdateGui::class)).thenReturn(loopUpdates)
        var refreshes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            overviewChipRefresh(bolusProgressData, rxBus).collect { refreshes++ }
        }
        runCurrent()
        assertThat(refreshes).isEqualTo(2)

        bolusState.value = mock()
        runCurrent()
        assertThat(refreshes).isEqualTo(3)

        calculations.emit(EventAutosensCalculationFinished(triggeredByNewBG = true))
        runCurrent()
        assertThat(refreshes).isEqualTo(4)

        loopUpdates.emit(EventLoopUpdateGui())
        runCurrent()
        assertThat(refreshes).isEqualTo(5)

        advanceTimeBy(149_999L)
        runCurrent()
        assertThat(refreshes).isEqualTo(5)
        advanceTimeBy(1L)
        runCurrent()
        assertThat(refreshes).isEqualTo(6)
    }

    @Test
    fun `required carbs uses translated templates and a value with units`() {
        val rh = mock<TextResolver>()
        whenever(rh.gs(InterfacesStrings.format_carbs, 15)).thenReturn("15 g")
        whenever(rh.gs(UiStrings.cob_with_carbs_required, "25 g", "15 g")).thenReturn("25 g (15 g required)")

        assertThat(rh.cobWithRequiredCarbs("25 g", 15)).isEqualTo("25 g (15 g required)")
    }
}
