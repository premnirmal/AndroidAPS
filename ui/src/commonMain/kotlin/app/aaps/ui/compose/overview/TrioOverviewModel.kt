package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.PaddingValues
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel

data class TrioOverviewModel(
    val profileName: String,
    val isProfileModified: Boolean,
    val profileProgress: Float,
    val profileSceneManaged: Boolean,
    val tempTargetText: String,
    val tempTargetState: TempTargetChipState,
    val tempTargetProgress: Float,
    val tempTargetReason: TT.Reason?,
    val tempTargetSceneManaged: Boolean,
    val runningMode: RM.Mode,
    val runningModeText: String,
    val runningModeRemaining: String,
    val runningModeProgress: Float,
    val runningModeSceneManaged: Boolean,
    val smbEnabled: Boolean,
    val isSimpleMode: Boolean,
    val tbrState: TbrState,
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
    val onBgSourceClick: () -> Unit,
    val notificationCount: Int,
    val highestNotificationLevel: NotificationLevel?,
    val onNotificationClick: () -> Unit,
    val bolusState: BolusProgressState?,
    val onStopBolus: () -> Unit,
    val timeInRangeTodayPercent: Int?,
    val formatDuration: (Long) -> String
)
