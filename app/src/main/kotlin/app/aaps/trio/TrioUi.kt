package app.aaps.trio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.compose.overview.TrioOverviewModel

interface TrioUi {

    @Composable
    fun topBar(
        title: String,
        modifier: Modifier
    )

    @Composable
    fun bottomBar(
        selectedTab: TrioNavTab,
        onTabSelected: (TrioNavTab) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier
    )

    @Composable
    fun addActionsSheet(
        onDismiss: () -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit
    )

    @Composable
    fun overview(model: TrioOverviewModel)

    @Composable
    fun tabScaffold(
        selectedTab: TrioNavTab,
        title: String,
        onTabSelected: (TrioNavTab) -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit,
        showTopBar: Boolean,
        topBarActions: @Composable RowScope.() -> Unit,
        content: @Composable (PaddingValues) -> Unit
    )
}
