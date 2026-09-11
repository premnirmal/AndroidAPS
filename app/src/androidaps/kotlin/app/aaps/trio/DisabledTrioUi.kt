package app.aaps.trio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.compose.overview.TrioOverviewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<TrioUi>())
class DisabledTrioUi @Inject constructor() : TrioUi {

    @Composable
    override fun topBar(title: String, modifier: Modifier) = unavailable()

    @Composable
    override fun bottomBar(
        selectedTab: TrioNavTab,
        onTabSelected: (TrioNavTab) -> Unit,
        onAddClick: () -> Unit,
        modifier: Modifier
    ) = unavailable()

    @Composable
    override fun addActionsSheet(
        onDismiss: () -> Unit,
        onBolusClick: () -> Unit,
        onCarbsClick: () -> Unit,
        onWizardClick: () -> Unit
    ) = unavailable()

    @Composable
    override fun overview(model: TrioOverviewModel) = unavailable()

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
    ) = unavailable()

    private fun unavailable(): Nothing = error("Trio UI is not available in this app")
}
