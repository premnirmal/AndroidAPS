package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
<<<<<<< HEAD
=======
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
>>>>>>> origin/dev
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
    timeInRangeTodayPercentFlow: StateFlow<Int?>
) {
<<<<<<< HEAD
=======
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showPumpActivityDialog by remember { mutableStateOf(false) }
    val showPumpFab = isPumpCommunicating || (bolusState != null && bolusState.isSMB)

    // Three seconds is enough to glance at the card, but not to hear it read out. Ask the platform
    // how long this content needs instead: with a screen reader on it stretches the timeout, and
    // with one off calculateRecommendedTimeoutMillis hands back the original 3 seconds unchanged.
    val accessibilityManager = LocalAccessibilityManager.current
    LaunchedEffect(showPumpFab) {
        if (!showPumpFab && showPumpActivityDialog) {
            delay(
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = 3_000,
                    containsIcons = true,
                    containsText = true,
                    containsControls = true
                ) ?: 3_000
            )
            showPumpActivityDialog = false
        }
    }

    LaunchedEffect(autoShowNotificationSheet) {
        if (autoShowNotificationSheet) {
            showNotificationSheet = true
            onAutoShowConsumed()
        }
    }

>>>>>>> origin/dev
    val runningModeSceneManaged = activeSceneState?.scopedRecords?.rmId
        ?.let { it == runningModeRecordId && it > 0 } == true
    val profileSceneManaged = activeSceneState?.scopedRecords?.psId
        ?.let { it == profilePsId && it > 0 } == true

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
