package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSystemBarStyleEffect
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.ui.compose.main.TrioNavTab

@Composable
fun TrioTabScaffold(
    selectedTab: TrioNavTab,
    title: String,
    onTabSelected: (TrioNavTab) -> Unit,
    onBolusClick: () -> Unit,
    onCarbsClick: () -> Unit,
    onWizardClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    AapsSystemBarStyleEffect()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (showTopBar) {
                TrioTopBar(
                    title = title,
                    actions = topBarActions
                )
            }
        },
        bottomBar = {
            TrioBottomBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                onAddClick = { showAddSheet = true }
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }

    if (showAddSheet) {
        TrioAddActionsSheet(
            onDismiss = { showAddSheet = false },
            onBolusClick = {
                showAddSheet = false
                onBolusClick()
            },
            onCarbsClick = {
                showAddSheet = false
                onCarbsClick()
            },
            onWizardClick = {
                showAddSheet = false
                onWizardClick()
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrioTabScaffoldPreview() {
    AapsTheme {
        TrioTabScaffold(
            selectedTab = TrioNavTab.Adjustments,
            title = "Adjustments",
            onTabSelected = {},
            onBolusClick = {},
            onCarbsClick = {},
            onWizardClick = {}
        ) { padding ->
            Text(
                text = "Trio Tab Content",
                modifier = Modifier.padding(padding)
            )
        }
    }
}
