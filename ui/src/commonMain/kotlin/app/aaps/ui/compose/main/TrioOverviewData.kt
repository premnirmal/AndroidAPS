package app.aaps.ui.compose.main

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.overview.TimeInRangeToday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Extra overview data used by Trio, with the same lifetime as the main ViewModel. */
internal class TrioOverviewData(
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope
) {
    // State rather than an event, so a refresh before collection starts is not lost.
    private val sensorInfoRefresh = MutableStateFlow(0)

    val sensorInfo: StateFlow<SensorInfo> = flow {
        config.initProgressFlow.first { it.done }
        emitAll(
            merge(
                persistenceLayer.observeChanges(TE::class),
                persistenceLayer.observeChanges(GV::class),
                persistenceLayer.databaseClearedFlow,
                rxBus.toFlow(EventConfigBuilderChange::class),
                sensorInfoRefresh,
                flow {
                    while (true) {
                        delay(30_000L)
                        emit(Unit)
                    }
                }
            ).map { }
        )
    }
        .conflate()
        .mapNotNull { readSensorInfo() }
        .stateIn(scope, SharingStarted.Eagerly, SensorInfo())

    private val _timeInRangeToday = MutableStateFlow<TimeInRangeToday?>(null)
    val timeInRangeToday: StateFlow<TimeInRangeToday?> = _timeInRangeToday.asStateFlow()
    private var timeInRangeTodayJob: Job? = null

    fun refreshSensorInfo() {
        sensorInfoRefresh.update { it + 1 }
    }

    suspend fun loadSensorInfo(): SensorInfo? {
        config.initProgressFlow.first { it.done }
        return readSensorInfo()
    }

    private suspend fun readSensorInfo(): SensorInfo? =
        try {
            val source = activePlugin.activeBgSource
            SensorInfo(
                sourceName = (source as? PluginBase)?.name.orEmpty(),
                startedAt = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp,
                batteryLevel = source.sensorBatteryLevel
            ).also { aapsLogger.debug(LTag.BGSOURCE, "TrioOverviewData: Sensor info read: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "TrioOverviewData: Failed to read sensor info", e)
            null
        }

    fun refreshTimeInRangeToday() {
        timeInRangeTodayJob?.cancel()
        timeInRangeTodayJob = scope.launch { updateTimeInRangeToday() }
    }

    suspend fun refreshTimeInRangeTodayNow() {
        timeInRangeTodayJob?.cancel()
        updateTimeInRangeToday()
    }

    /** Uses the overview marks and the same outer limits as the statistics reports. */
    private suspend fun updateTimeInRangeToday() {
        val start = MidnightTime.calc(dateUtil.now())
        val end = dateUtil.now()
        val lowMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.OverviewLowMark))
        val highMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.OverviewHighMark))
        val veryLowMgdl = minOf(Constants.STATS_RANGE_VERY_LOW_MMOL * Constants.MMOLL_TO_MGDL, lowMgdl)
        val veryHighMgdl = maxOf(Constants.STATS_RANGE_VERY_HIGH_MMOL * Constants.MMOLL_TO_MGDL, highMgdl)
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
            .filter { it.value >= 39.0 }
        _timeInRangeToday.value = if (readings.isEmpty()) null
        else {
            var veryLow = 0
            var low = 0
            var inRange = 0
            var high = 0
            var veryHigh = 0
            readings.forEach {
                when {
                    it.value < veryLowMgdl   -> veryLow++
                    it.value < lowMgdl       -> low++
                    it.value <= highMgdl     -> inRange++
                    it.value <= veryHighMgdl -> high++
                    else                    -> veryHigh++
                }
            }
            val total = readings.size.toDouble()
            fun share(count: Int) = count * 100.0 / total
            TimeInRangeToday(
                veryLowPercent = share(veryLow),
                lowPercent = share(low),
                inRangePercent = share(inRange),
                highPercent = share(high),
                veryHighPercent = share(veryHigh)
            )
        }
    }
}
