package app.aaps.trio.ui.compose.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.trio.TrioUi
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.main.MainUiState
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.compose.overview.TrioOverviewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TrioMainScreen(
    trioUi: TrioUi,
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    selectedTab: TrioNavTab,
    activeSceneCount: Int,
    onScenesClick: () -> Unit,
    onTabSelected: (TrioNavTab) -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    pumpNeedsSetup: Boolean,
    notificationsFlow: StateFlow<List<AapsNotification>>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    bolusStateFlow: StateFlow<BolusProgressState?>,
    onStopBolus: () -> Unit
) {
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val sensorInfo by mainViewModel.sensorInfo.collectAsStateWithLifecycle()
    val activeSceneState by mainViewModel.activeSceneState.collectAsStateWithLifecycle()
    val sceneExpired by mainViewModel.sceneExpired.collectAsStateWithLifecycle()
    val masterReachable by mainViewModel.masterReachable.collectAsStateWithLifecycle()
    val commandsAllowed by mainViewModel.masterOrPairedClient.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val previewMode = maxHeight < 500.dp
        var chromeVisible by remember { mutableStateOf(false) }
        val accessibilityManager = LocalAccessibilityManager.current
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(chromeVisible, previewMode) {
            if (!previewMode) {
                chromeVisible = false
            } else if (chromeVisible) {
                delay(
                    accessibilityManager?.calculateRecommendedTimeoutMillis(
                        3_000L, containsIcons = true, containsControls = true
                    ) ?: 3_000L
                )
                chromeVisible = false
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(
                    visible = !previewMode || chromeVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    trioUi.bottomBar(
                        selectedTab = selectedTab,
                        carbsRequired = cobUiState.carbsReq,
                        activeSceneCount = activeSceneCount,
                        onScenesClick = onScenesClick,
                        onTabSelected = onTabSelected,
                        onAddClick = { showAddSheet = true },
                        modifier = Modifier
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                trioUi.overview(
                    TrioOverviewModel(
                        profileName = uiState.profileName,
                        isProfileModified = uiState.isProfileModified,
                        profileProgress = uiState.profileProgress,
                        profileSceneManaged = activeSceneState?.scopedRecords?.psId
                            ?.let { it == uiState.profilePsId && it > 0 } == true,
                        profilePercentage = uiState.profilePercentage,
                        profileTargetRangeText = uiState.profileTargetRangeText,
                        runningMode = uiState.runningMode,
                        runningModeText = uiState.runningModeText,
                        runningModeRemaining = uiState.runningModeRemaining,
                        runningModeProgress = uiState.runningModeProgress,
                        runningModeSceneManaged = activeSceneState?.scopedRecords?.rmId
                            ?.let { it == uiState.runningModeRecordId && it > 0 } == true,
                        lastLoopAgeMillis = uiState.lastLoopAgeMillis,
                        isLooping = uiState.isLooping,
                        algorithmReasoning = uiState.algorithmReasoning,
                        loopStoppedReason = uiState.loopStoppedReason,
                        smbEnabled = uiState.smbEnabled,
                        tbrState = uiState.tbrState,
                        profileCardTempTargetStateFlow = mainViewModel.profileCardTempTargetStateFlow,
                        calcProgressFlow = mainViewModel.calcProgressFlow,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        onNavigate = onNavigate,
                        onTbrChipClick = mainViewModel::showTbrInfo,
                        onIobChipClick = chipsViewModel::showIobInfo,
                        paddingValues = PaddingValues(bottom = padding.calculateBottomPadding()),
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        onEndScene = { mainViewModel.requestSceneDeactivation() },
                        onDismissScene = { mainViewModel.dismissExpiredScene() },
                        endSceneEnabled = masterReachable,
                        commandsAllowed = commandsAllowed,
                        pumpNeedsSetup = pumpNeedsSetup && !uiState.pumpSuspended,
                        pumpSuspended = uiState.pumpSuspended,
                        pumpEndTimeMillis = uiState.pumpEndTimeMillis,
                        reservoirUnits = uiState.reservoirUnits,
                        onBgSourceClick = { onNavigate(mainViewModel.bgSourceNavigationRequest()) },
                        notificationsFlow = notificationsFlow,
                        onDismissNotification = onDismissNotification,
                        onNotificationActionClick = onNotificationActionClick,
                        autoShowNotificationSheet = autoShowNotificationSheet,
                        onAutoShowConsumed = onAutoShowConsumed,
                        bolusStateFlow = bolusStateFlow,
                        onStopBolus = onStopBolus,
                        timeInRangeTodayFlow = mainViewModel.timeInRangeToday,
                        formatDuration = mainViewModel::formatDuration,
                        sensorInfo = sensorInfo,
                        loadSensorInfo = mainViewModel::loadSensorInfo
                    )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(MaterialTheme.colorScheme.surface)
                )

                if (previewMode && !chromeVisible) {
                    val showControls = stringResource(UiStrings.a11y_show_controls)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = interactionSource, indication = null) { chromeVisible = true }
                            .semantics { contentDescription = showControls }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        val navigate: (ElementType) -> Unit = { element ->
            showAddSheet = false
            onNavigate(NavigationRequest.Element(element))
        }
        trioUi.addActionsSheet(
            onDismiss = { showAddSheet = false },
            onBolusClick = { navigate(ElementType.INSULIN) },
            onCarbsClick = { navigate(ElementType.CARBS) },
            onWizardClick = { navigate(ElementType.BOLUS_WIZARD) }
        )
    }
}
