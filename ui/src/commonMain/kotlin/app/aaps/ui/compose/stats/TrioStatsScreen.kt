package app.aaps.ui.compose.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.ceil

private val trioStatsRanges = listOf(
    TrioStatsRange.TODAY,
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
                    item {
                        TrioGlucoseProfileCard(
                            data = data,
                            lowMgdl = viewModel.trioLowMgdl,
                            highMgdl = viewModel.trioHighMgdl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrioGlucoseProfileCard(
    data: TrioStatsData,
    lowMgdl: Double,
    highMgdl: Double
) {
    val percentiles = data.hourlyPercentiles
    val profileUtil = LocalProfileUtil.current
    val density = LocalDensity.current
    val colors = AapsTheme.generalColors
    val wideBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val narrowBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val medianColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highThresholdColor = MaterialTheme.colorScheme.tertiary
    val tightHighMgdl = 140.0
    val chartMinimumMgdl = 40.0
    val highestValue = maxOf(
        highMgdl,
        percentiles.maxOfOrNull { it.p90Mgdl } ?: highMgdl
    )
    val chartMaximumMgdl = ceil(highestValue / 50.0).coerceAtLeast(2.0) * 50.0
    val chartDescription = stringResource(UiStrings.trio_stats_glucose_percentile_chart)
    val chartHeight = AapsSpacing.bgCircleSize + AapsSpacing.bgCircleSize / 2
    val axisWidth = AapsSpacing.xxLarge + AapsSpacing.extraLarge
    val strokeWidth = with(density) { AapsSpacing.extraSmall.toPx() }
    val gridWidth = strokeWidth / 2f
    val dashEffect = PathEffect.dashPathEffect(
        floatArrayOf(
            with(density) { AapsSpacing.medium.toPx() },
            with(density) { AapsSpacing.small.toPx() }
        )
    )

    TrioStatsCard {
        Text(
            text = stringResource(UiStrings.trio_stats_agp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = profileUtil.units.displayLabel,
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
                    .semantics { contentDescription = chartDescription }
            ) {
                fun yPosition(valueMgdl: Double): Float =
                    size.height * (1f - ((valueMgdl - chartMinimumMgdl) / (chartMaximumMgdl - chartMinimumMgdl)).toFloat())

                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = gridWidth
                    )
                }
                for (hour in 0..21 step 3) {
                    val x = size.width * hour / 23f
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = gridWidth,
                        pathEffect = dashEffect
                    )
                }

                fun drawThreshold(valueMgdl: Double, color: Color) {
                    if (valueMgdl !in chartMinimumMgdl..chartMaximumMgdl) return
                    val y = yPosition(valueMgdl)
                    drawLine(
                        color = color,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                        pathEffect = dashEffect
                    )
                }

                drawThreshold(lowMgdl, colors.bgLow)
                drawThreshold(tightHighMgdl, colors.bgInRange)
                drawThreshold(highMgdl, highThresholdColor)

                if (percentiles.isNotEmpty()) {
                    fun xPosition(hour: Int): Float = size.width * hour / 23f

                    fun drawBand(
                        lower: (TrioHourlyPercentile) -> Double,
                        upper: (TrioHourlyPercentile) -> Double,
                        color: Color
                    ) {
                        val path = Path()
                        percentiles.forEachIndexed { index, point ->
                            val offset = Offset(xPosition(point.hour), yPosition(upper(point)))
                            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                        }
                        percentiles.asReversed().forEach { point ->
                            path.lineTo(xPosition(point.hour), yPosition(lower(point)))
                        }
                        path.close()
                        drawPath(path = path, color = color, style = Fill)
                    }

                    drawBand(
                        lower = TrioHourlyPercentile::p10Mgdl,
                        upper = TrioHourlyPercentile::p90Mgdl,
                        color = wideBandColor
                    )
                    drawBand(
                        lower = TrioHourlyPercentile::p25Mgdl,
                        upper = TrioHourlyPercentile::p75Mgdl,
                        color = narrowBandColor
                    )

                    val medianPath = Path()
                    percentiles.forEachIndexed { index, point ->
                        val offset = Offset(xPosition(point.hour), yPosition(point.medianMgdl))
                        if (index == 0) medianPath.moveTo(offset.x, offset.y) else medianPath.lineTo(offset.x, offset.y)
                    }
                    drawPath(
                        path = medianPath,
                        color = medianColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .width(axisWidth)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                repeat(5) { index ->
                    val valueMgdl = chartMaximumMgdl - (chartMaximumMgdl - chartMinimumMgdl) * index / 4.0
                    Text(
                        text = profileUtil.fromMgdlToStringInUnits(valueMgdl),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (hour in 0..21 step 3) {
                    Text(
                        text = hour.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(axisWidth + AapsSpacing.medium))
        }
        val legendItems = listOf(
            stringResource(UiStrings.trio_stats_percentile_10_90) to wideBandColor,
            stringResource(UiStrings.trio_stats_percentile_25_75) to narrowBandColor,
            stringResource(UiStrings.trio_stats_median) to medianColor,
            profileUtil.fromMgdlToStringWithUnits(lowMgdl) to colors.bgLow,
            profileUtil.fromMgdlToStringWithUnits(tightHighMgdl) to colors.bgInRange,
            profileUtil.fromMgdlToStringWithUnits(highMgdl) to highThresholdColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            legendItems.chunked(3).forEach { columnItems ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    columnItems.forEach { (label, color) ->
                        TrioChartLegendItem(label, color)
                    }
                }
            }
        }

        Text(
            text = stringResource(UiStrings.trio_stats_days_decimal, data.availableDays),
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrioGlycemicOverview(data)
        TrioMetrics(data)
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
    TrioStatsRange.TODAY   -> stringResource(UiStrings.trio_stats_today)
    TrioStatsRange.DAYS_7  -> stringResource(UiStrings.trio_stats_short_days, 7)
    TrioStatsRange.DAYS_30 -> stringResource(UiStrings.trio_stats_short_days, 30)
    TrioStatsRange.DAYS_90 -> stringResource(UiStrings.trio_stats_short_days, 90)
}

@Composable
private fun TrioGlycemicOverview(data: TrioStatsData) {
    var selectedBand by remember(data) { mutableIntStateOf(2) }
    val colors = AapsTheme.generalColors
    val bands = listOf(
        TrioTirBand(stringResource(CoreUiStrings.veryLow), data.tir.veryLow, colors.bgVeryLow),
        TrioTirBand(stringResource(CoreUiStrings.low), data.tir.low, colors.bgLow),
        TrioTirBand(stringResource(CoreUiStrings.in_range), data.tir.inRange, colors.bgInRange),
        TrioTirBand(stringResource(CoreUiStrings.high), data.tir.high, colors.bgHigh),
        TrioTirBand(stringResource(CoreUiStrings.veryHigh), data.tir.veryHigh, colors.bgVeryHigh)
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
private fun TrioMetrics(data: TrioStatsData) {
    val profileUtil = LocalProfileUtil.current
    val metrics = listOf(
        TrioMetric(stringResource(UiStrings.trio_stats_average), profileUtil.fromMgdlToStringWithUnits(data.averageMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_median), profileUtil.fromMgdlToStringWithUnits(data.medianMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_gmi), stringResource(UiStrings.trio_stats_percent, data.gmiPercent)),
        TrioMetric(stringResource(UiStrings.trio_stats_standard_deviation), profileUtil.fromMgdlToStringWithUnits(data.standardDeviationMgdl)),
        TrioMetric(stringResource(UiStrings.trio_stats_cv), stringResource(UiStrings.trio_stats_percent, data.coefficientOfVariation)),
        TrioMetric(stringResource(UiStrings.trio_stats_tight_range), stringResource(UiStrings.trio_stats_percent, data.tightRangePercent))
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
