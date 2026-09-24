package app.aaps.ui.compose.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.AapsNotification
<<<<<<< HEAD
=======
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationHandle
>>>>>>> origin/dev
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel

/**
 * The overview - the app's home screen - assembled once for every platform.
 *
 * ## Why this exists
 *
 * [MainScreen] draws the overview and has been in commonMain all along, but the hundred and seventy
 * lines that *feed* it lived inline in `ComposeMainActivity`. That is why the shared navigation
 * graph had no home screen to offer: iOS and desktop could reach every settings screen and not the
 * one screen the app is actually about, and both had to open on a scaffolding list instead.
 *
 * Nothing in that assembly was Android. It is plugin state, objectives progress and glucose quality -
 * decisions about what to show, which is exactly the kind of thing worth having in one copy. Moving
 * it here is what lets `appNavGraph` register `AppRoute.Main` for everyone.
 *
 * ## What is still per platform
 *
 * The callbacks at the end, and only those: opening a browser, opening a directory picker, bringing
 * the window to the front, recreating the activity, and quitting after a failed authorization. Each
 * is a sentence of platform code, and each is passed in rather than guessed at.
 *
 * [appName] and [authorizationFailedMessage] are parameters because they live in the app module's
 * own resources - the name differs per flavour - and this module cannot see them.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun OverviewScreen(
    // View models
    mainViewModel: MainViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    // Dependencies
    activePlugin: ActivePlugin,
    config: Config,
    notificationManager: NotificationManager,
    bolusProgressData: BolusProgressData,
    clientControlActionDispatcher: ClientControlActionDispatcher,
    commandQueue: CommandQueue,
    // Text the app module owns
    appName: String,
    authorizationFailedMessage: String,
    // Navigation, which the platform routes because it holds the controller
    onNavigate: (NavigationRequest) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    // Platform actions
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onRecreateActivity: () -> Unit,
    onAuthorizationFailed: () -> Unit,
    // Notification sheet, opened from outside when something important arrives
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit
) {
    val state by mainViewModel.uiState.collectAsStateWithLifecycle()

    // Pump setup button in bottom bar.
    // The three casts here and below are `as?`, not `as`. Each of them runs before the condition
    // that decides whether the badge is drawn at all, so a hard cast makes an implementation that is
    // not a plugin crash the whole overview at compose time - on a platform where the badge would
    // never have been shown. A missing badge is the right failure, not a dead screen.
    val pumpPlugin = activePlugin.activePumpInternal as? PluginBase
    val showPumpSetup = (!activePlugin.activePump.isInitialized() || activePlugin.activePump.isSuspended()) &&
        pumpPlugin != null && pumpPlugin.hasComposeContent()
    val pumpSetupPlugin = if (showPumpSetup) pumpPlugin else null

    // Authorization failed dialog
    if (state.showAuthFailedDialog) {
        OkDialog(
            title = "",
            message = authorizationFailedMessage,
            onDismiss = {
                mainViewModel.setShowAuthFailedDialog(false)
                onAuthorizationFailed()
            }
        )
    }

    MainScreen(
        mainViewModel = mainViewModel,
        uiState = state,
        aboutDialogData = if (state.showAboutDialog) mainViewModel.buildAboutDialogData(appName) else null,
        maintenanceViewModel = maintenanceViewModel,
        onNavigate = onNavigate,
        onAboutDialogDismiss = { mainViewModel.setShowAboutDialog(false) },
        onOpenBatteryHelp = if (mainViewModel.showBatteryHelp) ({ mainViewModel.openBatteryHelp() }) else null,
        onMaintenanceSheetDismiss = { mainViewModel.setShowMaintenanceSheet(false) },
        onDirectoryClick = onDirectoryClick,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        // Notifications
<<<<<<< HEAD
        notificationsFlow = notificationManager.notifications,
        onDismissNotification = { notification -> notificationManager.dismiss(notification.id) },
=======
        notifications = notifications,
        // By handle, not by id: dismiss(id) removes EVERY card carrying that id, so on an allowMultiple
        // notification - patch alerts, automation messages, a failed plugin - dismissing one wiped them all.
        onDismissNotification = { notification -> notificationManager.dismiss(NotificationHandle(notification.instanceKey)) },
>>>>>>> origin/dev
        onNotificationActionClick = onNotificationActionClick,
        autoShowNotificationSheet = autoShowNotificationSheet,
        onAutoShowConsumed = onAutoShowConsumed,
        pumpSetupPlugin = pumpSetupPlugin,
        graphViewModel = graphViewModel,
        chipsViewModel = chipsViewModel,
        bolusStateFlow = bolusProgressData.state,
        onStopBolus = {
            if (config.AAPSCLIENT) {
                clientControlActionDispatcher.stopBolus()
                bolusProgressData.stopPressed()
            } else {
                commandQueue.cancelAllBoluses(null)
            }
        }
    )
}
