package app.aaps.ui.compose.maintenance

import androidx.compose.runtime.Composable

/**
 * Full-screen maintenance page.
 *
 * The maintenance actions and dialogs stay shared with the maintenance bottom sheet.
 */
@Composable
fun MaintenanceScreen(
    maintenanceViewModel: MaintenanceViewModel,
    onDirectoryClick: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onSnackbar: suspend (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    MaintenanceDialogs(
        maintenanceViewModel = maintenanceViewModel,
        showMaintenanceSheet = false,
        onMaintenanceSheetDismiss = onNavigateBack,
        onDirectoryClick = onDirectoryClick,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onSnackbar = onSnackbar,
        showMaintenanceScreen = true,
        onMaintenanceScreenBack = onNavigateBack
    )
}
