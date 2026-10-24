package app.aaps.ui.compose.main

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.overview.TimeInRangeToday
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrioOverviewDataTest {

    private val config = mock<Config>()
    private val persistence = mock<PersistenceLayer>()
    private val preferences = mock<Preferences>()
    private val profileUtil = mock<ProfileUtil>()
    private val readings = mutableListOf<GV>()

    private suspend fun TestScope.data(): TrioOverviewData {
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress(done = false)))
        whenever(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(70.0)
        whenever(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        whenever(profileUtil.convertToMgdlDetect(70.0)).thenReturn(70.0)
        whenever(profileUtil.convertToMgdlDetect(180.0)).thenReturn(180.0)
        whenever(persistence.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenAnswer { readings.toList() }
        return TrioOverviewData(
            activePlugin = mock(),
            config = config,
            persistenceLayer = persistence,
            rxBus = mock(),
            preferences = preferences,
            profileUtil = profileUtil,
            dateUtil = mock(),
            aapsLogger = mock(),
            scope = backgroundScope
        )
    }

    @Test
    fun `time in range keeps the five ranges and ignores invalid readings`() = runTest {
        readings += listOf(38.0, 50.0, 65.0, 70.0, 180.0, 200.0, 300.0)
            .map(::reading)
        val data = data()

        data.refreshTimeInRangeTodayNow()

        val result = checkNotNull(data.timeInRangeToday.value)
        assertThat(result.veryLowPercent).isWithin(0.001).of(100.0 / 6)
        assertThat(result.lowPercent).isWithin(0.001).of(100.0 / 6)
        assertThat(result.inRangePercent).isWithin(0.001).of(200.0 / 6)
        assertThat(result.highPercent).isWithin(0.001).of(100.0 / 6)
        assertThat(result.veryHighPercent).isWithin(0.001).of(100.0 / 6)
    }

    @Test
    fun `time in range clears when the readings are removed`() = runTest {
        readings += reading(100.0)
        val data = data()
        data.refreshTimeInRangeTodayNow()
        assertThat(data.timeInRangeToday.value).isEqualTo(TimeInRangeToday(0.0, 0.0, 100.0, 0.0, 0.0))

        readings.clear()
        data.refreshTimeInRangeTodayNow()

        assertThat(data.timeInRangeToday.value).isNull()
    }

    private fun reading(value: Double) = GV(
        timestamp = 0L,
        raw = null,
        value = value,
        trendArrow = TrendArrow.FLAT,
        noise = null,
        sourceSensor = SourceSensor.UNKNOWN
    )
}
