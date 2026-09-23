package app.aaps.pump.omnipod.omnipod5.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.omnipod5.R
import app.aaps.pump.omnipod.omnipod5.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.omnipod5.history.data.BolusRecord
import app.aaps.pump.omnipod.omnipod5.history.data.HistoryRecord
import app.aaps.pump.omnipod.omnipod5.history.data.TempBasalRecord

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun O5PodHistoryScreen(records: List<HistoryRecord>, rh: ResourceHelper, profileUtil: ProfileUtil) {
    val groups = remember { PumpHistoryEntryGroup.getTranslatedList(rh) }
    var selectedGroup by remember { mutableStateOf(PumpHistoryEntryGroup.All) }
    val dateUtil = LocalDateUtil.current
    val filtered = if (selectedGroup == PumpHistoryEntryGroup.All) records else records.filter {
        groupForCommandType(it.commandType) == selectedGroup
    }
    val grouped = filtered.groupBy { dateUtil.dateString(it.displayTimestamp()) }

    Column(Modifier.fillMaxSize()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            groups.forEach { group ->
                FilterChip(
                    selected = selectedGroup == group,
                    onClick = { selectedGroup = group },
                    label = { Text(group.translated ?: "") }
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            grouped.forEach { (date, dayRecords) ->
                stickyHeader(key = date) {
                    Text(
                        text = dateUtil.dateStringRelative(dayRecords.first().displayTimestamp(), rh),
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = AapsSpacing.medium),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(dayRecords, key = { it.id }) { record ->
                    O5HistoryCard(record, rh, profileUtil, dateUtil)
                }
            }
        }
    }
}

@Composable
private fun O5HistoryCard(record: HistoryRecord, rh: ResourceHelper, profileUtil: ProfileUtil, dateUtil: DateUtil) {
    AapsCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(AapsSpacing.large), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (record.isSuccess()) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
                tint = if (record.isSuccess()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(AapsSpacing.large))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = rh.gs(record.commandType.resourceId),
                        fontWeight = FontWeight.SemiBold,
                        color = if (record.isSuccess()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    Text(dateUtil.timeString(record.displayTimestamp()), style = MaterialTheme.typography.bodySmall)
                }
                formatValue(record, rh, profileUtil)?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = AapsSpacing.small))
                }
            }
        }
    }
}

private fun formatValue(record: HistoryRecord, rh: ResourceHelper, profileUtil: ProfileUtil): String? {
    if (!record.isSuccess()) return rh.gs(R.string.omnipod_o5_history_command_failed)
    return when (val value = record.record) {
        is TempBasalRecord -> rh.gs(R.string.omnipod_o5_history_tbr_value, value.rate, value.duration)
        is BolusRecord -> rh.gs(R.string.omnipod_o5_history_bolus_value, value.amount)
        is BasalValuesRecord -> profileUtil.getBasalProfilesDisplayable(value.segments.toTypedArray(), PumpType.OMNIPOD_5)
        null -> null
    }
}

private fun groupForCommandType(type: OmnipodCommandType): PumpHistoryEntryGroup = when (type) {
    OmnipodCommandType.INITIALIZE_POD, OmnipodCommandType.INSERT_CANNULA,
    OmnipodCommandType.DEACTIVATE_POD, OmnipodCommandType.DISCARD_POD -> PumpHistoryEntryGroup.Prime
    OmnipodCommandType.CANCEL_TEMPORARY_BASAL, OmnipodCommandType.SET_BASAL_PROFILE,
    OmnipodCommandType.SET_TEMPORARY_BASAL, OmnipodCommandType.RESUME_DELIVERY,
    OmnipodCommandType.SUSPEND_DELIVERY -> PumpHistoryEntryGroup.Basal
    OmnipodCommandType.SET_BOLUS, OmnipodCommandType.CANCEL_BOLUS -> PumpHistoryEntryGroup.Bolus
    OmnipodCommandType.ACKNOWLEDGE_ALERTS, OmnipodCommandType.CONFIGURE_ALERTS,
    OmnipodCommandType.PLAY_TEST_BEEP -> PumpHistoryEntryGroup.Alarm
    OmnipodCommandType.GET_POD_STATUS, OmnipodCommandType.SET_TIME -> PumpHistoryEntryGroup.Configuration
    OmnipodCommandType.READ_POD_PULSE_LOG -> PumpHistoryEntryGroup.Unknown
}
