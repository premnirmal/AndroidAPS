package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.trio.TrioUi
import app.aaps.trio.ui.compose.overview.TrioOverviewScreen
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.compose.overview.TrioOverviewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<TrioUi>())
class TrioUiImpl @Inject constructor() : TrioUi {

    @Composable
    override fun topBar(title: String, modifier: Modifier) {
        TrioTopBar(title = title, modifier = modifier)
    }

    @Composable
    override fun bottomBar(
        selectedTab: TrioNavTab,
        onTabSelected: (TrioNavTab) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier
    ) {
        TrioBottomBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            onAddClick = onAddClick,
            modifier = modifier
        )
    }

    @Composable
    override fun addActionsSheet(
        onDismiss: () -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit
    ) {
        TrioAddActionsSheet(
            onDismiss = onDismiss,
            onBolusClick = onBolusClick,
            onCarbsClick = onCarbsClick,
            onWizardClick = onWizardClick
        )
    }

    @Composable
    override fun overview(model: TrioOverviewModel) {
        TrioOverviewScreen(
            profileName = model.profileName,
            isProfileModified = model.isProfileModified,
            profileProgress = model.profileProgress,
            profileSceneManaged = model.profileSceneManaged,
            tempTargetText = model.tempTargetText,
            tempTargetState = model.tempTargetState,
            tempTargetProgress = model.tempTargetProgress,
            tempTargetReason = model.tempTargetReason,
            tempTargetSceneManaged = model.tempTargetSceneManaged,
            runningMode = model.runningMode,
            runningModeText = model.runningModeText,
            runningModeRemaining = model.runningModeRemaining,
            runningModeProgress = model.runningModeProgress,
            runningModeSceneManaged = model.runningModeSceneManaged,
            smbEnabled = model.smbEnabled,
            isSimpleMode = model.isSimpleMode,
            tbrState = model.tbrState,
            graphViewModel = model.graphViewModel,
            chipsViewModel = model.chipsViewModel,
            onNavigate = model.onNavigate,
            onTbrChipClick = model.onTbrChipClick,
            onIobChipClick = model.onIobChipClick,
            paddingValues = model.paddingValues,
            activeSceneState = model.activeSceneState,
            sceneExpired = model.sceneExpired,
            onEndScene = model.onEndScene,
            onDismissScene = model.onDismissScene,
            endSceneEnabled = model.endSceneEnabled,
            commandsAllowed = model.commandsAllowed,
            pumpNeedsSetup = model.pumpNeedsSetup,
            onBgSourceClick = model.onBgSourceClick,
            notificationCount = model.notificationCount,
            highestNotificationLevel = model.highestNotificationLevel,
            onNotificationClick = model.onNotificationClick,
            bolusState = model.bolusState,
            onStopBolus = model.onStopBolus,
            timeInRangeTodayPercent = model.timeInRangeTodayPercent,
            formatDuration = model.formatDuration
        )
    }

    @Composable
    override fun tabScaffold(
        selectedTab: TrioNavTab,
        title: String,
        onTabSelected: (TrioNavTab) -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit,
        showTopBar: Boolean,
        topBarActions: @Composable RowScope.() -> Unit,
        content: @Composable (PaddingValues) -> Unit
    ) {
        TrioTabScaffold(
            selectedTab = selectedTab,
            title = title,
            onTabSelected = onTabSelected,
            onBolusClick = onBolusClick,
            onCarbsClick = onCarbsClick,
            onWizardClick = onWizardClick,
            showTopBar = showTopBar,
            topBarActions = topBarActions,
            content = content
        )
    }
}
