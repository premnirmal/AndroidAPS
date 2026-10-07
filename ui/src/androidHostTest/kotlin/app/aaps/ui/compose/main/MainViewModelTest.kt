package app.aaps.ui.compose.main

import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.ui.UrlOpener
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RunningModeDisplayData
import app.aaps.core.interfaces.overview.graph.TbrDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import com.google.common.truth.Truth.assertThat
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainViewModelTest {

    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var pump: PumpWithConcentration
    @Mock private lateinit var bgSourcePlugin: PluginBase
    @Mock private lateinit var config: Config
    @Mock private lateinit var urlOpener: UrlOpener
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var fabricPrivacy: FabricPrivacy
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var overviewDataCache: OverviewDataCache
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var quickLaunchResolver: QuickLaunchResolver
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var loop: Loop
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var protectionCheck: ProtectionCheck
    @Mock private lateinit var sceneActions: SceneActions
    @Mock private lateinit var sceneChainTargetResolver: SceneChainResolver
    @Mock private lateinit var activeSceneManager: ActiveSceneSync
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var visibilityContext: VisibilityContext

    private lateinit var sut: MainViewModel
    private val loopRunning = MutableStateFlow(false)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers every init { ... launchIn(viewModelScope) } collector and the
        // WhileSubscribed/Eagerly stateIn launches, so construction only touches the flow GETTERS below —
        // all of which are StateFlow-typed and must be stubbed non-null or construction NPEs.
        Dispatchers.setMain(StandardTestDispatcher())

        // Reachability / pairing signals read as fields at construction.
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(true))

        // Cache flows folded into chipStateFlow's combine (getters evaluated eagerly as combine args).
        whenever(overviewDataCache.calcProgressFlow).thenReturn(MutableStateFlow(0))
        whenever(overviewDataCache.tempTargetFlow).thenReturn(MutableStateFlow<TempTargetDisplayData?>(null))
        whenever(overviewDataCache.profileFlow).thenReturn(MutableStateFlow<ProfileDisplayData?>(null))
        whenever(overviewDataCache.runningModeFlow).thenReturn(MutableStateFlow<RunningModeDisplayData?>(null))
        whenever(overviewDataCache.tbrFlow).thenReturn(MutableStateFlow<TbrDisplayData?>(null))
        whenever(quickWizard.changes).thenReturn(MutableStateFlow(0))
        whenever(loop.isRunning).thenReturn(loopRunning)
        whenever(activePlugin.activePump).thenReturn(pump)

        // Active scene state read as fields (activeSceneState + sceneExpired.map).
        whenever(activeSceneManager.activeSceneState).thenReturn(MutableStateFlow<ActiveSceneState?>(null))

        // Preference observers created (launchIn deferred) in init.
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(true))
        whenever(preferences.observe(BooleanKey.ApsUseSmb)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(StringNonKey.QuickLaunchActions)).thenReturn(MutableStateFlow(""))
        whenever(preferences.get(StringNonKey.LastOverviewProfileName)).thenReturn("")
        whenever(preferences.get(BooleanNonKey.LastOverviewProfileModified)).thenReturn(false)
        whenever(preferences.get(IntNonKey.LastOverviewProfilePercentage)).thenReturn(100)
        whenever(preferences.get(StringNonKey.LastOverviewProfileTargetRange)).thenReturn("")
        whenever(preferences.get(StringNonKey.LastOverviewRunningMode)).thenReturn("")
        whenever(preferences.get(LongNonKey.LastPumpExpectedEndTimeMillis)).thenReturn(0L)
        whenever(preferences.get(LongNonKey.LastLoopRunTimestamp)).thenReturn(0L)
        whenever(preferences.get(DoubleNonKey.LastPumpReservoirUnits)).thenReturn(-1.0)
        whenever(rh.gs(any<TextRef>())).thenReturn("")

        sut = createViewModel()
    }

    private fun createViewModel() = MainViewModel(
        activePlugin, config, urlOpener, preferences, fabricPrivacy, rh, dateUtil,
        overviewDataCache, iobCobCalculator, profileFunction, profileUtil, constraintChecker, quickWizard,
        automation, persistenceLayer, aapsLogger, quickLaunchResolver, wizardExecutor,
        batchExecutor, uel, loop, processedDeviceStatusData, protectionCheck, sceneActions, sceneChainTargetResolver,
        activeSceneManager, rxBus, nsClient, visibilityContext,
        CoroutineScope(UnconfinedTestDispatcher())
    )

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState exposes initial values`() {
        // The first value is the cached startup snapshot before asynchronous hydration runs.
        val state = sut.uiState.value
        assertThat(state.isSimpleMode).isTrue()
        assertThat(state.isDrawerOpen).isFalse()
        assertThat(state.runningMode).isEqualTo(RM.Mode.DISABLED_LOOP)
        assertThat(sut.profileCardTempTargetStateFlow.value.state).isEqualTo(TempTargetChipState.None)
        assertThat(state.quickWizardItems).isEmpty()
    }

    @Test
    fun `algorithm reasoning uses the local loop result`() {
        val result = mock<APSResult> {
            on { reason }.thenReturn("Keep basal rate")
        }
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { constraintsProcessed = result })

        sut = createViewModel()

        assertThat(sut.uiState.value.algorithmReasoning).isEqualTo("Keep basal rate")
    }

    /**
     * When the loop stopped before it reached the algorithm, the reason it stopped is newer than
     * the result of the last run that did finish, so it is the one to show.
     */
    @Test
    fun `algorithm reasoning prefers the reason the loop stopped`() {
        val result = mock<APSResult> {
            on { reason }.thenReturn("Keep basal rate")
        }
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { constraintsProcessed = result })
        whenever(loop.lastRunStatus).thenReturn("Pump is busy")

        sut = createViewModel()

        assertThat(sut.uiState.value.algorithmReasoning).isEqualTo("Pump is busy")
    }

    /**
     * The pill shows the reason itself, so the overview state has to carry it apart from the
     * reasoning text. A client has no local loop run and must not claim the loop stopped.
     */
    @Test
    fun `the stopped reason is carried for the pill and left empty on a client`() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(loop.lastRunStatus).thenReturn("Pump is busy")

        sut = createViewModel()
        assertThat(sut.uiState.value.loopStoppedReason).isEqualTo("Pump is busy")

        whenever(config.AAPSCLIENT).thenReturn(true)
        sut = createViewModel()
        assertThat(sut.uiState.value.loopStoppedReason).isNull()
    }

    @Test
    fun `algorithm reasoning uses the client device status result`() {
        val result = mock<APSResult> {
            on { reason }.thenReturn("Reduce basal rate")
        }
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(processedDeviceStatusData.getAPSResult()).thenReturn(result)

        sut = createViewModel()

        assertThat(sut.uiState.value.algorithmReasoning).isEqualTo("Reduce basal rate")
    }

    @Test
    fun `initial uiState restores cached Trio status`() {
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(preferences.get(StringNonKey.LastOverviewProfileName)).thenReturn("Workday")
        whenever(preferences.get(BooleanNonKey.LastOverviewProfileModified)).thenReturn(true)
        whenever(preferences.get(IntNonKey.LastOverviewProfilePercentage)).thenReturn(80)
        whenever(preferences.get(StringNonKey.LastOverviewProfileTargetRange)).thenReturn("90-110")
        whenever(preferences.get(StringNonKey.LastOverviewRunningMode)).thenReturn(RM.Mode.CLOSED_LOOP.name)
        whenever(preferences.get(LongNonKey.LastLoopRunTimestamp)).thenReturn(7_000L)
        whenever(preferences.get(LongNonKey.LastPumpExpectedEndTimeMillis)).thenReturn(20_000L)
        whenever(preferences.get(DoubleNonKey.LastPumpReservoirUnits)).thenReturn(42.0)
        whenever(rh.gs(any<TextRef>())).thenReturn("Closed loop")

        sut = createViewModel()

        val state = sut.uiState.value
        assertThat(state.profileName).isEqualTo("Workday")
        assertThat(state.isProfileModified).isTrue()
        assertThat(state.profilePercentage).isEqualTo(80)
        assertThat(state.profileTargetRangeText).isEqualTo("90-110")
        assertThat(state.runningMode).isEqualTo(RM.Mode.CLOSED_LOOP)
        assertThat(state.runningModeText).isEqualTo("Closed loop")
        assertThat(state.lastLoopAgeMillis).isEqualTo(3_000L)
        assertThat(state.pumpEndTimeMillis).isEqualTo(20_000L)
        assertThat(state.reservoirUnits).isEqualTo(42.0)
    }

    @Test
    fun `pump suspension follows pump events and keeps reservoir data`() {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        val pumpEvents = MutableSharedFlow<EventPumpStatusChanged>(extraBufferCapacity = 1)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(pumpEvents)
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(preferences.get(DoubleNonKey.LastPumpReservoirUnits)).thenReturn(42.0)

        val viewModel = createViewModel()
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.pumpSuspended).isFalse()

        whenever(pump.isSuspended()).thenReturn(true)
        pumpEvents.tryEmit(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.pumpSuspended).isTrue()
        assertThat(viewModel.uiState.value.reservoirUnits).isEqualTo(42.0)

        whenever(pump.isSuspended()).thenReturn(false)
        pumpEvents.tryEmit(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.pumpSuspended).isFalse()
        assertThat(viewModel.uiState.value.reservoirUnits).isEqualTo(42.0)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `active temp target does not replace the profile target range`() = runBlocking {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        val profile = mock<EffectiveProfile>()
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(profile.getTargetLowMgdl()).thenReturn(90.0)
        whenever(profile.getTargetHighMgdl()).thenReturn(110.0)
        whenever(profile.insulinConcentration()).thenReturn(1.0)
        whenever(pump.reservoirLevel).thenReturn(MutableStateFlow(PumpInsulin(42.0)))
        whenever(profileUtil.toTargetRangeString(90.0, 110.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL))
            .thenReturn("90-110")
        whenever(dateUtil.untilString(65_000L, rh)).thenReturn("(1 min)")
        val targets = MutableStateFlow<TempTargetDisplayData?>(
            TempTargetDisplayData("140", TempTargetState.ACTIVE, 5_000L, 60_000L)
        )
        whenever(overviewDataCache.tempTargetFlow).thenReturn(targets)

        val viewModel = createViewModel()
        viewModel.profileCardTempTargetStateFlow.launchIn(viewModel.viewModelScope)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.profileTargetRangeText).isEqualTo("90-110")
        assertThat(viewModel.profileCardTempTargetStateFlow.value.rangeText).isEqualTo("140")
        assertThat(viewModel.profileCardTempTargetStateFlow.value.state).isEqualTo(TempTargetChipState.Active)

        targets.value = TempTargetDisplayData("90-110", TempTargetState.NONE, 0L, 0L)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.profileTargetRangeText).isEqualTo("90-110")
        assertThat(viewModel.profileCardTempTargetStateFlow.value.state).isEqualTo(TempTargetChipState.None)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `actionConfirmation starts null and dismiss keeps it null`() {
        assertThat(sut.actionConfirmation.value).isNull()
        sut.dismissActionConfirmation()
        assertThat(sut.actionConfirmation.value).isNull()
    }

    @Test
    fun `reachability flows are exposed from nsClient`() {
        assertThat(sut.masterReachable.value).isTrue()
        assertThat(sut.masterOrPairedClient.value).isTrue()
    }

    @Test
    fun `formatDuration delegates to dateUtil`() {
        whenever(dateUtil.timeRemainingString(any(), any())).thenReturn("1h 30m")
        assertThat(sut.formatDuration(5_400_000L)).isEqualTo("1h 30m")
    }

    @Test
    fun `BG circle opens the active BG source plugin`() {
        whenever(activePlugin.getSpecificPluginsList(PluginType.BGSOURCE))
            .thenReturn(arrayListOf(bgSourcePlugin))
        whenever(bgSourcePlugin.isEnabled(PluginType.BGSOURCE)).thenReturn(true)

        val request = sut.bgSourceNavigationRequest()

        assertThat(request).isEqualTo(
            NavigationRequest.Plugin(bgSourcePlugin::class.simpleName.orEmpty())
        )
    }

    @Test
    fun `BG circle opens BG source selection when none is active`() {
        whenever(activePlugin.getSpecificPluginsList(PluginType.BGSOURCE))
            .thenReturn(arrayListOf())

        assertThat(sut.bgSourceNavigationRequest()).isEqualTo(
            NavigationRequest.PluginCategory(PluginType.BGSOURCE)
        )
    }

    /**
     * The About dialog's "don't kill my app" button, which is Android's problem and nobody else's.
     *
     * The dialog moved into shared code and the gate did not come with it, so iOS and desktop drew a
     * button that opened `dontkillmyapp.com/apple` and `dontkillmyapp.com/windows-11` - addresses
     * that are not pages, for a thing those systems do not do.
     */
    /**
     * The Exit row, which iOS must not offer.
     *
     * `IosAppExit` refuses on purpose - Apple records a self-terminating app as a crash - so the row
     * ran `exitApp`, wrote an `EXIT_AAPS` user entry, and then nothing happened.
     */
    @Test
    fun `the exit row is offered everywhere except iOS`() {
        whenever(config.platform).thenReturn(AppPlatform.Android)
        assertThat(sut.showExit).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Desktop)
        assertThat(sut.showExit).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Ios)
        assertThat(sut.showExit).isFalse()
    }

    @Test
    fun `the battery help button is offered only on Android`() {
        whenever(config.platform).thenReturn(AppPlatform.Android)
        assertThat(sut.showBatteryHelp).isTrue()

        whenever(config.platform).thenReturn(AppPlatform.Ios)
        assertThat(sut.showBatteryHelp).isFalse()

        whenever(config.platform).thenReturn(AppPlatform.Desktop)
        assertThat(sut.showBatteryHelp).isFalse()
    }

    /**
     * The loop pill used to show the age the view model was built with: a client has no local loop
     * run, so the age only became right after the app was force closed. The newest of the local
     * run, the device status from the master and the stored value is used now.
     */
    @Test
    fun `last loop age follows the device status on a client`() {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(preferences.get(LongNonKey.LastLoopRunTimestamp)).thenReturn(4_000L)
        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(4_000L)

        val viewModel = createViewModel()
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(6_000L)

        // A newer loop run reaches the client as device status, without an app restart.
        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(9_000L)
        main.scheduler.advanceTimeBy(31_000L)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(1_000L)
        viewModel.viewModelScope.cancel()
    }

    /**
     * A CancellationException from a call inside the chip build (a cancelled calculation, say) used
     * to end the chip flow without a crash, so the loop age stayed frozen until the app was force
     * closed. The flow must keep going and the loop age must still follow new loop runs.
     */
    @Test
    fun `last loop age keeps updating when a chip build fails`() {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(quickWizard.list()).thenThrow(CancellationException("calculation stopped"))
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(preferences.get(LongNonKey.LastLoopRunTimestamp)).thenReturn(4_000L)
        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(4_000L)

        val viewModel = createViewModel()
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(6_000L)

        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(9_000L)
        main.scheduler.advanceTimeBy(31_000L)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(1_000L)
        viewModel.viewModelScope.cancel()
    }

    /**
     * A chip build that waits forever throws nothing and logs nothing. It used to block every later
     * build, so the chips and the loop age froze silently. The time limit must still let the loop
     * age move, and the next tick must start a fresh build.
     */
    @Test
    fun `last loop age keeps updating when a chip build never finishes`() {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(dateUtil.now()).thenReturn(10_000L)
        whenever(preferences.get(LongNonKey.LastLoopRunTimestamp)).thenReturn(4_000L)
        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(4_000L)
        profileFunction.stub {
            onBlocking { getProfile() } doSuspendableAnswer { awaitCancellation() }
        }

        val viewModel = createViewModel()
        main.scheduler.advanceTimeBy(11_000L)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(6_000L)

        whenever(dateUtil.now()).thenReturn(40_000L)
        whenever(processedDeviceStatusData.openApsTimestamp).thenReturn(39_000L)
        main.scheduler.advanceTimeBy(31_000L)
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.lastLoopAgeMillis).isEqualTo(1_000L)
        viewModel.viewModelScope.cancel()
    }

    /** The pill shows "Looping" only while the loop plugin says a run is in progress. */
    @Test
    fun `isLooping follows the loop run state`() {
        sut.viewModelScope.cancel()
        val main = StandardTestDispatcher()
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(dateUtil.now()).thenReturn(10_000L)

        val viewModel = createViewModel()
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.isLooping).isFalse()

        loopRunning.value = true
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.isLooping).isTrue()

        loopRunning.value = false
        main.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.isLooping).isFalse()
        viewModel.viewModelScope.cancel()
    }

    /**
     * The sensor info sheet must have data as soon as the overview is shown. It used to read once
     * and then only every 30 s, so the sheet stayed empty after start up.
     */
    @Test
    fun `sensor info loads after start up and reloads on a new sensor change`() {
        // The view model from setUp was built without these stubs. Stop it so it does not run below.
        sut.viewModelScope.cancel()
        // Shares the scheduler of the main test dispatcher set in setUp.
        val main = StandardTestDispatcher()
        val initProgress = MutableStateFlow(InitProgress())
        val therapyEvents = MutableSharedFlow<List<TE>>(extraBufferCapacity = 1)
        whenever(config.initProgressFlow).thenReturn(initProgress)
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(therapyEvents)
        whenever(persistenceLayer.observeChanges(GV::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.databaseClearedFlow).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        val bgSource: BgSource = mock()
        whenever(bgSource.sensorBatteryLevel).thenReturn(80)
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        val sensorEvent: TE = mock()
        whenever(sensorEvent.timestamp).thenReturn(1_000L)
        persistenceLayer.stub { onBlocking { getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE) }.thenReturn(null) }
        persistenceLayer.stub { onBlocking { getBgReadingsDataFromTimeToTime(any(), any(), any()) }.thenReturn(emptyList()) }
        persistenceLayer.stub { onBlocking { getApsResults(any(), any()) }.thenReturn(emptyList()) }

        val viewModel = createViewModel()
        main.scheduler.runCurrent()
        // Not read before the app has finished start up.
        assertThat(viewModel.sensorInfo.value).isEqualTo(SensorInfo())

        initProgress.value = InitProgress(done = true)
        main.scheduler.runCurrent()
        assertThat(viewModel.sensorInfo.value).isEqualTo(SensorInfo(batteryLevel = 80))

        persistenceLayer.stub { onBlocking { getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE) }.thenReturn(sensorEvent) }
        therapyEvents.tryEmit(listOf(sensorEvent))
        main.scheduler.runCurrent()
        assertThat(viewModel.sensorInfo.value).isEqualTo(SensorInfo(startedAt = 1_000L, batteryLevel = 80))

        // A refresh reads again, even when no change was sent.
        whenever(bgSource.sensorBatteryLevel).thenReturn(50)
        viewModel.refreshSensorInfo()
        main.scheduler.runCurrent()
        assertThat(viewModel.sensorInfo.value).isEqualTo(SensorInfo(startedAt = 1_000L, batteryLevel = 50))

        // The sheet reads the data itself when it opens.
        whenever(bgSource.sensorBatteryLevel).thenReturn(40)
        assertThat(runBlocking { viewModel.loadSensorInfo() }).isEqualTo(SensorInfo(startedAt = 1_000L, batteryLevel = 40))
        viewModel.viewModelScope.cancel()
    }
}
