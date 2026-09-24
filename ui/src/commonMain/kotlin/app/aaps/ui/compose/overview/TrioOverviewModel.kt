package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.PaddingValues
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.main.TempTargetUiState
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import kotlinx.coroutines.flow.StateFlow

data class TrioOverviewModel(
    val profileName: String,
    val isProfileModified: Boolean,
    val profileProgress: Float,
    val profileSceneManaged: Boolean,
    val profilePercentage: Int,
    val profileTargetRangeText: String,
    val runningMode: RM.Mode,
    val runningModeText: String,
    val runningModeRemaining: String,
    val runningModeProgress: Float,
    val runningModeSceneManaged: Boolean,
    val lastLoopAgeMillis: Long?,
    val smbEnabled: Boolean,
    val tbrState: TbrState,
    val profileCardTempTargetStateFlow: StateFlow<TempTargetUiState>,
    val calcProgressFlow: StateFlow<Int>,
    val graphViewModel: GraphViewModel,
    val chipsViewModel: ChipsViewModel,
    val onNavigate: (NavigationRequest) -> Unit,
    val onTbrChipClick: () -> Unit,
    val onIobChipClick: () -> Unit,
    val paddingValues: PaddingValues,
    val activeSceneState: ActiveSceneState?,
    val sceneExpired: Boolean,
    val onEndScene: () -> Unit,
    val onDismissScene: () -> Unit,
    val endSceneEnabled: Boolean,
    val commandsAllowed: Boolean,
    val pumpNeedsSetup: Boolean,
    val pumpEndTimeMillis: Long?,
    val reservoirUnits: Double?,
    val onBgSourceClick: () -> Unit,
    val notificationsFlow: StateFlow<List<AapsNotification>>,
    val onDismissNotification: (AapsNotification) -> Unit,
    val onNotificationActionClick: (AapsNotification) -> Unit,
    val autoShowNotificationSheet: Boolean,
    val onAutoShowConsumed: () -> Unit,
    val bolusStateFlow: StateFlow<BolusProgressState?>,
    val onStopBolus: () -> Unit,
    val timeInRangeTodayPercentFlow: StateFlow<Int?>,
    val formatDuration: (Long) -> String
)
