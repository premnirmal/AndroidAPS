package app.aaps.ui.compose.configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.preference.SyncBadge
import app.aaps.ui.R as UiR
import app.aaps.ui.plugin.HardwarePumpConfirmation

@Composable
fun ConfigurationScreen(
    categories: List<ConfigCategoryUiModel>,
    visibleTypes: Set<PluginType>? = null,
    hardwarePumpConfirmation: HardwarePumpConfirmation?,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (PluginType) -> Unit,
    onOpenHealthConnect: () -> Unit,
    showHealthConnect: Boolean = false,
    showTopBar: Boolean = true,
    onConfirmHardwarePump: () -> Unit,
    onDismissHardwarePump: () -> Unit,
) {
    val visibleCategories = if (visibleTypes == null) {
        categories
    } else {
        categories.filter { it.type in visibleTypes }
    }

    if (hardwarePumpConfirmation != null) {
        OkCancelDialog(
            title = stringResource(R.string.confirmation),
            message = hardwarePumpConfirmation.message,
            onConfirm = onConfirmHardwarePump,
            onDismiss = onDismissHardwarePump
        )
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                AapsTopAppBar(
                    title = { Text(stringResource(R.string.nav_configuration)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            visibleCategories.forEach { category ->
                item(key = "cat_${category.type}") {
                    CategoryRow(
                        category = category,
                        onClick = { onNavigateToCategory(category.type) }
                    )
                }
            }
            if (showHealthConnect) {
                item(key = "health_connect") {
                    ActionRow(
                        title = stringResource(UiR.string.health_connect),
                        subtitle = stringResource(UiR.string.health_connect_settings_subtitle),
                        onClick = onOpenHealthConnect
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ConfigCategoryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryName = stringResource(category.titleRes)
    val iconPainter = rememberVectorPainter(category.categoryIcon ?: Icons.Default.Settings)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = AapsSpacing.xxLarge,
                top = AapsSpacing.large,
                bottom = AapsSpacing.large,
                end = AapsSpacing.small
            )
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AapsSpacing.xxLarge)
        )
        Spacer(modifier = Modifier.width(AapsSpacing.extraLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SyncBadge(visible = category.synced, modifier = Modifier.padding(end = AapsSpacing.medium))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = AapsSpacing.large)
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = AapsSpacing.xxLarge,
                top = AapsSpacing.large,
                bottom = AapsSpacing.large,
                end = AapsSpacing.small
            )
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AapsSpacing.xxLarge)
        )
        Spacer(modifier = Modifier.width(AapsSpacing.extraLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = AapsSpacing.large)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationScreenPreview() {
    AapsTheme {
        ConfigurationScreen(
            categories = listOf(
                ConfigCategoryUiModel(
                    type = PluginType.GENERAL,
                    titleRes = R.string.configbuilder_general,
                    plugins = emptyList(),
                    isMultiSelect = true,
                    subtitle = "General plugins",
                    categoryIcon = Icons.Default.Settings
                )
            ),
            visibleTypes = setOf(PluginType.GENERAL, PluginType.PUMP, PluginType.BGSOURCE, PluginType.SYNC),
            hardwarePumpConfirmation = null,
            onNavigateBack = {},
            onNavigateToCategory = {},
            onOpenHealthConnect = {},
            showHealthConnect = true,
            showTopBar = true,
            onConfirmHardwarePump = {},
            onDismissHardwarePump = {}
        )
    }
}
