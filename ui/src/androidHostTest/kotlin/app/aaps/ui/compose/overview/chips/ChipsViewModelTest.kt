package app.aaps.ui.compose.overview.chips

import androidx.lifecycle.viewModelScope
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.UiStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChipsViewModelTest {

    @Mock private lateinit var cache: OverviewDataCache
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var config: Config
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var sensitivityOverview: SensitivityOverview
    @Mock private lateinit var bolusProgressData: BolusProgressData
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: ChipsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // The stateIn/shareIn coroutines are deferred by StandardTestDispatcher, but the cold combine chains
        // are built at construction, so the cache graph flows they reference must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(cache.iobGraphFlow).thenReturn(MutableStateFlow(IobGraphData(emptyList(), emptyList())))
        whenever(cache.cobGraphFlow).thenReturn(MutableStateFlow(CobGraphData(emptyList(), emptyList())))
        whenever(bolusProgressData.state).thenReturn(MutableStateFlow<BolusProgressState?>(null))
        whenever(rxBus.toFlow(EventAutosensCalculationFinished::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventLoopUpdateGui::class)).thenReturn(emptyFlow())
        whenever(cache.predictionsFlow).thenReturn(MutableStateFlow(emptyList()))
        sut = ChipsViewModel(cache, iobCobCalculator, loop, config, persistenceLayer, sensitivityOverview, bolusProgressData, rh, decimalFormatter, rxBus)
    }

    @AfterEach
    fun tearDown() {
        sut.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `default uiState exposes empty initial values`() {
        assertThat(sut.iobUiState.value.iobTotal).isEqualTo(0.0)
        assertThat(sut.iobUiState.value.text).isEmpty()
        assertThat(sut.cobUiState.value.text).isEmpty()
        assertThat(sut.cobUiState.value.carbsReq).isEqualTo(0)
        assertThat(sut.sensitivityUiState.value.ratio).isEqualTo(1.0)
        assertThat(sut.sensitivityUiState.value.isEnabled).isTrue()
        assertThat(sut.sensitivityUiState.value.hasData).isFalse()
    }

    @Test
    fun `COB keeps the plain value separate from the required carbs message`() = runTest {
        try {
            val result = mock<APSResult>()
            whenever(result.carbsReq).thenReturn(15)
            whenever(loop.lastRun).thenReturn(
                Loop.LastRun().apply {
                    constraintsProcessed = result
                    lastAPSRun = 1_000L
                }
            )
            whenever(config.APS).thenReturn(true)
            whenever(iobCobCalculator.getCobInfo("ChipsViewModel COB")).thenReturn(CobInfo(1_000L, 25.0, 0.0))
            whenever(persistenceLayer.getNewestCarbs()).thenReturn(null)
            whenever(rh.gs(InterfacesStrings.format_carbs, 25)).thenReturn("25 g")
            whenever(rh.gs(InterfacesStrings.format_carbs, 15)).thenReturn("15 g")
            whenever(rh.gs(UiStrings.cob_with_carbs_required, "25 g", "15 g")).thenReturn("25 g (15 g required)")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.cobUiState.collect {} }

            runCurrent()

            assertThat(sut.cobUiState.value.baseText).isEqualTo("25 g")
            assertThat(sut.cobUiState.value.text).isEqualTo("25 g (15 g required)")
            assertThat(sut.cobUiState.value.carbsReq).isEqualTo(15)
            assertThat(sut.cobUiState.value.cobValue).isEqualTo(25.0)
        } finally {
            sut.viewModelScope.cancel()
        }
    }
}
