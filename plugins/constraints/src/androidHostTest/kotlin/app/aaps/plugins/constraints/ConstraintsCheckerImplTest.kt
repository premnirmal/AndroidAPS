package app.aaps.plugins.constraints

import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.implementation.pump.PumpWithConcentrationImpl
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.constraints.safety.SafetyPlugin
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The active pump, as far as the checker can see it: a [Pump] with a basal cap of its own. This used
 * to be a real `DanaRPlugin`, with `DanaRSPlugin` and `InsightPlugin` built beside it but never asked
 * anything. That made the pump modules a dependency of this module's tests - so removing a pump from
 * `settings.gradle` failed the configuration of the whole build. The checker only needs something to
 * fold in; each driver tests its own cap (`DanaRPluginTest` and the Korean and v2 tests).
 */
private class CappedPumpPlugin(private val maxBasal: Double) : Pump by mock(), PumpPluginConstraints {

    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate = PumpRate(absoluteRate.cU.coerceAtMost(maxBasal))
}

/**
 * Created by mike on 18.03.2018.
 */
class ConstraintsCheckerImplTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var determineBasalSMB: DetermineBasalSMB
    @Mock lateinit var determineBasalAMA: DetermineBasalAMA
    @Mock lateinit var loop: Loop
    @Mock lateinit var passwordCheck: PasswordCheck
    @Mock lateinit var pumpWithConcentration: PumpWithConcentrationImpl

    /**
     * Real English for every reason the checker builds, so the sentences asserted below are the ones the
     * user reads. `:shared:tests` cannot see this module, so the generated map is handed over here.
     */
    private val text = generatedTextResolver("constraints" to ConstraintsStringsValues::textOf)

    private lateinit var constraintChecker: ConstraintsCheckerImpl
    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin

    @BeforeEach
    fun prepare() {
        // Mock persistenceLayer for OpenAPSSMBPlugin.onStart()
        runTest {
            whenever(persistenceLayer.getApsResults(any(), any())).thenReturn(emptyList())
        }

<<<<<<< HEAD
        whenever(rh.gs(ConstraintsStrings.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        whenever(rh.gs(CoreUiStrings.no_valid_basal_rate)).thenReturn("No valid basal rate read from pump")
        // :plugins:aps resolves its own strings through TextRef, so these need the ApsStrings key, not ConstraintsStrings.
        whenever(rh.gs(ApsStrings.hardlimit)).thenReturn("hard limit")
        whenever(rh.gs(CoreUiStrings.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(ApsStrings.maxvalueinpreferences)).thenReturn("max value in preferences")
        whenever(rh.gs(ApsStrings.autosens_disabled_in_preferences)).thenReturn("Autosens disabled in preferences")
        whenever(rh.gs(ApsStrings.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        whenever(rh.gs(CoreUiStrings.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(CoreUiStrings.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(ConstraintsStrings.maxvalueinpreferences)).thenReturn("max value in preferences")
        whenever(rh.gs(ApsStrings.max_basal_multiplier)).thenReturn("max basal multiplier")
        whenever(rh.gs(ApsStrings.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        whenever(rh.gs(CoreUiStrings.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(CoreUiStrings.limitingbolus)).thenReturn("Limiting bolus to %.1f U because of %s")
        whenever(rh.gs(ConstraintsStrings.hardlimit)).thenReturn("hard limit")
        whenever(rh.gs(ConstraintsStrings.limitingcarbs)).thenReturn("Limiting carbs to %d g because of %s")
        whenever(rh.gs(ApsStrings.limiting_iob)).thenReturn("Limiting IOB to %.1f U because of %s")
        whenever(rh.gs(CoreUiStrings.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(CoreUiStrings.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(CoreUiStrings.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(ConstraintsStrings.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        whenever(rh.gs(CoreUiStrings.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(ConstraintsStrings.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        whenever(rh.gs(CoreUiStrings.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(CoreUiStrings.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        whenever(rh.gs(CoreUiStrings.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")

=======
>>>>>>> origin/dev
        whenever(activePlugin.activePump).thenReturn(pumpWithConcentration)
        whenever(pumpWithConcentration.pumpDescription).thenReturn(PumpDescription())

        //SafetyPlugin
        constraintChecker = ConstraintsCheckerImpl(activePlugin, aapsLogger, ch, text)

<<<<<<< HEAD
=======
        // The real formatter rather than a mock: it is pure arithmetic over a duration, and the
        // objectives only read it for display.
        val durationText = PlainDurationText()
        val objectives = listOf(
            Objective0(preferences, text, durationText, dateUtil, activePlugin, virtualPumpPlugin, persistenceLayer, loop, iobCobCalculator, passwordCheck),
            Objective1(preferences, text, durationText, dateUtil),
            Objective2(preferences, text, durationText, dateUtil),
            Objective3(preferences, text, durationText, dateUtil),
            Objective4(preferences, text, durationText, dateUtil, profileFunction),
            Objective5(preferences, text, durationText, dateUtil),
            Objective6(preferences, text, durationText, dateUtil, constraintsChecker, loop),
            Objective7(preferences, text, durationText, dateUtil),
            Objective8(preferences, text, durationText, dateUtil),
            Objective9(preferences, text, durationText, dateUtil)
        )
        objectivesPlugin = ObjectivesPlugin(aapsLogger, text, preferences, config, objectives, mock())
        runBlocking { objectivesPlugin.onStart() }
>>>>>>> origin/dev
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                aapsLogger, rxBus, constraintChecker, text, profileFunction, profileUtil, config, activePlugin, iobCobCalculator,
                hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, smbGlucoseStatusProvider, tddCalculator, bgQualityCheck,
                notificationManager, determineBasalSMB, profiler, GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), { apsResultProvider() }, ch,
                fabricPrivacy
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                aapsLogger, rxBus, constraintChecker, text, config, profileFunction, activePlugin, iobCobCalculator, processedTbrEbData,
                hardLimits, dateUtil, persistenceLayer, smbGlucoseStatusProvider, preferences, determineBasalAMA,
                GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), { apsResultProvider() }, ch, fabricPrivacy, mock()
            )
        safetyPlugin =
            SafetyPlugin(
                aapsLogger, text, preferences, constraintChecker, activePlugin, hardLimits,
                config, persistenceLayer, dateUtil, notificationManager, decimalFormatter
            )
        val constraintsPluginsList = ArrayList<PluginBase>()
        constraintsPluginsList.add(safetyPlugin)
        // Pump plugins are no longer PluginConstraints — their cU delivery caps are PumpPluginConstraints,
        // folded into the scan by ConstraintsCheckerImpl via activePumpInternal (stubbed per test).
        constraintsPluginsList.add(openAPSAMAPlugin)
        constraintsPluginsList.add(openAPSSMBPlugin)
        whenever(activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class)).thenReturn(constraintsPluginsList)
    }

    @Test
    fun isLoopInvocationAllowedTest() {
        val c = constraintChecker.isLoopInvocationAllowed()
        assertThat(c.reasonList).isEmpty()
        assertThat(c.mostLimitedReasonList).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test
    fun isClosedLoopAllowedTest() = runTest {
        whenever(config.isEngineeringModeOrRelease()).thenReturn(true)
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        val c: Constraint<Boolean> = constraintChecker.isClosedLoopAllowed()
        aapsLogger.debug("Reason list: " + c.reasonList.toString())
        assertThat(c.reasonList).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test
    fun isAutosensModeEnabledTest() {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        whenever(preferences.get(BooleanKey.ApsUseAutosens)).thenReturn(false)
        val c = constraintChecker.isAutosensModeEnabled()
        assertThat(c.reasonList).hasSize(1)
        assertThat(c.mostLimitedReasonList).hasSize(1)
        assertThat(c.value()).isFalse()
    }

    // Safety
    @Test
    fun isAdvancedFilteringEnabledTest() = runTest {
        whenever(persistenceLayer.isAdvancedFilteringSupported()).thenReturn(false)
        val c = constraintChecker.isAdvancedFilteringEnabled()
        assertThat(c.reasonList).hasSize(1) // Safety
        assertThat(c.mostLimitedReasonList).hasSize(1) // Safety
        assertThat(c.value()).isFalse()
    }

    // SMB should limit
    @Test
    fun isSuperBolusEnabledTest() {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        val c = constraintChecker.isSuperBolusEnabled()
        assertThat(c.value()).isFalse() // SMB should limit
    }

    @Test
    fun isSMBModeEnabledTest() = runTest {
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        whenever(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
//        whenever(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(true))
        val c = constraintChecker.isSMBModeEnabled()
        assertThat(c.reasonList).hasSize(2)
        assertThat(c.mostLimitedReasonList).hasSize(2)
        assertThat(c.value()).isFalse()
    }

    // applyBasalConstraints tests
    @Test
    fun basalRateShouldBeLimited() {
        val pump = CappedPumpPlugin(maxBasal = 0.8)
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(pump)
        // The active pump's cU cap is folded into the IU scan by ConstraintsChecker via activePumpInternal.
        whenever(activePlugin.activePumpInternal).thenReturn(pump)

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBasalAllowed(validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.8)
        // Safety hard-limit + the active pump's cU cap, folded into the IU scan by ConstraintsChecker.
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("CappedPump: Limiting max basal rate to 0.80 U/h because of pump limit")
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(CappedPumpPlugin(maxBasal = 0.8))

        // No limit by default
        whenever(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        whenever(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        whenever(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val i = constraintChecker.getMaxBasalPercentAllowed(validProfile)
        assertThat(i.value()).isEqualTo(200)
        // Pump plugins no longer contribute percent reasons — their percent cap was redundant with SafetyPlugin,
        // which still caps to the same value (tbrSettings.maxDose); remaining reasons are all from SafetyPlugin.
        assertThat(i.reasonList).hasSize(4)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 200% because of pump limit")
    }

    // applyBolusConstraints tests
    @Test
    fun bolusAmountShouldBeLimited() {
        whenever(pumpWithConcentration.activePumpInternal).thenReturn(virtualPumpPlugin)
        whenever(virtualPumpPlugin.pumpDescription).thenReturn(PumpDescription())

        // No limit by default
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")

        // Apply all limits
        val d = constraintChecker.getMaxBolusAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        // 2x Safety only. A pump's own bolus cap is folded in the same way as the basal cap, but the active
        // pump here is the virtual pump, which has none.
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 3.0 U because of max value in preferences")
    }

    // applyCarbsConstraints tests
    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        whenever(preferences.get(IntKey.SafetyMaxCarbs)).thenReturn(48)

        // Apply all limits
        val i = constraintChecker.getMaxCarbsAllowed()
        assertThat(i.value()).isEqualTo(48)
        assertThat(i.reasonList).hasSize(1)
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting carbs to 48 g because of max value in preferences")
    }

    // applyMaxIOBConstraints tests
    @Test
    fun iobAMAShouldBeLimited() = runTest {
        // No limit by default
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(preferences.get(DoubleKey.ApsAmaMaxIob)).thenReturn(1.5)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSAMAPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(1.5)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences")
    }

    @Test
    fun iobSMBShouldBeLimited() = runTest {
        // No limit by default
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(preferences.get(DoubleKey.ApsSmbMaxIob)).thenReturn(3.0)
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        openAPSSMBPlugin.setPluginEnabledBlocking(PluginType.APS, true)
        openAPSAMAPlugin.setPluginEnabledBlocking(PluginType.APS, false)

        // Apply all limits
        val d = constraintChecker.getMaxIOBAllowed()
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.reasonList).hasSize(2)
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences")
    }
}
