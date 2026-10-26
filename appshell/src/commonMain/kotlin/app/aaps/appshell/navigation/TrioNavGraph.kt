package app.aaps.appshell.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.main.MainStrings
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.history.HistoryScreen
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.compose.main.VersionOverlay
import app.aaps.ui.compose.preferences.AllPreferencesScreen
import app.aaps.ui.compose.stats.StatsScreen
import app.aaps.ui.compose.stats.TrioStatsScreen
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementScreen
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import app.aaps.ui.compose.treatments.TreatmentsScreen
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import app.aaps.ui.search.BuiltInSearchables

internal fun NavGraphBuilder.trioNavGraph(
    navController: NavHostController,
    treatmentsViewModel: TreatmentsViewModel,
    tempTargetManagementViewModel: TempTargetManagementViewModel,
    statsViewModel: StatsViewModel,
    activePlugin: ActivePlugin,
    rh: TextResolver,
    builtInSearchables: BuiltInSearchables,
    configBuilder: ConfigBuilder,
    requestEditModeAuthorization: (() -> Unit) -> Unit,
    onNavigateToTrioTab: (TrioNavTab) -> Unit,
    trioTabScaffold: @Composable (
        TrioNavTab,
        String,
        Boolean,
        @Composable RowScope.() -> Unit,
        @Composable (PaddingValues) -> Unit
    ) -> Unit,
    aboutDialog: (@Composable (() -> Unit) -> Unit)?,
    onManageClick: (() -> Unit)?,
    onMaintenanceClick: (() -> Unit)?
) {
    composable(AppRoute.TrioTreatmentList.route) {
        trioTabScaffold(
            TrioNavTab.Treatments,
            stringResource(CoreUiStrings.treatments),
            false,
            {}
        ) { paddingValues ->
            TreatmentsScreen(
                viewModel = treatmentsViewModel,
                onNavigateBack = { onNavigateToTrioTab(TrioNavTab.Overview) },
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
            )
        }
    }

    composable(AppRoute.TrioTreatments.route) {
        trioTabScaffold(
            TrioNavTab.Adjustments,
            stringResource(UiStrings.trio_tab_adjustments),
            false,
            {}
        ) { paddingValues ->
            TempTargetManagementScreen(
                viewModel = tempTargetManagementViewModel,
                initialMode = ScreenMode.EDIT,
                onNavigateBack = { onNavigateToTrioTab(TrioNavTab.Overview) },
                onRequestEditMode = {
                    requestEditModeAuthorization { tempTargetManagementViewModel.setScreenMode(ScreenMode.EDIT) }
                },
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
            )
        }
    }

    composable(AppRoute.TrioStats.route) {
        TrioStatsScreen(
            viewModel = statsViewModel,
            onNavigateBack = { navController.safePopBackStack() }
        )
    }

    composable(AppRoute.TrioHistory.route) {
        trioTabScaffold(
            TrioNavTab.Adjustments,
            stringResource(MainStrings.nav_history_browser),
            true,
            {}
        ) { paddingValues ->
            HistoryScreen(
                title = stringResource(MainStrings.nav_history_browser),
                onNavigateBack = { onNavigateToTrioTab(TrioNavTab.Overview) },
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
                showTopBar = false
            )
        }
    }

    composable(AppRoute.TrioSettings.route) {
        var showAboutDialog by rememberSaveable { mutableStateOf(false) }
        trioTabScaffold(
            TrioNavTab.Settings,
            stringResource(CoreUiStrings.settings),
            true,
            { VersionOverlay() }
        ) { paddingValues ->
            AllPreferencesScreen(
                activePlugin = activePlugin,
                rh = rh,
                builtInSearchables = builtInSearchables,
                configBuilder = configBuilder,
                onBackClick = { onNavigateToTrioTab(TrioNavTab.Overview) },
                modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
                showTopBar = false,
                showSimpleModeHiddenPreferences = true,
                onConfigurationClick = { navController.navigate(AppRoute.Configuration.route) },
                onManageClick = onManageClick,
                onMaintenanceClick = onMaintenanceClick,
                onAboutClick = aboutDialog?.let { { showAboutDialog = true } }
            )
        }
        if (showAboutDialog) {
            aboutDialog?.invoke { showAboutDialog = false }
        }
    }
}

@Composable
internal fun AppStatistics(
    isTrio: Boolean,
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit
) {
    if (isTrio) {
        TrioStatsScreen(viewModel = viewModel, onNavigateBack = onNavigateBack)
    } else {
        StatsScreen(viewModel = viewModel, onNavigateBack = onNavigateBack)
    }
}
