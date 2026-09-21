package app.aaps.trio.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility as AnimatedVisibilityComposable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.res.stringResource as androidStringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.R
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_WINDOW_MS = 6L * 60L * 60L * 1000L
private const val MIN_WINDOW_MS = 10L * 60L * 1000L
private const val MAX_WINDOW_MS = 72L * 60L * 60L * 1000L
private const val LIVE_EDGE_TOLERANCE_MS = 10L * 60L * 1000L
private const val NOW_POSITION_FRACTION = 0.6
private const val FUTURE_POSITION_FRACTION = 1.0 - NOW_POSITION_FRACTION
private const val DATA_GAP_MS = 17L * 60L * 1000L
private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val INFO_BUTTON_SHOW_DELAY = 1500L
internal const val BOLUS_VALUE_THRESHOLD_UNITS = 0.5
internal val GRAPH_RANGE_HRS = intArrayOf(4, 6, 10, 12)

private val GRID_INTERVALS_MS = longArrayOf(
    5L * 60L * 1000L,
    15L * 60L * 1000L,
    30L * 60L * 1000L,
    60L * 60L * 1000L,
    2L * 60L * 60L * 1000L,
    4L * 60L * 60L * 1000L,
    8L * 60L * 60L * 1000L,
    12L * 60L * 60L * 1000L,
    24L * 60L * 60L * 1000L
)

internal data class TrioGraphRange(
    val min: Double,
    val max: Double
)

internal data class BolusMarkerPosition(
    val bolus: BolusGraphPoint,
    val x: Float,
    val y: Float
)

@Composable
fun TrioOverviewGraph(
    graphViewModel: GraphViewModel,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val readings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bucketedReadings by graphViewModel.bucketedDataFlow.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val chartConfig by graphViewModel.chartConfigFlow.collectAsStateWithLifecycle()
    val graphConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()
    val derivedTimeRange by graphViewModel.derivedTimeRange.collectAsStateWithLifecycle()
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val treatments by graphViewModel.treatmentGraphFlow.collectAsStateWithLifecycle()

    val history = remember(readings, bucketedReadings) {
        (readings + bucketedReadings)
            .sortedBy(BgDataPoint::timestamp)
            .distinctBy(BgDataPoint::timestamp)
    }
    val visiblePredictions = remember(predictions, graphConfig) {
        if (SeriesType.PREDICTIONS in graphConfig.bgOverlays) {
            predictions.sortedBy(BgDataPoint::timestamp)
        } else {
            emptyList()
        }
    }

    val liveEnd = maxOf(
        nowTimestamp,
        history.lastOrNull()?.timestamp ?: nowTimestamp,
        visiblePredictions.lastOrNull()?.timestamp ?: nowTimestamp,
        nowTimestamp + (DEFAULT_WINDOW_MS * FUTURE_POSITION_FRACTION).toLong()
    )
    val range = derivedTimeRange?.first?.let { it to liveEnd }
        ?: (liveEnd - 24L * 60L * 60L * 1000L to liveEnd)
    var selectedRangeHours by rememberSaveable { mutableStateOf<Int?>(6) }
    var showPredictionInfo by rememberSaveable { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    var showInfoButton by remember { mutableStateOf(true) }
    var infoButtonHideRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            if (isInteracting) showInfoButton = false
        } else {
            delay(INFO_BUTTON_SHOW_DELAY.milliseconds)
            if (!isInteracting) showInfoButton = true
        }
    }
    LaunchedEffect(infoButtonHideRequest) {
        if (infoButtonHideRequest == 0) return@LaunchedEffect
        showInfoButton = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            InteractiveTrioGlucoseChart(
                history = history,
                predictions = visiblePredictions,
                boluses = treatments.boluses,
                fullRange = range,
                nowTimestamp = nowTimestamp,
                lowMark = chartConfig.lowMark,
                highMark = chartConfig.highMark,
                selectedRangeHours = selectedRangeHours,
                onRangeSelected = { selectedRangeHours = it },
                onInteraction = {
                    graphViewModel.onGraphInteraction()
                    infoButtonHideRequest++
                },
                onInteractingChanged = { isInteracting = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.align(Alignment.Center)) {
                GRAPH_RANGE_HRS.forEach { hours ->
                    FilterChip(
                        selected = hours == selectedRangeHours,
                        onClick = { selectedRangeHours = hours },
                        label = {
                            Text(
                                text = stringResource(CoreUiStrings.units_format_hours, hours),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        border = null,
                    )
                }
            }
            GraphInfoButton(
                visible = showInfoButton,
                onClick = { showPredictionInfo = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showPredictionInfo) {
        PredictionLegendBottomSheet(onDismiss = { showPredictionInfo = false })
    }
}

@Composable
private fun GraphInfoButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibilityComposable(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = androidStringResource(R.string.trio_graph_prediction_info),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PredictionLegendBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(
                text = androidStringResource(R.string.trio_graph_prediction_info),
                style = MaterialTheme.typography.titleLarge
            )
            PredictionLegendItem(
                title = androidStringResource(R.string.trio_graph_prediction_iob_title),
                description = androidStringResource(R.string.trio_graph_prediction_iob_description),
                color = AapsTheme.generalColors.iobPrediction
            )
            PredictionLegendItem(
                title = androidStringResource(R.string.trio_graph_prediction_cob_title),
                description = androidStringResource(R.string.trio_graph_prediction_cob_description),
                color = AapsTheme.generalColors.cobPrediction
            )
            PredictionLegendItem(
                title = androidStringResource(R.string.trio_graph_prediction_acob_title),
                description = androidStringResource(R.string.trio_graph_prediction_acob_description),
                color = AapsTheme.generalColors.aCobPrediction
            )
            PredictionLegendItem(
                title = androidStringResource(R.string.trio_graph_prediction_uam_title),
                description = androidStringResource(R.string.trio_graph_prediction_uam_description),
                color = AapsTheme.generalColors.uamPrediction
            )
            PredictionLegendItem(
                title = androidStringResource(R.string.trio_graph_prediction_zt_title),
                description = androidStringResource(R.string.trio_graph_prediction_zt_description),
                color = AapsTheme.generalColors.ztPrediction
            )
            Text(
                text = androidStringResource(R.string.trio_graph_gesture_help_title),
                style = MaterialTheme.typography.titleLarge
            )
            GestureHelpItem(text = androidStringResource(R.string.trio_graph_gesture_pinch))
            GestureHelpItem(text = androidStringResource(R.string.trio_graph_gesture_double_tap))
            GestureHelpItem(text = androidStringResource(R.string.trio_graph_gesture_scroll))
        }
    }
}

@Composable
private fun GestureHelpItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bulletColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(
            modifier = Modifier
                .padding(top = AapsSpacing.small)
                .size(AapsSpacing.small)
        ) {
            drawCircle(color = bulletColor)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PredictionLegendItem(
    title: String,
    description: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .padding(top = AapsSpacing.medium)
                .size(AapsSpacing.xxLarge)
        ) {
            drawLine(
                color = color,
                start = Offset.Zero,
                end = Offset(size.width, 0f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InteractiveTrioGlucoseChart(
    history: List<BgDataPoint>,
    predictions: List<BgDataPoint>,
    boluses: List<BolusGraphPoint>,
    fullRange: Pair<Long, Long>,
    nowTimestamp: Long,
    lowMark: Double,
    highMark: Double,
    selectedRangeHours: Int?,
    onRangeSelected: (Int?) -> Unit,
    onInteraction: () -> Unit,
    onInteractingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    val dateUtil = LocalDateUtil.current
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val numberFormat = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
    }
    val bolusTitle = stringResource(InterfacesStrings.bolus)
    val smbTitle = stringResource(CoreUiStrings.smb_shortname)

    val fullStart = fullRange.first
    val fullEnd = maxOf(fullRange.second, fullStart + MIN_WINDOW_MS)
    val fullSpan = (fullEnd - fullStart).coerceAtLeast(MIN_WINDOW_MS)
    val maxDuration = fullSpan.coerceAtMost(MAX_WINDOW_MS).coerceAtLeast(MIN_WINDOW_MS)
    var visibleDuration by rememberSaveable {
        mutableLongStateOf(DEFAULT_WINDOW_MS.coerceAtMost(maxDuration))
    }
    var centerTime by rememberSaveable {
        mutableLongStateOf(nowCenteredViewport(nowTimestamp, visibleDuration))
    }
    var selectedPoint by remember { mutableStateOf<BgDataPoint?>(null) }
    var selectedBolus by remember { mutableStateOf<BolusGraphPoint?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val inertia = remember { Animatable(0f) }

    fun clampCenter(value: Long, duration: Long = visibleDuration): Long {
        val half = duration / 2L
        val minCenter = fullStart + half
        val maxCenter = (fullEnd - half).coerceAtLeast(minCenter)
        return value.coerceIn(minCenter, maxCenter)
    }

    LaunchedEffect(selectedRangeHours, maxDuration) {
        val hours = selectedRangeHours ?: return@LaunchedEffect
        val duration = (hours * 60L * 60L * 1000L)
            .coerceIn(MIN_WINDOW_MS, maxDuration)
        visibleDuration = duration
        centerTime = clampCenter(nowCenteredViewport(nowTimestamp, duration), duration)
        selectedPoint = null
        selectedBolus = null
    }

    LaunchedEffect(maxDuration) {
        if (visibleDuration > maxDuration) {
            visibleDuration = maxDuration
            centerTime = clampCenter(centerTime, maxDuration)
        }
    }

    LaunchedEffect(fullEnd, nowTimestamp) {
        val liveCenter = nowCenteredViewport(nowTimestamp, visibleDuration)
        if (abs(centerTime - liveCenter) <= LIVE_EDGE_TOLERANCE_MS) {
            centerTime = clampCenter(liveCenter)
        }
    }

    LaunchedEffect(centerTime, visibleDuration, selectedPoint, selectedBolus) {
        val start = centerTime - visibleDuration / 2L
        val end = centerTime + visibleDuration / 2L
        selectedPoint?.let {
            if (it.timestamp !in start..end) selectedPoint = null
        }
        selectedBolus?.let {
            if (it.timestamp !in start..end) selectedBolus = null
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface
    val targetColor = colors.bgTargetRangeArea
    val insulinColor = AapsTheme.elementColors.insulin
    val lineColors = listOf(
        colors.bgVeryHigh,
        colors.bgHigh,
        colors.bgInRange,
        colors.bgLow,
        colors.bgVeryLow
    )
    val predictionColors = mapOf(
        BgType.IOB_PREDICTION to colors.iobPrediction,
        BgType.COB_PREDICTION to colors.cobPrediction,
        BgType.A_COB_PREDICTION to colors.aCobPrediction,
        BgType.UAM_PREDICTION to colors.uamPrediction,
        BgType.ZT_PREDICTION to colors.ztPrediction
    )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    Box(
        modifier = modifier
            .pointerInput(history, predictions, boluses, fullStart, fullEnd, maxDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    coroutineScope.launch { inertia.stop() }
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPointerInputChange(down)
                    val downTime = down.uptimeMillis
                    val isSecondTap = downTime - lastTapTime <= DOUBLE_TAP_TIMEOUT_MS
                    var previous = down
                    var totalX = 0f
                    var totalY = 0f
                    var horizontalGesture = false
                    var zoomGesture = false
                    var interactionStarted = false
                    var pointerCount = 1

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val change = pressed.firstOrNull() ?: event.changes.firstOrNull() ?: break
                        if (pressed.isEmpty() || change.changedToUp()) break
                        if (pressed.size != pointerCount) {
                            pointerCount = pressed.size
                            previous = change
                            totalX = 0f
                            totalY = 0f
                            velocityTracker.resetTracking()
                            continue
                        }

                        if (pressed.size > 1) {
                            val zoom = event.calculateZoom()
                            if (zoom.isFinite() && zoom != 1f) {
                                val effectiveZoom = 1f + (zoom - 1f) * 2f
                                val newDuration = (visibleDuration / effectiveZoom)
                                    .toLong()
                                    .coerceIn(MIN_WINDOW_MS, maxDuration)
                                visibleDuration = newDuration
                                centerTime = clampCenter(centerTime, newDuration)
                                onRangeSelected(null)
                                zoomGesture = true
                                if (!interactionStarted) {
                                    interactionStarted = true
                                    onInteractingChanged(true)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            velocityTracker.addPointerInputChange(change)
                            val deltaX = change.position.x - previous.position.x
                            val deltaY = change.position.y - previous.position.y
                            totalX += deltaX
                            totalY += deltaY

                            if (!horizontalGesture && abs(totalX) > viewConfiguration.touchSlop) {
                                horizontalGesture = abs(totalX) > abs(totalY)
                                if (horizontalGesture) {
                                    interactionStarted = true
                                    onInteractingChanged(true)
                                }
                            }
                            if (horizontalGesture) {
                                val timePerPixel = visibleDuration.toDouble() / size.width.coerceAtLeast(1)
                                centerTime = clampCenter(centerTime - (deltaX * timePerPixel).toLong())
                                change.consume()
                            }
                        }
                        previous = change
                    }

                    val wasTap = !horizontalGesture && !zoomGesture &&
                        abs(totalX) < viewConfiguration.touchSlop &&
                        abs(totalY) < viewConfiguration.touchSlop

                    when {
                        wasTap && isSecondTap -> {
                            interactionStarted = true
                            onInteractingChanged(true)
                            val targetDuration = if (visibleDuration <= DEFAULT_WINDOW_MS / 2L) {
                                DEFAULT_WINDOW_MS.coerceAtMost(maxDuration)
                            } else {
                                (visibleDuration / 2L).coerceAtLeast(MIN_WINDOW_MS)
                            }
                            visibleDuration = targetDuration
                            centerTime = clampCenter(centerTime, targetDuration)
                            onRangeSelected(null)
                            selectedPoint = null
                            selectedBolus = null
                            lastTapTime = 0L
                            onRangeSelected(null)
                            onInteraction()
                        }

                        wasTap -> {
                            lastTapTime = downTime
                            val viewportStart = centerTime - visibleDuration / 2L
                            val viewportEnd = centerTime + visibleDuration / 2L
                            val visibleHistory = history.withRangePadding(viewportStart, viewportEnd)
                            val visiblePredictionPoints = predictions.withRangePadding(viewportStart, viewportEnd)
                            val yRange = calculateYRange(
                                points = visibleHistory + visiblePredictionPoints,
                                lowMark = lowMark,
                                highMark = highMark
                            )
                            val plotHeight = (size.height - 32.dp.toPx()).coerceAtLeast(1f)
                            val markers = calculateBolusMarkerPositions(
                                boluses = boluses,
                                history = history,
                                viewportStart = viewportStart,
                                viewportDuration = visibleDuration,
                                width = size.width.toFloat(),
                                plotHeight = plotHeight,
                                yRange = yRange,
                                markerOffset = 12.dp.toPx()
                            )
                            val tappedBolus = findTappedBolus(
                                markers = markers,
                                tapX = down.position.x,
                                tapY = down.position.y,
                                hitRadius = 20.dp.toPx()
                            )
                            val tappedTime = viewportStart +
                                (down.position.x / size.width.coerceAtLeast(1)) * visibleDuration
                            selectedBolus = tappedBolus
                            selectedPoint = if (tappedBolus == null) {
                                nearestPoint(history, tappedTime.toLong())
                            } else {
                                null
                            }
                        }

                        horizontalGesture -> {
                            lastTapTime = 0L
                            onRangeSelected(null)
                            onInteraction()
                            val velocityX = velocityTracker.calculateVelocity().x
                            if (abs(velocityX) > 1_000f) {
                                coroutineScope.launch {
                                    var previousValue = 0f
                                    inertia.snapTo(0f)
                                    inertia.animateDecay(
                                        initialVelocity = -velocityX,
                                        animationSpec = exponentialDecay(frictionMultiplier = 2f)
                                    ) {
                                        val delta = value - previousValue
                                        val timePerPixel = visibleDuration.toDouble() / size.width.coerceAtLeast(1)
                                        centerTime = clampCenter(centerTime + (delta * timePerPixel).toLong())
                                        previousValue = value
                                    }
                                }
                            }
                        }

                        zoomGesture -> {
                            lastTapTime = 0L
                            onInteraction()
                        }
                    }
                    if (interactionStarted) onInteractingChanged(false)
                }
            }
    ) {
        val animatedVisibleDuration by animateFloatAsState(
            targetValue = visibleDuration.toFloat(),
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "TrioGraphZoom"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val bottomAxisHeight = 32.dp.toPx()
            val plotHeight = (size.height - bottomAxisHeight).coerceAtLeast(1f)
            val renderedDuration = animatedVisibleDuration.toLong().coerceAtLeast(MIN_WINDOW_MS)
            val viewportStart = centerTime - renderedDuration / 2L
            val viewportEnd = centerTime + renderedDuration / 2L
            val visibleHistory = history.withRangePadding(viewportStart, viewportEnd)
            val visiblePredictionPoints = predictions.withRangePadding(viewportStart, viewportEnd)
            val yRange = calculateYRange(
                points = visibleHistory + visiblePredictionPoints,
                lowMark = lowMark,
                highMark = highMark
            )
            val ySpan = (yRange.max - yRange.min).coerceAtLeast(0.1)

            fun timeToX(timestamp: Long): Float =
                ((timestamp - viewportStart).toDouble() / renderedDuration * size.width).toFloat()

            fun valueToY(value: Double): Float =
                (plotHeight - ((value - yRange.min) / ySpan * plotHeight)).toFloat()

            val targetTop = valueToY(highMark).coerceIn(0f, plotHeight)
            val targetBottom = valueToY(lowMark).coerceIn(0f, plotHeight)
            drawRect(
                color = targetColor,
                topLeft = Offset(0f, minOf(targetTop, targetBottom)),
                size = Size(size.width, abs(targetBottom - targetTop))
            )

            val yStep = chooseYGridStep(ySpan)
            var yValue = ceil(yRange.min / yStep) * yStep
            while (yValue < yRange.max) {
                val y = valueToY(yValue)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                val label = numberFormat.format(yValue)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(AapsSpacing.small.toPx(), (y - 8.dp.toPx()).coerceAtLeast(0f)),
                    style = labelStyle
                )
                yValue += yStep
            }

            val gridInterval = chooseTimeGridInterval(renderedDuration, size.width)
            var gridTime = floor(viewportStart.toDouble() / gridInterval).toLong() * gridInterval
            if (gridTime < viewportStart) gridTime += gridInterval
            while (gridTime <= viewportEnd) {
                val x = timeToX(gridTime)
                drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), 1.dp.toPx())
                val label = dateUtil.timeString(gridTime)
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(
                        (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                        plotHeight + AapsSpacing.small.toPx()
                    ),
                    style = labelStyle
                )
                gridTime += gridInterval
            }

            val highPosition = ((yRange.max - highMark) / ySpan).toFloat().coerceIn(0f, 1f)
            val lowPosition = ((yRange.max - lowMark) / ySpan).toFloat().coerceIn(0f, 1f)
            val lineBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to lineColors[0],
                    highPosition to lineColors[1],
                    ((highPosition + lowPosition) / 2f) to lineColors[2],
                    lowPosition to lineColors[3],
                    1f to lineColors[4]
                ),
                endY = plotHeight
            )

            drawGlucosePath(
                points = visibleHistory,
                x = ::timeToX,
                y = ::valueToY,
                brush = lineBrush,
                pointColor = { point ->
                    when (point.range) {
                        BgRange.HIGH     -> colors.bgHigh
                        BgRange.IN_RANGE -> colors.bgInRange
                        BgRange.LOW      -> colors.bgLow
                    }
                }
            )

            predictions.groupBy(BgDataPoint::type).forEach { (type, points) ->
                val color = predictionColors[type] ?: return@forEach
                val path = points.withRangePadding(viewportStart, viewportEnd).toPath(::timeToX, ::valueToY)
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.7f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
                    )
                )
            }

            val bolusMarkers = calculateBolusMarkerPositions(
                boluses = boluses,
                history = history,
                viewportStart = viewportStart,
                viewportDuration = renderedDuration,
                width = size.width,
                plotHeight = plotHeight,
                yRange = yRange,
                markerOffset = 12.dp.toPx()
            )
            val markerHalfWidth = 5.dp.toPx()
            val markerHalfHeight = 5.dp.toPx()
            bolusMarkers.forEach { marker ->
                val markerPath = Path().apply {
                    moveTo(marker.x - markerHalfWidth, marker.y - markerHalfHeight)
                    lineTo(marker.x + markerHalfWidth, marker.y - markerHalfHeight)
                    lineTo(marker.x, marker.y + markerHalfHeight)
                    close()
                }
                drawPath(
                    path = markerPath,
                    color = insulinColor
                )
                if (shouldShowBolusValue(marker.bolus.amount)) {
                    val labelLayout = textMeasurer.measure(marker.bolus.label, labelStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = marker.bolus.label,
                        topLeft = Offset(
                            x = (marker.x - labelLayout.size.width / 2f)
                                .coerceIn(0f, size.width - labelLayout.size.width),
                            y = (marker.y - markerHalfHeight - labelLayout.size.height)
                                .coerceAtLeast(0f)
                        ),
                        style = labelStyle
                    )
                }
            }

            if (nowTimestampInRange(viewportStart, viewportEnd, nowTimestamp)) {
                val x = timeToX(nowTimestamp)
                drawLine(
                    color = selectionColor.copy(alpha = 0.45f),
                    start = Offset(x, 0f),
                    end = Offset(x, plotHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }

            selectedPoint?.let { point ->
                val x = timeToX(point.timestamp)
                val y = valueToY(point.value)
                drawLine(
                    color = selectionColor.copy(alpha = 0.65f),
                    start = Offset(x, 0f),
                    end = Offset(x, plotHeight),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 7.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = selectionColor,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        selectedBolus?.let { bolus ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = AapsSpacing.medium),
                shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = AapsSpacing.extraSmall
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AapsSpacing.large,
                        vertical = AapsSpacing.medium
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
                ) {
                    Text(
                        text = bolusTitle,
                        color = insulinColor,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = stringResource(InterfacesStrings.format_insulin_units, bolus.amount),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (bolus.bolusType == BolusType.SMB) {
                        Text(
                            text = smbTitle,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(
                        text = dateUtil.timeString(bolus.timestamp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } ?: selectedPoint?.let { point ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = AapsSpacing.medium),
                shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = AapsSpacing.extraSmall
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AapsSpacing.large,
                        vertical = AapsSpacing.medium
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
                ) {
                    Text(
                        text = numberFormat.format(point.value),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = dateUtil.timeString(point.timestamp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun List<BgDataPoint>.withRangePadding(start: Long, end: Long): List<BgDataPoint> {
    if (isEmpty()) return emptyList()
    val first = binarySearchBy(start) { it.timestamp }
        .let { if (it >= 0) it else -it - 1 }
        .minus(1)
        .coerceAtLeast(0)
    val last = binarySearchBy(end) { it.timestamp }
        .let { if (it >= 0) it + 1 else -it - 1 }
        .plus(1)
        .coerceAtMost(size)
    return if (first < last) subList(first, last) else emptyList()
}

private fun nearestPoint(points: List<BgDataPoint>, timestamp: Long): BgDataPoint? {
    if (points.isEmpty()) return null
    val index = points.binarySearchBy(timestamp) { it.timestamp }
    if (index >= 0) return points[index]
    val insertionPoint = -index - 1
    if (insertionPoint == 0) return points.first()
    if (insertionPoint == points.size) return points.last()
    val before = points[insertionPoint - 1]
    val after = points[insertionPoint]
    return if (timestamp - before.timestamp <= after.timestamp - timestamp) before else after
}

internal fun shouldShowBolusValue(amount: Double): Boolean =
    amount > BOLUS_VALUE_THRESHOLD_UNITS

internal fun calculateBolusMarkerPositions(
    boluses: List<BolusGraphPoint>,
    history: List<BgDataPoint>,
    viewportStart: Long,
    viewportDuration: Long,
    width: Float,
    plotHeight: Float,
    yRange: TrioGraphRange,
    markerOffset: Float
): List<BolusMarkerPosition> {
    if (history.isEmpty() || viewportDuration <= 0L || width <= 0f || plotHeight <= 0f) return emptyList()
    val viewportEnd = viewportStart + viewportDuration
    val ySpan = (yRange.max - yRange.min).coerceAtLeast(0.1)
    val maxMarkerY = (plotHeight - markerOffset).coerceAtLeast(markerOffset)
    return boluses
        .asSequence()
        .filter { it.isValid && it.amount > 0.0 && it.timestamp in viewportStart..viewportEnd }
        .mapNotNull { bolus ->
            val glucose = nearestPoint(history, bolus.timestamp) ?: return@mapNotNull null
            val x = ((bolus.timestamp - viewportStart).toDouble() / viewportDuration * width).toFloat()
            val glucoseY = (plotHeight - ((glucose.value - yRange.min) / ySpan * plotHeight)).toFloat()
            BolusMarkerPosition(
                bolus = bolus,
                x = x,
                y = (glucoseY - markerOffset).coerceIn(markerOffset, maxMarkerY)
            )
        }
        .toList()
}

internal fun findTappedBolus(
    markers: List<BolusMarkerPosition>,
    tapX: Float,
    tapY: Float,
    hitRadius: Float
): BolusGraphPoint? {
    val maxDistanceSquared = hitRadius * hitRadius
    return markers
        .map { marker ->
            val dx = marker.x - tapX
            val dy = marker.y - tapY
            marker to dx * dx + dy * dy
        }
        .filter { (_, distanceSquared) -> distanceSquared <= maxDistanceSquared }
        .minByOrNull { (_, distanceSquared) -> distanceSquared }
        ?.first
        ?.bolus
}

private fun calculateYRange(
    points: List<BgDataPoint>,
    lowMark: Double,
    highMark: Double
): TrioGraphRange {
    val values = points.map(BgDataPoint::value).filter(Double::isFinite)
    val baseMin = minOf(lowMark, values.minOrNull() ?: lowMark)
    val baseMax = maxOf(highMark, values.maxOrNull() ?: highMark)
    val span = (baseMax - baseMin).coerceAtLeast(if (highMark < 20.0) 6.0 else 108.0)
    val padding = span * 0.08
    val step = if (highMark < 20.0) 1.0 else 18.0
    return TrioGraphRange(
        min = (floor((baseMin - padding) / step) * step).coerceAtLeast(0.0),
        max = ceil((baseMax + padding) / step) * step
    )
}

private fun chooseYGridStep(span: Double): Double = when {
    span <= 8.0  -> 1.0
    span <= 20.0 -> 2.0
    span <= 120.0 -> 20.0
    else         -> 50.0
}

private fun chooseTimeGridInterval(duration: Long, width: Float): Long {
    val minimumInterval = (duration * (120f / width.coerceAtLeast(1f))).toLong()
    return GRID_INTERVALS_MS.firstOrNull { it >= minimumInterval } ?: GRID_INTERVALS_MS.last()
}

private fun List<BgDataPoint>.toPath(
    x: (Long) -> Float,
    y: (Double) -> Float
): Path {
    val path = Path()
    var previousTimestamp: Long? = null
    forEach { point ->
        val pointX = x(point.timestamp)
        val pointY = y(point.value)
        if (previousTimestamp == null || point.timestamp - previousTimestamp!! > DATA_GAP_MS) {
            path.moveTo(pointX, pointY)
        } else {
            path.lineTo(pointX, pointY)
        }
        previousTimestamp = point.timestamp
    }
    return path
}

private fun DrawScope.drawGlucosePath(
    points: List<BgDataPoint>,
    x: (Long) -> Float,
    y: (Double) -> Float,
    brush: Brush,
    pointColor: (BgDataPoint) -> Color
) {
    if (points.isEmpty()) return
    drawPath(
        path = points.toPath(x, y),
        brush = brush,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    points.forEach { point ->
        drawCircle(
            color = pointColor(point),
            radius = 2.5.dp.toPx(),
            center = Offset(x(point.timestamp), y(point.value))
        )
    }
}

private fun nowTimestampInRange(start: Long, end: Long, timestamp: Long): Boolean =
    timestamp in start..end

private fun nowCenteredViewport(nowTimestamp: Long, duration: Long): Long =
    nowTimestamp - (duration * (NOW_POSITION_FRACTION - 0.5)).toLong()
