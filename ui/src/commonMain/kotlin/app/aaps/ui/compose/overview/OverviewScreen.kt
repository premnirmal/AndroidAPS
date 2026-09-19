package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun OverviewScreen(
    profileName: String,
    profilePsId: Long = 0,
    isProfileModified: Boolean,
    profileProgress: Float,
    profilePercentage: Int = 100,
    profileTargetRangeText: String = "",
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeRecordId: Long = 0,
    lastLoopAgeMillis: Long? = null,
    tbrState: TbrState,
    smbEnabled: Boolean,
    profileCardTempTargetStateFlow: StateFlow<TempTargetUiState>,
    calcProgressFlow: StateFlow<Int>,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    notificationsFlow: StateFlow<List<AapsNotification>>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    // Disables the command chips' click (running mode / profile / temp target) on an unpaired client — same gate as nav/Manage.
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    paddingValues: PaddingValues,
    bolusStateFlow: StateFlow<BolusProgressState?>,
    onStopBolus: () -> Unit = {},
    trioOverview: @Composable (TrioOverviewModel) -> Unit = {},
    pumpNeedsSetup: Boolean = false,
    pumpEndTimeMillis: Long? = null,
    reservoirUnits: Double? = null,
    onBgSourceClick: () -> Unit = {},
    timeInRangeTodayPercentFlow: StateFlow<Int?>,
    modifier: Modifier = Modifier
) {
    val runningModeSceneManaged = activeSceneState?.scopedRecords?.rmId
        ?.let { it == runningModeRecordId && it > 0 } == true
    val profileSceneManaged = activeSceneState?.scopedRecords?.psId
        ?.let { it == profilePsId && it > 0 } == true

    Box(modifier = modifier.fillMaxSize()) {
        trioOverview(
            TrioOverviewModel(
                    profileName = profileName,
                    isProfileModified = isProfileModified,
                    profileProgress = profileProgress,
                    profileSceneManaged = profileSceneManaged,
                    profilePercentage = profilePercentage,
                    profileTargetRangeText = profileTargetRangeText,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    lastLoopAgeMillis = lastLoopAgeMillis,
                    smbEnabled = smbEnabled,
                    tbrState = tbrState,
                    profileCardTempTargetStateFlow = profileCardTempTargetStateFlow,
                    calcProgressFlow = calcProgressFlow,
                    graphViewModel = graphViewModel,
                    chipsViewModel = chipsViewModel,
                    onNavigate = onNavigate,
                    onTbrChipClick = onTbrChipClick,
                    onIobChipClick = onIobChipClick,
                    paddingValues = paddingValues,
                    activeSceneState = activeSceneState,
                    sceneExpired = sceneExpired,
                    onEndScene = onEndScene,
                    onDismissScene = onDismissScene,
                    endSceneEnabled = endSceneEnabled,
                    commandsAllowed = commandsAllowed,
                    pumpNeedsSetup = pumpNeedsSetup,
                    pumpEndTimeMillis = pumpEndTimeMillis,
                    reservoirUnits = reservoirUnits,
                    onBgSourceClick = onBgSourceClick,
                    notificationsFlow = notificationsFlow,
                    onDismissNotification = onDismissNotification,
                    onNotificationActionClick = onNotificationActionClick,
                    autoShowNotificationSheet = autoShowNotificationSheet,
                    onAutoShowConsumed = onAutoShowConsumed,
                    bolusStateFlow = bolusStateFlow,
                    onStopBolus = onStopBolus,
                    timeInRangeTodayPercentFlow = timeInRangeTodayPercentFlow,
                    formatDuration = formatDuration
            )
        )
    }
}
