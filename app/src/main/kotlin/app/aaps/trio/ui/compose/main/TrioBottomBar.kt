package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.ui.R
import app.aaps.core.ui.R as CoreUiR

private val FAB_CUTOUT_WIDTH = AapsSpacing.xxLarge * 3
private val FAB_VERTICAL_OFFSET = AapsSpacing.medium - AapsSpacing.extraSmall

@Composable
fun TrioBottomBar(
    selectedTab: TrioNavTab,
    onTabSelected: (TrioNavTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(modifier = modifier) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            TrioTabItem(
                selected = selectedTab == TrioNavTab.Overview,
                onClick = { onTabSelected(TrioNavTab.Overview) },
                label = stringResource(R.string.trio_tab_home),
                icon = { Icon(imageVector = Icons.Default.AreaChart, contentDescription = null) },
                colors = colors
            )
            TrioTabItem(
                selected = selectedTab == TrioNavTab.Treatments,
                onClick = { onTabSelected(TrioNavTab.Treatments) },
                label = stringResource(CoreUiR.string.treatments),
                icon = { Icon(imageVector = Icons.Default.Medication, contentDescription = null) },
                colors = colors
            )
            Spacer(modifier = Modifier.width(FAB_CUTOUT_WIDTH))
            TrioTabItem(
                selected = selectedTab == TrioNavTab.Adjustments,
                onClick = { onTabSelected(TrioNavTab.Adjustments) },
                label = stringResource(R.string.trio_tab_adjustments),
                icon = { Icon(imageVector = Icons.Default.Tune, contentDescription = null) },
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

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = FAB_VERTICAL_OFFSET)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.trio_add_action)
            )
        }

    }
}

@Composable
private fun RowScope.TrioTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit,
    colors: NavigationBarItemColors
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        label = null,
        icon = icon,
        colors = colors
    )
}

@Preview(showBackground = true)
@Composable
private fun TrioBottomBarPreview() {
    AapsTheme {
        TrioBottomBar(
            selectedTab = TrioNavTab.Overview,
            onTabSelected = {},
            onAddClick = {}
        )
    }
}
