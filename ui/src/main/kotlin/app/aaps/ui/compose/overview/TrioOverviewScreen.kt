package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.icons.Pump
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.TbrChip
import app.aaps.ui.compose.overview.chips.TempTargetChip
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.scenes.ActiveSceneBanner
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrioOverviewScreen(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    tbrState: TbrState,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    modifier: Modifier = Modifier
) {
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val predictedText = predictions.lastOrNull()?.value?.let { value ->
        if (value >= 40.0) value.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.1f", value)
    } ?: stringResource(app.aaps.core.ui.R.string.value_unavailable_short)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val chartHeight = maxHeight * 0.48f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            ActiveSceneBanner(
                activeState = activeSceneState,
                expired = sceneExpired,
                onEndClick = onEndScene,
                onDismiss = onDismissScene,
                endEnabled = endSceneEnabled,
                formatDuration = formatDuration
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PumpEntryPoint(
                    onClick = { onNavigate(NavigationRequest.Element(ElementType.PUMP)) },
                    modifier = Modifier.weight(1f)
                )
                BgInfoSection(
                    bgInfo = bgInfoState.bgInfo,
                    timeAgoText = bgInfoState.timeAgoText,
                    modifier = Modifier.weight(1f)
                )
                LoopMetricPanel(
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    smbEnabled = smbEnabled,
                    iobUiState = iobUiState,
                    cobUiState = cobUiState,
                    sensitivityUiState = sensitivityUiState,
                    predictedText = predictedText,
                    commandsAllowed = commandsAllowed,
                    onNavigate = onNavigate,
                    onIobChipClick = onIobChipClick,
                    modifier = Modifier.weight(1f)
                )
            }

            GraphsSection(
                graphViewModel = graphViewModel,
                isSimpleMode = isSimpleMode,
                mainChartOnly = true,
                mainChartHeight = chartHeight,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileChip(
                    profileName = profileName.ifEmpty { stringResource(app.aaps.core.ui.R.string.no_profile_set) },
                    isModified = isProfileModified,
                    progress = profileProgress,
                    onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
                    sceneManaged = profileSceneManaged,
                    isNoProfile = profileName.isEmpty(),
                    modifier = Modifier.weight(1f)
                )
                if (tempTargetText.isNotEmpty()) {
                    TempTargetChip(
                        targetText = tempTargetText,
                        state = tempTargetState,
                        progress = tempTargetProgress,
                        reason = tempTargetReason,
                        onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) },
                        sceneManaged = tempTargetSceneManaged,
                        enabled = commandsAllowed,
                        modifier = Modifier.weight(1f)
                    )
                }
                TbrChip(
                    state = tbrState,
                    onClick = onTbrChipClick
                )
            }
        }
    }
}

@Composable
private fun PumpEntryPoint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = ElementType.PUMP.color().copy(alpha = 0.16f),
        modifier = modifier.widthIn(min = AapsSpacing.bgCircleSize)
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Icon(
                imageVector = Pump,
                contentDescription = null,
                tint = ElementType.PUMP.color()
            )
            Text(
                text = stringResource(app.aaps.core.ui.R.string.pump),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LoopMetricPanel(
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean,
    smbEnabled: Boolean,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    predictedText: String,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onIobChipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        if (runningModeText.isNotEmpty()) {
            RunningModeChip(
                mode = runningMode,
                text = runningModeText,
                progress = runningModeProgress,
                remaining = runningModeRemaining,
                sceneManaged = runningModeSceneManaged,
                smbEnabled = smbEnabled,
                enabled = commandsAllowed,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
            )
        }
        MetricRow(
            label = stringResource(app.aaps.core.ui.R.string.iob),
            value = iobUiState.text,
            onClick = onIobChipClick
        )
        MetricRow(
            label = stringResource(app.aaps.core.ui.R.string.cob),
            value = cobUiState.text
        )
        MetricRow(
            label = stringResource(app.aaps.core.ui.R.string.predictions_shortname),
            value = predictedText
        )
        if (sensitivityUiState.asText.isNotEmpty()) {
            MetricRow(
                label = stringResource(R.string.trio_metric_sensitivity),
                value = sensitivityUiState.asText
            )
        }
        if (sensitivityUiState.isfFrom.isNotEmpty()) {
            MetricRow(
                label = stringResource(R.string.trio_metric_isf),
                value = "${sensitivityUiState.isfFrom} → ${sensitivityUiState.isfTo}"
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        rowContent()
    }
}
