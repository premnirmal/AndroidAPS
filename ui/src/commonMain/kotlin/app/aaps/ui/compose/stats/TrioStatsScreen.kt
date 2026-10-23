package app.aaps.ui.compose.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.viewmodels.StatsUiState
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val trioStatsRanges = listOf(
    TrioStatsRange.TODAY,
    TrioStatsRange.HOURS_24,
    TrioStatsRange.DAYS_7,
    TrioStatsRange.DAYS_30,
    TrioStatsRange.DAYS_90
)

@Composable
fun TrioStatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadAgpStats(state.agpRange)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiStrings.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
        ) {
            TrioStatsSectionSelector(
                selectedSection = state.trioStatsSection,
                onSelect = viewModel::selectTrioStatsSection
            )
            when (state.trioStatsSection) {
                TrioStatsSection.GLUCOSE -> TrioGlucoseStatsContent(
                    state = state,
                    viewModel = viewModel
                )

                TrioStatsSection.INSULIN -> TrioInsulinStatsContent(
                    state = state,
                    viewModel = viewModel
                )

                TrioStatsSection.MEALS -> TrioCarbStatsContent(
                    state = state,
                    viewModel = viewModel
                )
            }
            Spacer(modifier = Modifier.fillMaxWidth().height(8.dp))
        }
    }
}

@Composable
private fun TrioStatsSectionSelector(
    selectedSection: TrioStatsSection,
    onSelect: (TrioStatsSection) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge)
    ) {
        TrioStatsSection.entries.forEachIndexed { index, section ->
            SegmentedButton(
                selected = selectedSection == section,
                onClick = { onSelect(section) },
                shape = SegmentedButtonDefaults.itemShape(index, TrioStatsSection.entries.size),
                label = {
                    Text(
                        when (section) {
                            TrioStatsSection.GLUCOSE -> stringResource(UiStrings.trio_stats_glucose)
                            TrioStatsSection.INSULIN -> stringResource(UiStrings.trio_stats_insulin)
                            TrioStatsSection.MEALS -> stringResource(UiStrings.trio_stats_meals)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun TrioGlucoseStatsContent(
    state: StatsUiState,
    viewModel: StatsViewModel
) {
    AGPStats(
        selectedRange = state.agpRange,
        onSelectRange = viewModel::loadAgpStats,
        data = state.agpStatsData,
        isLoading = state.agpLoading,
        hasError = state.agpError,
        modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge)
    )
}

@Composable
private fun TrioStatsLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().height(AapsSpacing.bgCircleSize),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TrioStatsEmptyState(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge),
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TrioInsulinStatsContent(
    state: StatsUiState,
    viewModel: StatsViewModel
) {
    var chart by rememberSaveable { mutableStateOf(TrioInsulinChart.TOTAL_DAILY_DOSE.ordinal) }
    val selectedChart = TrioInsulinChart.entries[chart]
    LaunchedEffect(Unit) {
        viewModel.loadTrioInsulinStats(state.trioInsulinRange)
    }
    TrioInsulinRangeSelector(
        selectedRange = state.trioInsulinRange,
        onSelect = viewModel::loadTrioInsulinStats
    )
    TrioInsulinChartSelector(
        selectedChart = selectedChart,
        onSelect = { chart = it.ordinal }
    )
    when {
        state.trioInsulinStatsLoading ->
            TrioStatsLoading()

        selectedChart == TrioInsulinChart.TOTAL_DAILY_DOSE &&
            state.trioInsulinStatsData?.tddPoints.isNullOrEmpty() ->
            TrioStatsEmptyState(stringResource(UiStrings.trio_stats_no_tdd_data))

        selectedChart == TrioInsulinChart.BOLUS_DISTRIBUTION &&
            state.trioInsulinStatsData?.bolusPoints.isNullOrEmpty() ->
            TrioStatsEmptyState(stringResource(UiStrings.trio_stats_no_bolus_data))

        else -> state.trioInsulinStatsData?.let { data ->
            TrioInsulinCard(
                modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge),
                data = data,
                state = state,
                chart = selectedChart
            )
        }
    }
}

@Composable
private fun TrioInsulinRangeSelector(
    selectedRange: TrioStatsRange,
    onSelect: (TrioStatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            trioStatsRanges.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onSelect(range) },
                    label = { Text(range.label()) }
                )
            }
        }
    }
}

@Composable
private fun TrioInsulinChartSelector(
    selectedChart: TrioInsulinChart,
    onSelect: (TrioInsulinChart) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
    ) {
        TrioInsulinChart.entries.forEachIndexed { index, chart ->
            SegmentedButton(
                selected = selectedChart == chart,
                onClick = { onSelect(chart) },
                shape = SegmentedButtonDefaults.itemShape(index, TrioInsulinChart.entries.size),
                label = { Text(chart.label()) }
            )
        }
    }
}

@Composable
private fun TrioInsulinChart.label(): String = when (this) {
    TrioInsulinChart.TOTAL_DAILY_DOSE  -> stringResource(UiStrings.trio_stats_total_daily_dose)
    TrioInsulinChart.BOLUS_DISTRIBUTION -> stringResource(UiStrings.trio_stats_bolus_distribution)
}

@Composable
private fun TrioInsulinCard(
    data: TrioInsulinStatsData,
    chart: TrioInsulinChart,
    state: StatsUiState,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    val points = when (chart) {
        TrioInsulinChart.TOTAL_DAILY_DOSE  -> data.tddPoints
        TrioInsulinChart.BOLUS_DISTRIBUTION -> data.bolusPoints
    }
    TrioStatsCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = chart.label(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        TrioInsulinSummary(data, chart)
        TrioInsulinBarChart(
            points = points,
            chart = chart,
            range = state.trioInsulinRange,
            modifier = Modifier.fillMaxWidth()
        )
        val legendItems = when (chart) {
            TrioInsulinChart.TOTAL_DAILY_DOSE -> listOf(
                stringResource(UiStrings.trio_stats_basal) to colors.trioBasal,
                stringResource(UiStrings.trio_stats_bolus) to colors.trioInsulin
            )

            TrioInsulinChart.BOLUS_DISTRIBUTION -> listOf(
                stringResource(UiStrings.trio_stats_manual) to colors.trioManualBolus,
                stringResource(UiStrings.trio_stats_smb) to colors.trioSmb
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            legendItems.forEach { (label, color) ->
                TrioChartLegendItem(label, color)
            }
        }
    }
}

@Composable
private fun TrioInsulinSummary(data: TrioInsulinStatsData, chart: TrioInsulinChart) {
    val values = when (chart) {
        TrioInsulinChart.TOTAL_DAILY_DOSE -> listOf(
            stringResource(UiStrings.trio_stats_average) to stringResource(UiStrings.trio_stats_insulin_units, data.averageTdd),
            stringResource(UiStrings.trio_stats_total) to stringResource(UiStrings.trio_stats_insulin_units, data.totalTdd)
        )

        TrioInsulinChart.BOLUS_DISTRIBUTION -> listOf(
            stringResource(UiStrings.trio_stats_manual) to stringResource(UiStrings.trio_stats_insulin_units, data.averageManualBolus),
            stringResource(UiStrings.trio_stats_smb) to stringResource(UiStrings.trio_stats_insulin_units, data.averageSmbBolus),
            stringResource(UiStrings.trio_stats_total) to stringResource(UiStrings.trio_stats_insulin_units, data.totalBolus)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        values.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrioInsulinBarChart(
    points: List<TrioInsulinPoint>,
    chart: TrioInsulinChart,
    range: TrioStatsRange,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    val basalColor = colors.trioBasal
    val bolusColor = colors.trioInsulin
    val manualBolusColor = colors.trioManualBolus
    val smbColor = colors.trioSmb
    val description = when (chart) {
        TrioInsulinChart.TOTAL_DAILY_DOSE  -> stringResource(UiStrings.trio_stats_tdd_chart)
        TrioInsulinChart.BOLUS_DISTRIBUTION -> stringResource(UiStrings.trio_stats_bolus_chart)
    }
    val showAxis = chart == TrioInsulinChart.BOLUS_DISTRIBUTION
    val maximum = points.maxOfOrNull {
        when (chart) {
            TrioInsulinChart.TOTAL_DAILY_DOSE  -> it.total
            TrioInsulinChart.BOLUS_DISTRIBUTION -> it.manualBolus + it.smbBolus
        }
    }?.coerceAtLeast(1.0) ?: 1.0
    val chartHeight = AapsSpacing.bgCircleSize + AapsSpacing.bgCircleSize / 2
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val gridWidth = with(density) { AapsSpacing.extraSmall.toPx() } / 2f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(chartHeight)
                    .semantics { contentDescription = description }
            ) {
                if (showAxis) {
                    repeat(5) { index ->
                        val y = size.height * index / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), gridWidth)
                    }
                }
                val barWidth = size.width / points.size.coerceAtLeast(1) * 0.7f
                points.forEachIndexed { index, point ->
                    val centerX = size.width * (index + 0.5f) / points.size
                    if (chart == TrioInsulinChart.TOTAL_DAILY_DOSE) {
                        val basalHeight = (point.basal / maximum * size.height).toFloat()
                        val bolusHeight = (point.bolus / maximum * size.height).toFloat()
                        drawRect(
                            color = basalColor,
                            topLeft = Offset(centerX - barWidth / 2, size.height - basalHeight),
                            size = Size(barWidth, basalHeight)
                        )
                        drawRect(
                            color = bolusColor,
                            topLeft = Offset(centerX - barWidth / 2, size.height - basalHeight - bolusHeight),
                            size = Size(barWidth, bolusHeight)
                        )
                    } else {
                        val manualHeight = (point.manualBolus / maximum * size.height).toFloat()
                        val smbHeight = (point.smbBolus / maximum * size.height).toFloat()
                        drawRect(
                            color = manualBolusColor,
                            topLeft = Offset(centerX - barWidth / 2, size.height - manualHeight),
                            size = Size(barWidth, manualHeight)
                        )
                        drawRect(
                            color = smbColor,
                            topLeft = Offset(centerX - barWidth / 2, size.height - manualHeight - smbHeight),
                            size = Size(barWidth, smbHeight)
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trioInsulinPointLabel(points.first().timestamp, range), style = MaterialTheme.typography.labelSmall)
                Text(trioInsulinPointLabel(points.last().timestamp, range), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (showAxis) {
            Column(
                modifier = Modifier.height(chartHeight).clearAndSetSemantics { },
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                repeat(5) { index ->
                    Text(
                        text = stringResource(UiStrings.trio_stats_insulin_units, maximum * (4 - index) / 4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun trioInsulinPointLabel(timestamp: Long, range: TrioStatsRange): String {
    val time = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    return if (range.usesHourlyBuckets) {
        stringResource(UiStrings.trio_stats_time_label, time.hour)
    } else {
        stringResource(UiStrings.trio_stats_date_label, time.monthNumber, time.dayOfMonth)
    }
}

@Composable
private fun TrioCarbStatsContent(
    state: StatsUiState,
    viewModel: StatsViewModel
) {
    LaunchedEffect(Unit) {
        viewModel.loadTrioCarbStats(state.trioCarbRange)
    }
    TrioStatsRangeSelector(
        modifier = Modifier.padding(bottom = AapsSpacing.medium),
        selectedRange = state.trioCarbRange,
        onSelect = viewModel::loadTrioCarbStats
    )
    when {
        state.trioCarbStatsLoading ->
            TrioStatsLoading()

        state.trioCarbStatsData?.points.isNullOrEmpty() ->
            TrioStatsEmptyState(stringResource(UiStrings.trio_stats_no_meal_data))

        else -> TrioCarbCard(
            modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge),
            data = state.trioCarbStatsData,
            range = state.trioCarbRange
        )
    }
}

@Composable
private fun TrioCarbCard(
    data: TrioCarbStatsData,
    range: TrioStatsRange,
    modifier: Modifier = Modifier
) {
    val carbColor = AapsTheme.generalColors.trioCarbs
    TrioStatsCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(UiStrings.trio_stats_total_meals),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        TrioCarbSummary(data)
        Text(
            text = stringResource(UiStrings.trio_stats_carbs_axis),
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrioCarbBarChart(
            points = data.points,
            color = carbColor,
            range = range,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            TrioChartLegendItem(stringResource(UiStrings.trio_stats_carbs), carbColor)
        }
    }
}

@Composable
private fun TrioCarbSummary(data: TrioCarbStatsData) {
    val values = listOf(
        stringResource(UiStrings.trio_stats_average_per_day) to stringResource(UiStrings.trio_stats_carb_grams, data.averagePerDay),
        stringResource(UiStrings.trio_stats_total) to stringResource(UiStrings.trio_stats_carb_grams, data.total),
        stringResource(UiStrings.trio_stats_entries) to data.entryCount.toString()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        values.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrioCarbBarChart(
    points: List<TrioCarbPoint>,
    color: Color,
    range: TrioStatsRange,
    modifier: Modifier = Modifier
) {
    val description = stringResource(UiStrings.trio_stats_carb_chart)
    val maximum = points.maxOfOrNull(TrioCarbPoint::carbs)?.coerceAtLeast(1.0) ?: 1.0
    val chartHeight = AapsSpacing.bgCircleSize + AapsSpacing.bgCircleSize / 2
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val gridWidth = with(density) { AapsSpacing.extraSmall.toPx() } / 2f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(chartHeight)
                    .semantics { contentDescription = description }
            ) {
                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), gridWidth)
                }
                val barWidth = size.width / points.size.coerceAtLeast(1) * 0.7f
                points.forEachIndexed { index, point ->
                    val centerX = size.width * (index + 0.5f) / points.size
                    val barHeight = (point.carbs / maximum * size.height).toFloat()
                    drawRect(
                        color = color,
                        topLeft = Offset(centerX - barWidth / 2, size.height - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trioCarbPointLabel(points.first().timestamp, range), style = MaterialTheme.typography.labelSmall)
                Text(trioCarbPointLabel(points.last().timestamp, range), style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(
            modifier = Modifier.height(chartHeight).clearAndSetSemantics { },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            repeat(5) { index ->
                Text(
                    text = stringResource(UiStrings.trio_stats_carb_grams, maximum * (4 - index) / 4),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun trioCarbPointLabel(timestamp: Long, range: TrioStatsRange): String {
    val time = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    return if (range.usesHourlyBuckets) {
        stringResource(UiStrings.trio_stats_time_label, time.hour)
    } else {
        stringResource(UiStrings.trio_stats_date_label, time.monthNumber, time.dayOfMonth)
    }
}

@Composable
private fun TrioChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(AapsSpacing.large),
            shape = RoundedCornerShape(AapsSpacing.large),
            color = color
        ) {}
        Spacer(modifier = Modifier.width(AapsSpacing.medium))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrioStatsRangeSelector(
    selectedRange: TrioStatsRange,
    onSelect: (TrioStatsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            trioStatsRanges.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onSelect(range) },
                    label = { Text(range.label()) }
                )
            }
        }
    }
}

@Composable
private fun TrioStatsRange.label(): String = when (this) {
    TrioStatsRange.TODAY    -> stringResource(UiStrings.trio_stats_today)
    TrioStatsRange.HOURS_24 -> stringResource(UiStrings.trio_stats_short_hours, 24)
    TrioStatsRange.DAYS_7   -> stringResource(UiStrings.trio_stats_short_days, 7)
    TrioStatsRange.DAYS_30  -> stringResource(UiStrings.trio_stats_short_days, 30)
    TrioStatsRange.DAYS_90  -> stringResource(UiStrings.trio_stats_short_days, 90)
}

@Composable
private fun TrioStatsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
            content = content
        )
    }
}
