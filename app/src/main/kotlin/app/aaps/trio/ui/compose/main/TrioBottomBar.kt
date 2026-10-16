package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.compose.main.TrioNavTab
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

@Composable
fun TrioBottomBar(
    selectedTab: TrioNavTab,
    carbsRequired: Int,
    onTabSelected: (TrioNavTab) -> Unit,
    onAddClick: () -> Unit,
    activeSceneCount: Int = 0,
    onScenesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = NavigationBarItemDefaults.colors()
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp
    ) {
        TrioTabItem(
            selected = selectedTab == TrioNavTab.Overview,
            onClick = { onTabSelected(TrioNavTab.Overview) },
            label = stringResource(R.string.trio_tab_home),
            icon = {
                val label = stringResource(R.string.trio_tab_home)
                BadgedBox(
                    badge = {
                        if (carbsRequired > 0) {
                            Badge {
                                Text(
                                    text = stringResource(R.string.trio_carbs_required_badge, carbsRequired),
                                    modifier = Modifier.padding(horizontal = AapsSpacing.small)
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AreaChart,
                        contentDescription = if (carbsRequired > 0) {
                            stringResource(R.string.trio_tab_carbs_required, label, carbsRequired)
                        } else {
                            label
                        }
                    )
                }
            },
            colors = colors
        )
        TrioTabItem(
            selected = selectedTab == TrioNavTab.Treatments,
            onClick = { onTabSelected(TrioNavTab.Treatments) },
            label = stringResource(CoreUiR.string.treatments),
            icon = { Icon(imageVector = Icons.Default.Medication, contentDescription = null) },
            colors = colors
        )
        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.trio_add_action)
            )
        }
        /*
        TrioTabItem(
            selected = selectedTab == TrioNavTab.Adjustments,
            onClick = { onTabSelected(TrioNavTab.Adjustments) },
            label = stringResource(R.string.trio_tab_adjustments),
            icon = { Icon(imageVector = Icons.Default.Tune, contentDescription = null) },
            colors = colors
        )
        */
        val scenesLabel = stringResource(CoreUiR.string.scenes)
        val sceneCountDescription = stringResource(R.string.trio_active_scene_count, activeSceneCount)
        TrioTabItem(
            selected = false,
            onClick = onScenesClick,
            label = scenesLabel,
            modifier = Modifier.semantics { stateDescription = sceneCountDescription },
            icon = {
                BadgedBox(
                    badge = {
                        if (activeSceneCount > 0) {
                            Badge { Text(activeSceneCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = ElementType.SCENE.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = colors
        )
        TrioTabItem(
            selected = selectedTab == TrioNavTab.Settings,
            onClick = { onTabSelected(TrioNavTab.Settings) },
            label = stringResource(CoreUiR.string.settings),
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
            colors = colors
        )
    }
}

@Composable
private fun RowScope.TrioTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit,
    colors: NavigationBarItemColors,
    modifier: Modifier = Modifier
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        label = null,
        modifier = modifier.semantics { contentDescription = label },
        icon = icon,
        colors = colors
    )
}

@Preview(showBackground = true)
@Composable
private fun TrioBottomBarPreview() {
    MaterialTheme {
        TrioBottomBar(
            selectedTab = TrioNavTab.Overview,
            carbsRequired = 23,
            activeSceneCount = 1,
            onTabSelected = {},
            onAddClick = {}
        )
    }
}
