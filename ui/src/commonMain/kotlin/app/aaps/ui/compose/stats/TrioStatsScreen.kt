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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.extensions.round
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.viewmodels.StatsUiState
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import kotlin.math.ceil
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
            }
        }
    }
}

@Composable
private fun TrioStatsSectionSelector(
    selectedSection: TrioStatsSection,
    onSelect: (TrioStatsSection) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
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
    TrioStatsRangeSelector(
        modifier = Modifier.padding(bottom = AapsSpacing.medium),
        selectedRange = state.trioRange,
        onSelect = viewModel::loadTrioStats
    )
    when {
        state.trioStatsLoading ->
            TrioStatsLoading()

        state.trioStatsData == null || state.trioStatsData?.readingCount == 0 ->
            TrioStatsEmptyState(stringResource(UiStrings.trio_stats_no_data))

        else -> state.trioStatsData?.let { data ->
            TrioGlucoseProfileCard(
                modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge),
                data = data,
                lowMgdl = viewModel.trioLowMgdl,
                highMgdl = viewModel.trioHighMgdl,
                glycemicMetricUnits = viewModel.trioGlycemicMetricUnits
            )
        }
    }
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
                chart = selectedChart
            )
        }
    }
}

@Composable
private fun TrioInsulinRangeSelector(
    selectedRange: TrioInsulinRange,
    onSelect: (TrioInsulinRange) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
    ) {
        TrioInsulinRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index, TrioInsulinRange.entries.size),
                label = { Text(range.label()) }
            )
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
private fun TrioInsulinRange.label(): String = when (this) {
    TrioInsulinRange.DAY          -> stringResource(UiStrings.trio_stats_short_day)
    TrioInsulinRange.WEEK         -> stringResource(UiStrings.trio_stats_short_days, 7)
    TrioInsulinRange.MONTH        -> stringResource(UiStrings.trio_stats_short_days, 30)
    TrioInsulinRange.THREE_MONTHS -> stringResource(UiStrings.trio_stats_short_days, 90)
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
    modifier: Modifier = Modifier
) {
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
            modifier = Modifier.fillMaxWidth()
        )
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
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val description = when (chart) {
        TrioInsulinChart.TOTAL_DAILY_DOSE  -> stringResource(UiStrings.trio_stats_tdd_chart)
        TrioInsulinChart.BOLUS_DISTRIBUTION -> stringResource(UiStrings.trio_stats_bolus_chart)
    }
    Canvas(
        modifier = modifier.height(AapsSpacing.bgCircleSize + AapsSpacing.bgCircleSize / 2)
            .semantics { contentDescription = description }
    ) {
        val maximum = points.maxOfOrNull {
            when (chart) {
                TrioInsulinChart.TOTAL_DAILY_DOSE  -> it.total
                TrioInsulinChart.BOLUS_DISTRIBUTION -> it.manualBolus + it.smbBolus
            }
        }?.coerceAtLeast(1.0) ?: 1.0
        val barWidth = size.width / points.size.coerceAtLeast(1) * 0.7f
        points.forEachIndexed { index, point ->
            val centerX = size.width * (index + 0.5f) / points.size
            if (chart == TrioInsulinChart.TOTAL_DAILY_DOSE) {
                val basalHeight = (point.basal / maximum * size.height).toFloat()
                val bolusHeight = (point.bolus / maximum * size.height).toFloat()
                drawRect(
                    color = primary,
                    topLeft = Offset(centerX - barWidth / 2, size.height - basalHeight),
                    size = Size(barWidth, basalHeight)
                )
                drawRect(
                    color = secondary,
                    topLeft = Offset(centerX - barWidth / 2, size.height - basalHeight - bolusHeight),
                    size = Size(barWidth, bolusHeight)
                )
            } else {
                val manualHeight = (point.manualBolus / maximum * size.height).toFloat()
                val smbHeight = (point.smbBolus / maximum * size.height).toFloat()
                drawRect(
                    color = primary,
                    topLeft = Offset(centerX - barWidth / 2, size.height - manualHeight),
                    size = Size(barWidth, manualHeight)
                )
                drawRect(
                    color = secondary,
                    topLeft = Offset(centerX - barWidth / 2, size.height - manualHeight - smbHeight),
                    size = Size(barWidth, smbHeight)
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(trioInsulinPointLabel(points.first().timestamp), style = MaterialTheme.typography.labelSmall)
        Text(trioInsulinPointLabel(points.last().timestamp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun trioInsulinPointLabel(timestamp: Long): String {
    val time = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    return stringResource(UiStrings.trio_stats_time_label, time.monthNumber, time.dayOfMonth, time.hour)
}

@Composable
private fun TrioGlucoseProfileCard(
    data: TrioStatsData,
    lowMgdl: Double,
    highMgdl: Double,
    glycemicMetricUnits: String,
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                modifier = Modifier.align(Alignment.CenterHorizontally),
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
        }

        TrioStatsCard {
            TrioGlycemicOverview(data, lowMgdl, highMgdl) { value ->
                profileUtil.fromMgdlToStringInUnits(value)
            }
            HorizontalDivider()
            TrioMetrics(data, glycemicMetricUnits)
        }
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
private fun TrioGlycemicOverview(
    data: TrioStatsData,
    lowMgdl: Double,
    highMgdl: Double,
    formatGlucose: (Double) -> String
) {
    val colors = AapsTheme.generalColors
    val bands = listOf(
        TrioTirBand(data.tir.veryLow, colors.bgVeryLow),
        TrioTirBand(data.tir.low, colors.bgLow),
        TrioTirBand(data.tir.inRange, colors.bgInRange),
        TrioTirBand(data.tir.high, colors.bgHigh),
        TrioTirBand(data.tir.veryHigh, colors.bgVeryHigh)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            content = {
                TrioOverviewValue(
                    label = stringResource(
                        UiStrings.trio_stats_range,
                        formatGlucose(lowMgdl),
                        formatGlucose(highMgdl)
                    ),
                    value = data.tir.inRange,
                    color = colors.bgInRange
                )
                TrioOverviewValue(
                    label = stringResource(
                        UiStrings.trio_stats_range,
                        formatGlucose(70.0),
                        formatGlucose(140.0)
                    ),
                    value = data.tightRangePercent,
                    color = colors.bgInRange
                )
            }
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            content = {
                TrioOverviewValue(
                    label = stringResource(
                        UiStrings.trio_stats_above,
                        formatGlucose(highMgdl)
                    ),
                    value = data.tir.high + data.tir.veryHigh,
                    color = colors.bgHigh
                )
                TrioOverviewValue(
                    label = stringResource(
                        UiStrings.trio_stats_below,
                        formatGlucose(lowMgdl)
                    ),
                    value = data.tir.veryLow + data.tir.low,
                    color = colors.bgVeryLow
                )
            }
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            content = {
                TrioOverviewValue(
                    label = stringResource(UiStrings.trio_stats_average),
                    value = formatGlucose(data.averageMgdl),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TrioOverviewValue(
                    label = stringResource(UiStrings.trio_stats_median),
                    value = formatGlucose(data.medianMgdl),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )
        TrioTirRing(bands)
    }
}

@Composable
private fun TrioOverviewValue(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(UiStrings.trio_stats_percent, value),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun TrioOverviewValue(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun TrioTirRing(bands: List<TrioTirBand>) {
    Box(
        modifier = Modifier.size(AapsSpacing.tirPieChartCircleSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = AapsSpacing.tirPieChartStrokeWidth.toPx()
            val inset = strokeWidth / 2f
            var startAngle = -90f
            bands.forEach { band ->
                val sweep = (band.percentage / 100.0 * 360.0).toFloat()
                drawArc(
                    color = band.color,
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
    }
}

@Composable
private fun TrioMetrics(data: TrioStatsData, glycemicMetricUnits: String) {
    val metrics = listOf(
        TrioMetric(
            stringResource(UiStrings.trio_stats_ea1c),
            formatGlycemicMetric(data.eA1cPercent, glycemicMetricUnits)
        ),
        TrioMetric(
            stringResource(UiStrings.trio_stats_gmi),
            formatGlycemicMetric(data.gmiPercent, glycemicMetricUnits)
        ),
        TrioMetric(stringResource(UiStrings.trio_stats_standard_deviation), data.standardDeviationMgdl.round(1).toString()),
        TrioMetric(stringResource(UiStrings.trio_stats_cv), stringResource(UiStrings.trio_stats_percent, data.coefficientOfVariation)),
        TrioMetric(stringResource(UiStrings.trio_stats_days), stringResource(UiStrings.trio_stats_decimal, data.availableDays))
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        metrics.forEach { metric ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
            ) {
                Text(
                    metric.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    metric.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

        }
    }
}

@Composable
private fun formatGlycemicMetric(valuePercent: Double, units: String): String =
    if (units == "mmol/mol") {
        (valuePercent * 10.929 - 23.5).round(1).toString()
    } else {
        stringResource(UiStrings.trio_stats_percent, valuePercent)
    }

@Preview(showBackground = true)
@Composable
private fun TrioGlycemicOverviewPreview() {
    MaterialTheme {
        TrioGlycemicOverview(
            data = TrioStatsData(
                averageMgdl = 119.0,
                medianMgdl = 115.0,
                tightRangePercent = 64.5,
                tir = TrioTirBreakdown(inRange = 81.8, high = 7.7, veryLow = 10.5)
            ),
            lowMgdl = 70.0,
            highMgdl = 180.0,
            formatGlucose = { value ->
                when (value) {
                    70.0  -> "3.9"
                    140.0 -> "7.8"
                    180.0 -> "10.0"
                    else  -> "6.6"
                }
            }
        )
    }
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
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            content = content
        )
    }
}

private data class TrioTirBand(
    val percentage: Double,
    val color: Color
)

private data class TrioMetric(
    val label: String,
    val value: String
)
