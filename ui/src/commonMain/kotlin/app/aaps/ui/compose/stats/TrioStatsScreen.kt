package app.aaps.ui.compose.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import kotlin.math.abs
import kotlinx.datetime.DayOfWeek

private val trioStatsRanges = listOf(
    TrioStatsRange.TODAY,
    TrioStatsRange.HOURS_12,
    TrioStatsRange.DAYS_7,
    TrioStatsRange.DAYS_14,
    TrioStatsRange.DAYS_30,
    TrioStatsRange.DAYS_90,
    TrioStatsRange.ALL
)

@Composable
fun TrioStatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadTrioStats(state.trioRange)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge)
        ) {
            item {
                TrioStatsRangeSelector(
                    selectedRange = state.trioRange,
                    onSelect = viewModel::loadTrioStats
                )
            }

            when {
                state.trioStatsLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AapsSpacing.bgCircleSize),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.trioStatsData == null || state.trioStatsData?.readingCount == 0 -> item {
                    Text(
                        text = stringResource(UiStrings.trio_stats_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> state.trioStatsData?.let { data ->
                    item { TrioGlycemicOverviewCard(data) }
                    item { TrioMetricsCard(data) }
                    data.comparison?.let { comparison ->
                        item { TrioComparisonCard(comparison) }
                    }
                    item {
                        TrioPatternCard(
                            title = stringResource(UiStrings.trio_stats_hourly_patterns),
                            rows = data.hourly
                        )
                    }
                    item {
                        TrioPatternCard(
                            title = stringResource(UiStrings.trio_stats_weekday_patterns),
                            rows = data.weekdays
                        )
                    }
                    item {
                        Text(
                            text = stringResource(UiStrings.trio_stats_day_by_day),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(
                        items = data.daily.asReversed(),
                        key = { it.key }
                    ) { day ->
                        TrioPatternRow(day)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrioStatsRangeSelector(
    selectedRange: TrioStatsRange,
    onSelect: (TrioStatsRange) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        items(trioStatsRanges) { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onSelect(range) },
                label = { Text(range.label()) }
            )
        }
    }
}

@Composable
private fun TrioStatsRange.label(): String = when (this) {
    TrioStatsRange.TODAY    -> stringResource(UiStrings.trio_stats_today)
    TrioStatsRange.HOURS_12 -> stringResource(UiStrings.trio_stats_12_hours)
    TrioStatsRange.DAYS_7   -> stringResource(UiStrings.trio_stats_short_days, 7)
    TrioStatsRange.DAYS_14  -> stringResource(UiStrings.trio_stats_short_days, 14)
    TrioStatsRange.DAYS_30  -> stringResource(UiStrings.trio_stats_short_days, 30)
    TrioStatsRange.DAYS_90  -> stringResource(UiStrings.trio_stats_short_days, 90)
    TrioStatsRange.ALL      -> stringResource(UiStrings.trio_stats_all_history)
}

@Composable
private fun TrioGlycemicOverviewCard(data: TrioStatsData) {
    var selectedBand by remember(data) { mutableIntStateOf(2) }
    val colors = AapsTheme.generalColors
    val bands = listOf(
        TrioTirBand(stringResource(CoreUiStrings.veryLow), data.tir.veryLow, colors.bgVeryLow),
        TrioTirBand(stringResource(CoreUiStrings.low), data.tir.low, colors.bgLow),
        TrioTirBand(stringResource(CoreUiStrings.in_range), data.tir.inRange, colors.bgInRange),
        TrioTirBand(stringResource(CoreUiStrings.high), data.tir.high, colors.bgHigh),
        TrioTirBand(stringResource(CoreUiStrings.veryHigh), data.tir.veryHigh, colors.bgVeryHigh)
    )

    TrioStatsCard {
        Text(
            text = stringResource(UiStrings.trio_stats_overview),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrioTirRing(
                bands = bands,
                selectedBand = selectedBand,
                onSelect = { selectedBand = it }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                bands.forEachIndexed { index, band ->
                    TrioTirBandRow(
                        band = band,
                        selected = selectedBand == index,
                        onClick = { selectedBand = index }
                    )
                }
            }
        }
        Text(
            text = stringResource(
                UiStrings.trio_stats_readings_and_coverage,
                data.readingCount,
                data.coveragePercent
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrioTirRing(
    bands: List<TrioTirBand>,
    selectedBand: Int,
    onSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier.size(AapsSpacing.bgCircleSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = AapsSpacing.bgRingStrokeWidth.toPx()
            val inset = strokeWidth / 2f
            var startAngle = -90f
            bands.forEachIndexed { index, band ->
                val sweep = (band.percentage / 100.0 * 360.0).toFloat()
                drawArc(
                    color = band.color.copy(alpha = if (selectedBand == index) 1f else 0.35f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Text(
            text = stringResource(UiStrings.trio_stats_percent, bands[selectedBand].percentage),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Surface(
            onClick = { onSelect((selectedBand + 1) % bands.size) },
            modifier = Modifier.matchParentSize(),
            color = Color.Transparent
        ) {}
    }
}

@Composable
private fun TrioTirBandRow(
    band: TrioTirBand,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = if (selected) band.color.copy(alpha = 0.16f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Surface(
                modifier = Modifier.size(AapsSpacing.medium),
                shape = RoundedCornerShape(AapsSpacing.medium),
                color = band.color
            ) {}
            Text(
                text = band.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(UiStrings.trio_stats_percent, band.percentage),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrioMetricsCard(data: TrioStatsData) {
    val profileUtil = LocalProfileUtil.current
    val metrics = listOf(
        TrioMetric(stringResource(UiStrings.trio_stats_average), profileUtil.fromMgdlToStringWithUnits(data.averageMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_gmi), stringResource(UiStrings.trio_stats_percent, data.gmiPercent)),
        TrioMetric(stringResource(UiStrings.trio_stats_median), profileUtil.fromMgdlToStringWithUnits(data.medianMgdl)),
        TrioMetric(
            stringResource(UiStrings.trio_stats_iqr),
            stringResource(
                UiStrings.trio_stats_glucose_range,
                profileUtil.fromMgdlToStringWithUnits(data.p25Mgdl),
                profileUtil.fromMgdlToStringWithUnits(data.p75Mgdl)
            )
        ),
        TrioMetric(stringResource(UiStrings.trio_stats_standard_deviation), profileUtil.fromMgdlToStringWithUnits(data.standardDeviationMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_cv), stringResource(UiStrings.trio_stats_percent, data.coefficientOfVariation)),
        TrioMetric(stringResource(UiStrings.trio_stats_gvi), stringResource(UiStrings.trio_stats_decimal, data.gvi)),
        TrioMetric(
            stringResource(UiStrings.trio_stats_psg),
            stringResource(UiStrings.trio_stats_psg_value, data.psgConfidencePercent, data.psgTrendPercent)
        ),
        TrioMetric(stringResource(UiStrings.trio_stats_tight_range), stringResource(UiStrings.trio_stats_percent, data.tightRangePercent)),
        TrioMetric(stringResource(UiStrings.trio_stats_dawn_rise), profileUtil.fromMgdlToStringWithUnits(data.dawnRiseMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_mage), profileUtil.fromMgdlToStringWithUnits(data.mageMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_modd), profileUtil.fromMgdlToStringWithUnits(data.moddMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_best_streak), stringResource(UiStrings.trio_stats_streak_days, data.bestStreakDays)),
        TrioMetric(stringResource(UiStrings.trio_stats_minimum), profileUtil.fromMgdlToStringWithUnits(data.minimumMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_maximum), profileUtil.fromMgdlToStringWithUnits(data.maximumMgdl))
    )

    TrioStatsCard {
        Text(
            text = stringResource(UiStrings.trio_stats_metrics),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.Top
        ) {
            listOf(
                metrics.filterIndexed { index, _ -> index % 2 == 0 },
                metrics.filterIndexed { index, _ -> index % 2 == 1 }
            ).forEach { columnMetrics ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    columnMetrics.forEach { metric ->
                        TrioMetricTile(metric, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun TrioMetricTile(metric: TrioMetric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
        ) {
            Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(metric.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrioComparisonCard(comparison: TrioStatsComparison) {
    TrioStatsCard {
        Text(
            text = stringResource(UiStrings.trio_stats_previous_period),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        ComparisonRow(stringResource(CoreUiStrings.in_range), comparison.tirDelta, isPercent = true)
        ComparisonRow(stringResource(UiStrings.trio_stats_average), comparison.averageDeltaMgdl, isPercent = false)
        ComparisonRow(stringResource(UiStrings.trio_stats_cv), comparison.cvDelta, isPercent = true)
    }
}

@Composable
private fun ComparisonRow(label: String, delta: Double, isPercent: Boolean) {
    val profileUtil = LocalProfileUtil.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            text = if (isPercent) {
                stringResource(UiStrings.trio_stats_signed_percent, delta)
            } else {
                stringResource(
                    UiStrings.trio_stats_signed_value,
                    if (delta >= 0.0) "+" else "-",
                    profileUtil.fromMgdlToStringWithUnits(abs(delta))
                )
            },
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrioPatternCard(title: String, rows: List<TrioPatternRow>) {
    TrioStatsCard {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        rows.forEach { row -> TrioPatternRow(row) }
    }
}

@Composable
private fun TrioPatternRow(row: TrioPatternRow) {
    val profileUtil = LocalProfileUtil.current
    val label = row.dayOfWeek?.label() ?: row.label
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        UiStrings.trio_stats_pattern_value,
                        profileUtil.fromMgdlToStringWithUnits(row.averageMgdl),
                        row.tir.inRange
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TrioTirStackedBar(row.tir)
        }
    }
}

@Composable
private fun DayOfWeek.label(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY    -> CoreUiStrings.weekday_monday_short
        DayOfWeek.TUESDAY   -> CoreUiStrings.weekday_tuesday_short
        DayOfWeek.WEDNESDAY -> CoreUiStrings.weekday_wednesday_short
        DayOfWeek.THURSDAY  -> CoreUiStrings.weekday_thursday_short
        DayOfWeek.FRIDAY    -> CoreUiStrings.weekday_friday_short
        DayOfWeek.SATURDAY  -> CoreUiStrings.weekday_saturday_short
        DayOfWeek.SUNDAY    -> CoreUiStrings.weekday_sunday_short
    }
)

@Composable
private fun TrioTirStackedBar(tir: TrioTirBreakdown) {
    val colors = AapsTheme.generalColors
    val segments = listOf(
        tir.veryLow to colors.bgVeryLow,
        tir.low to colors.bgLow,
        tir.inRange to colors.bgInRange,
        tir.high to colors.bgHigh,
        tir.veryHigh to colors.bgVeryHigh
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(AapsSpacing.small)
    ) {
        var x = 0f
        segments.forEach { (percentage, color) ->
            val width = size.width * (percentage / 100.0).toFloat()
            drawRect(color = color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}

@Composable
private fun TrioStatsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = AapsSpacing.xxLarge,
            topEnd = AapsSpacing.medium,
            bottomEnd = AapsSpacing.xxLarge,
            bottomStart = AapsSpacing.medium
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = AapsSpacing.extraSmall
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            content = content
        )
    }
}

private data class TrioTirBand(
    val label: String,
    val percentage: Double,
    val color: Color
)

private data class TrioMetric(
    val label: String,
    val value: String
)
