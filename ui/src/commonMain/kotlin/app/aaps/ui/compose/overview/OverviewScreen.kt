package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.isLandscape
import app.aaps.core.ui.compose.smallestScreenWidthDp
import app.aaps.core.ui.compose.TABLET_MIN_SW_DP
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.pump.PumpActivityDialog
import app.aaps.core.ui.compose.pump.PumpActivityFab
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private val SPLIT_LAYOUT_MIN_WIDTH: Dp = 720.dp

@Composable
fun OverviewScreen(
    profileName: String,
    profilePsId: Long = 0,
    isProfileModified: Boolean,
    profileProgress: Float,
    profilePercentage: Int = 100,
    profileTargetRangeText: String = "",
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetRecordId: Long = 0,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeRecordId: Long = 0,
    lastLoopAgeMillis: Long? = null,
    tbrState: TbrState,
    smbEnabled: Boolean,
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
    val tempTargetSceneManaged = activeSceneState?.scopedRecords?.ttId
        ?.let { it == tempTargetRecordId && it > 0 } == true
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
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    lastLoopAgeMillis = lastLoopAgeMillis,
                    smbEnabled = smbEnabled,
                    tbrState = tbrState,
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
