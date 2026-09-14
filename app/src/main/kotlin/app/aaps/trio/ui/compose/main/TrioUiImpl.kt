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
        carbsRequired: Int,
        onTabSelected: (TrioNavTab) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier
    ) {
        TrioBottomBar(
            selectedTab = selectedTab,
            carbsRequired = carbsRequired,
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
        TrioOverviewScreen(model)
    }

    @Composable
    override fun tabScaffold(
        selectedTab: TrioNavTab,
        title: String,
        carbsRequired: Int,
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
            carbsRequired = carbsRequired,
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
