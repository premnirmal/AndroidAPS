package app.aaps.ui.compose.main

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.aboutDialog.AboutAlertDialog
import app.aaps.ui.compose.aboutDialog.AboutDialogData
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceDialogs
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.TrioOverviewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchToolbar
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.scenesSheet.ScenesBottomSheet
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentBottomSheet
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    aboutDialogData: AboutDialogData?,
    maintenanceViewModel: MaintenanceViewModel,
    onNavigate: (NavigationRequest) -> Unit,
    onTrioTabSelected: (TrioNavTab) -> Unit = {},
    trioSelectedTab: TrioNavTab = TrioNavTab.Overview,
    trioBottomBar: @Composable (
        selectedTab: TrioNavTab,
        carbsRequired: Int,
        onTabSelected: (TrioNavTab) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier
    ) -> Unit = { _, _, _, _, _ -> },
    trioAddActionsSheet: @Composable (
        onDismiss: () -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    trioOverview: @Composable (TrioOverviewModel) -> Unit = {},
    onAboutDialogDismiss: () -> Unit,
    /** Null hides the button - only Android has the problem it links to. */
    onOpenBatteryHelp: (() -> Unit)?,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Notifications
    notificationsFlow: StateFlow<List<AapsNotification>>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    pumpSetupPlugin: PluginBase? = null,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    bolusStateFlow: StateFlow<BolusProgressState?>,
    onStopBolus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    var showTrioAddSheet by rememberSaveable { mutableStateOf(false) }
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        mainViewModel.refreshOverviewState()
    }

    val mainContent: @Composable () -> Unit = {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val previewMode = maxHeight < PREVIEW_MODE_MIN_HEIGHT
            var chromeVisible by remember { mutableStateOf(false) }
            val showChrome = !previewMode || chromeVisible
            val interactionSource = remember { MutableInteractionSource() }

            // Auto-hide chrome after timeout, reset when leaving preview mode
            LaunchedEffect(chromeVisible, previewMode) {
                if (!previewMode) {
                    chromeVisible = false
                    return@LaunchedEffect
                }
                if (chromeVisible) {
                    delay(AUTO_HIDE_DELAY_MS)
                    chromeVisible = false
                }
            }

            // topBar/bottomBar are intentionally absent — chrome is rendered as
            // overlays inside the content (see AnimatedVisibility blocks below) so
            // it can hide in preview mode without reflowing layout. The status-bar
            // and navigation-bar protection scrims rely on this: they read raw
            // WindowInsets.statusBars / navigationBars, which would be consumed
            // (returning zero height) if those Scaffold slots were populated.
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        trioBottomBar(
                            trioSelectedTab,
                            cobUiState.carbsReq,
                            { tab -> onTrioTabSelected(tab) },
                            { showTrioAddSheet = true },
                            Modifier
                        )
                    }
                }
            ) { scaffoldPadding ->
                val contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding())

                val activeSceneState by mainViewModel.activeSceneState.collectAsStateWithLifecycle()
                val sceneExpired by mainViewModel.sceneExpired.collectAsStateWithLifecycle()
                val masterReachable by mainViewModel.masterReachable.collectAsStateWithLifecycle()
                // Stable pairing signal — hides the mutating nav buttons on an unpaired client.
                val masterOrPairedClient by mainViewModel.masterOrPairedClient.collectAsStateWithLifecycle()
                // (Probe-while-offline is now global — see ComposeMainActivity. This screen still reads
                // masterReachable for its own gating.)
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content
                    OverviewScreen(
                        profileName = uiState.profileName,
                        profilePsId = uiState.profilePsId,
                        isProfileModified = uiState.isProfileModified,
                        profileProgress = uiState.profileProgress,
                        profilePercentage = uiState.profilePercentage,
                        profileTargetRangeText = uiState.profileTargetRangeText,
                        tempTargetText = uiState.tempTargetText,
                        tempTargetState = uiState.tempTargetState,
                        tempTargetProgress = uiState.tempTargetProgress,
                        tempTargetReason = uiState.tempTargetReason,
                        tempTargetRecordId = uiState.tempTargetRecordId,
                        runningMode = uiState.runningMode,
                        runningModeText = uiState.runningModeText,
                        runningModeRemaining = uiState.runningModeRemaining,
                        runningModeProgress = uiState.runningModeProgress,
                        runningModeRecordId = uiState.runningModeRecordId,
                        lastLoopAgeMillis = uiState.lastLoopAgeMillis,
                        tbrState = uiState.tbrState,
                        smbEnabled = uiState.smbEnabled,
                        calcProgressFlow = mainViewModel.calcProgressFlow,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        onNavigate = onNavigate,
                        onTbrChipClick = mainViewModel::showTbrInfo,
                        onIobChipClick = chipsViewModel::showIobInfo,
                        notificationsFlow = notificationsFlow,
                        onDismissNotification = onDismissNotification,
                        onNotificationActionClick = onNotificationActionClick,
                        autoShowNotificationSheet = autoShowNotificationSheet,
                        onAutoShowConsumed = onAutoShowConsumed,
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        onEndScene = { mainViewModel.requestSceneDeactivation() },
                        onDismissScene = { mainViewModel.dismissExpiredScene() },
                        endSceneEnabled = masterReachable,
                        commandsAllowed = masterOrPairedClient,
                        formatDuration = mainViewModel::formatDuration,
                        paddingValues = contentPadding,
                        bolusStateFlow = bolusStateFlow,
                        onStopBolus = onStopBolus,
                        timeInRangeTodayPercentFlow = mainViewModel.timeInRangeTodayPercent,
                        trioOverview = trioOverview,
                        pumpNeedsSetup = pumpSetupPlugin != null,
                        pumpEndTimeMillis = uiState.pumpEndTimeMillis,
                        reservoirUnits = uiState.reservoirUnits,
                        onBgSourceClick = {
                            onNavigate(mainViewModel.bgSourceNavigationRequest())
                        }
                    )

                    // Navigation bar protection scrim
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(MaterialTheme.colorScheme.surface)
                    )

                    // Tap overlay to restore chrome in preview mode (only when hidden)
                    if (previewMode && !chromeVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { chromeVisible = true }
                        )
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) { mainContent() }

    if (showTrioAddSheet) {
        trioAddActionsSheet(
            { showTrioAddSheet = false },
            {
                showTrioAddSheet = false
                onNavigate(NavigationRequest.Element(ElementType.INSULIN))
            },
            {
                showTrioAddSheet = false
                onNavigate(NavigationRequest.Element(ElementType.CARBS))
            },
            {
                showTrioAddSheet = false
                onNavigate(NavigationRequest.Element(ElementType.BOLUS_WIZARD))
            }
        )
    }

    // Shared confirmation dialog (automation actions, TT presets, scene end — from toolbar or
    // bottom sheets). When the confirmation carries a secondary action (e.g., scene chain skip),
    // render a 3-button dialog; otherwise the standard 2-button OK/Cancel.
    val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
    actionConfirmation?.let { confirmation ->
        val secondaryAction = confirmation.secondaryAction
        val secondaryLabel = confirmation.secondaryLabel
        if (secondaryAction != null && secondaryLabel != null) {
            ThreeButtonDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                primaryLabel = confirmation.confirmLabel ?: stringResource(CoreUiStrings.ok),
                onPrimary = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                secondaryLabel = secondaryLabel,
                onSecondary = { mainViewModel.executeConfirmableAction(secondaryAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        } else {
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }
    }

    // Maintenance dialogs (sheets, confirmations, export chain)
    MaintenanceDialogs(
        maintenanceViewModel = maintenanceViewModel,
        showMaintenanceSheet = uiState.showMaintenanceSheet,
        onMaintenanceSheetDismiss = onMaintenanceSheetDismiss,
        onDirectoryClick = onDirectoryClick,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onSnackbar = { snackbarHostState.showSnackbar(it) }
    )

    // About dialog
    if (uiState.showAboutDialog && aboutDialogData != null) {
        AboutAlertDialog(
            data = aboutDialogData,
            onDismiss = onAboutDialogDismiss,
            onOpenBatteryHelp = onOpenBatteryHelp
        )
    }
}

private val PREVIEW_MODE_MIN_HEIGHT: Dp = 500.dp
private const val AUTO_HIDE_DELAY_MS = 3000L
