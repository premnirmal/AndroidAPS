package app.aaps.trio.ui.compose.overview

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val HOUR_MILLIS = 3_600_000L
private const val DEFAULT_VIEWPORT_MILLIS = 6 * HOUR_MILLIS
private const val MIN_VIEWPORT_MILLIS = 30 * 60_000L
// On first render the viewport shows 75% history and 25% future, so "now" sits at 75% of the width.
private const val FUTURE_POSITION_FRACTION = 0.25
private val ZOOM_CYCLE_MILLIS = listOf(12 * HOUR_MILLIS, 6 * HOUR_MILLIS, 3 * HOUR_MILLIS)
private const val MIN_FLING_VELOCITY_PX_PER_SEC = 50f
// Kept at 0 so the plot spans the full width and the Y-axis value labels overlay the left of the
// plot. The gutter term is left in the layout maths as the plot origin in case a non-zero gutter is ever wanted again.
private val LEFT_GUTTER = 0.dp
// Small inset so the Y-axis value labels are not flush against the very left edge. The labels are
// drawn on top of the plot so there is no blank strip on the left.
private val Y_LABEL_INSET = 2.dp
private val BOTTOM_LABEL_GAP = 4.dp
private val BOTTOM_SAFETY_MARGIN = 6.dp
private const val SCROLL_TO_LATEST_MILLIS = 700
private const val INSPECT_MATCH_MILLIS = 300_000L
private const val INSPECT_STATUS_WINDOW_MILLIS = 150_000L
// How close (in pixels) a tap must land to a reading or bolus marker to select it.
private const val TAP_MATCH_PX = 24f
private val INSPECT_EDGE_ZONE = 44.dp
private const val LIVE_EDGE_TOLERANCE_MILLIS = 2_000L

// Band layout, top to bottom: basal strip, glucose area, IOB/COB strip, then the time axis labels.
private val BASAL_STRIP_HEIGHT = 40.dp
private val STRIP_TO_GLUCOSE_GAP = 8.dp
private val GLUCOSE_TO_IOB_GAP = 8.dp
private val IOB_STRIP_HEIGHT = 50.dp
private val BOLUS_MARKER_TOP_MARGIN = 10.dp

/**
 * The part of a time-sorted list within [start, end], found by binary search. When [includeBounds]
 * is true the range is widened by one on each side so the point just before [start] and just after
 * [end] are kept as well. This is needed for step lines (such as the target line) whose points are
 * stored compressed: only the moments where the value changes are kept, so a constant value across
 * the whole graph is just two points at the extreme edges. Clipping strictly to the viewport would
 * drop both and the line would disappear while scrolling; keeping the bounding neighbours lets the
 * line span the full width at any scroll position.
 */
private inline fun <T> List<T>.sliceByMillis(start: Long, end: Long, includeBounds: Boolean = false, millis: (T) -> Long): List<T> {
    if (isEmpty()) return this
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (millis(this[mid]) < start) lo = mid + 1 else hi = mid
    }
    val from = if (includeBounds) (lo - 1).coerceAtLeast(0) else lo
    hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (millis(this[mid]) <= end) lo = mid + 1 else hi = mid
    }
    val until = if (includeBounds) (lo + 1).coerceAtMost(size) else lo
    return subList(from, until)
}

/**
 * Pannable, pinch-zoomable glucose chart with fling and double-tap zoom (12h/6h/3h). Bands from top
 * to bottom: basal, glucose, IOB/COB. Adapted from TrioFollower's GlucoseChart to draw from the
 * existing AndroidAPS [GraphViewModel] data (user units, millisecond timestamps).
 */
@Composable
fun GlucoseChart(
    graphViewModel: GraphViewModel,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val readings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val bucketedReadings by graphViewModel.bucketedDataFlow.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val chartConfig by graphViewModel.chartConfigFlow.collectAsStateWithLifecycle()
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val treatments by graphViewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val basal by graphViewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val iobData by graphViewModel.iobGraphFlow.collectAsStateWithLifecycle()
    val cobData by graphViewModel.cobGraphFlow.collectAsStateWithLifecycle()
    val targetLine by graphViewModel.targetLineFlow.collectAsStateWithLifecycle()

    val readingsAsc = remember(readings, bucketedReadings) {
        (readings + bucketedReadings).sortedBy(BgDataPoint::timestamp).distinctBy(BgDataPoint::timestamp)
    }
    // Always draw the forecast lines when prediction data is present. The persisted graph config
    // (or simple mode) can leave the PREDICTIONS overlay off, which would hide them here, so this
    // chart does not gate on it.
    val visiblePredictions = remember(predictions) {
        predictions.sortedBy(BgDataPoint::timestamp)
    }
    val boluses = remember(treatments) { treatments.boluses.filter { it.isValid && it.amount > 0.0 } }
    val carbEntries = remember(treatments) { treatments.carbs.filter { it.isValid && it.amount > 0.0 } }
    val basalSegments = remember(basal) { basal.actualBasal.sortedBy(GraphDataPoint::timestamp) }
    val iobPointsAsc = remember(iobData) { iobData.iob.sortedBy(GraphDataPoint::timestamp) }
    val cobPointsAsc = remember(cobData) { cobData.cob.sortedBy(GraphDataPoint::timestamp) }
    val targetPointsAsc = remember(targetLine) { targetLine.targets.sortedBy(GraphDataPoint::timestamp) }
    val basalCeiling = remember(basal) { basal.maxBasal.coerceAtLeast(0.1) }

    val density = LocalDensity.current
    val dateUtil = LocalDateUtil.current
    val textMeasurer = rememberTextMeasurer()
    val colors = AapsTheme.generalColors
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val basalColor = colors.trioBasal
    val bolusColor = colors.trioInsulin
    val carbColor = colors.cobPrediction
    val iobColor = colors.iobPrediction
    val cobColor = colors.cobPrediction
    val targetColor = colors.loopClosed
    val targetRangeColor = colors.bgTargetRangeArea
    val lowColor = colors.bgLow
    val highColor = colors.bgHigh
    val predictionColors = mapOf(
        BgType.IOB_PREDICTION to colors.iobPrediction,
        BgType.COB_PREDICTION to colors.cobPrediction,
        BgType.A_COB_PREDICTION to colors.aCobPrediction,
        BgType.UAM_PREDICTION to colors.uamPrediction,
        BgType.ZT_PREDICTION to colors.ztPrediction
    )

    val numberFormat = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
    }

    fun rangeColor(range: BgRange): Color = when (range) {
        BgRange.HIGH     -> highColor
        BgRange.IN_RANGE -> colors.bgInRange
        BgRange.LOW      -> lowColor
    }

    val lowMark = chartConfig.lowMark
    val highMark = chartConfig.highMark

    val dataMinMillis = readingsAsc.firstOrNull()?.timestamp ?: (nowTimestamp - DEFAULT_VIEWPORT_MILLIS)
    val forecastEndMillis = visiblePredictions.lastOrNull()?.timestamp ?: nowTimestamp
    val futureMillis = (forecastEndMillis - nowTimestamp).coerceAtLeast(0L)
    // Always reserve a 25% future strip at the resting position, even when the prediction is short,
    // and let the user scroll forward as far as the prediction reaches.
    val restingFutureMillis = (DEFAULT_VIEWPORT_MILLIS * FUTURE_POSITION_FRACTION).toLong()
    val dataMaxMillis = nowTimestamp + maxOf(futureMillis, restingFutureMillis)
    val maxViewportMillis = (dataMaxMillis - dataMinMillis).coerceAtLeast(DEFAULT_VIEWPORT_MILLIS)

    // Resting position: "now" at 75% of the width, so the right 25% shows the future prediction.
    fun liveEdgeMillis(durationMillis: Long) = nowTimestamp + (durationMillis * FUTURE_POSITION_FRACTION).toLong()

    var viewportDurationMillis by remember { mutableLongStateOf(DEFAULT_VIEWPORT_MILLIS) }
    var viewportEndMillis by remember { mutableLongStateOf(liveEdgeMillis(DEFAULT_VIEWPORT_MILLIS)) }
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var zoomCycleIndex by remember { mutableIntStateOf(-1) }
    var lastFollowedEndMillis by remember { mutableLongStateOf(viewportEndMillis) }
    var inspectX by remember { mutableStateOf<Float?>(null) }
    // A tap picks the nearest reading or bolus and keeps it selected until the user taps elsewhere.
    var tapSelection by remember { mutableStateOf<ChartTapSelection?>(null) }
    // Info button opens the prediction/gesture legend
    var showPredictionInfo by remember { mutableStateOf(false) }

    val leftGutterPx = with(density) { LEFT_GUTTER.toPx() }

    val currentDataMinMillis by rememberUpdatedState(dataMinMillis)
    val currentDataMaxMillis by rememberUpdatedState(dataMaxMillis)
    val currentMaxViewportMillis by rememberUpdatedState(maxViewportMillis)
    val currentReadings by rememberUpdatedState(readingsAsc)
    val currentBoluses by rememberUpdatedState(boluses)

    val inspectFingerX = inspectX
    val inspectReading: BgDataPoint? = if (inspectFingerX != null && canvasWidthPx > leftGutterPx) {
        val fraction = ((inspectFingerX - leftGutterPx) / (canvasWidthPx - leftGutterPx)).coerceIn(0f, 1f)
        val timeMillis = viewportEndMillis - viewportDurationMillis + (fraction * viewportDurationMillis).toLong()
        readingsAsc
            .sliceByMillis(timeMillis - INSPECT_MATCH_MILLIS, timeMillis + INSPECT_MATCH_MILLIS) { it.timestamp }
            .minByOrNull { abs(it.timestamp - timeMillis) }
    } else {
        null
    }
    // Press-hold inspect takes precedence; otherwise a tap keeps a reading or bolus selected.
    val tapBg = (tapSelection as? ChartTapSelection.Bg)?.reading
    val tapBolus = (tapSelection as? ChartTapSelection.Bolus)?.bolus
    val selectedReading: BgDataPoint? = inspectReading ?: tapBg
    val selectedBolus: BolusGraphPoint? = if (inspectReading == null) tapBolus else null
    val selectedIob = selectedReading?.let { reading ->
        iobPointsAsc
            .sliceByMillis(reading.timestamp - INSPECT_STATUS_WINDOW_MILLIS, reading.timestamp + INSPECT_STATUS_WINDOW_MILLIS) { it.timestamp }
            .minByOrNull { abs(it.timestamp - reading.timestamp) }?.value
    }
    val selectedCob = selectedReading?.let { reading ->
        cobPointsAsc
            .sliceByMillis(reading.timestamp - INSPECT_STATUS_WINDOW_MILLIS, reading.timestamp + INSPECT_STATUS_WINDOW_MILLIS) { it.timestamp }
            .minByOrNull { abs(it.timestamp - reading.timestamp) }?.value
    }
    LaunchedEffect(inspectReading?.timestamp) {
        if (inspectReading != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // While inspecting, a finger near either edge scrolls the chart (faster the closer to the edge).
    val currentInspectX by rememberUpdatedState(inspectX)
    val isInspecting = inspectX != null
    LaunchedEffect(isInspecting) {
        if (!isInspecting) return@LaunchedEffect
        val zonePx = with(density) { INSPECT_EDGE_ZONE.toPx() }
        var lastNanos = System.nanoTime()
        while (true) {
            delay(16)
            val nowNanos = System.nanoTime()
            val seconds = (nowNanos - lastNanos) / 1e9
            lastNanos = nowNanos
            val x = currentInspectX ?: break
            val leftDepth = ((leftGutterPx + zonePx - x) / zonePx).coerceIn(0f, 1f)
            val rightDepth = ((x - (canvasWidthPx - zonePx)) / zonePx).coerceIn(0f, 1f)
            val depth = maxOf(leftDepth, rightDepth)
            if (depth <= 0f) continue
            val direction = if (rightDepth > 0f) 1 else -1
            val deltaMillis = (direction * depth * viewportDurationMillis * 0.5 * seconds).toLong()
            val minEnd = currentDataMinMillis + viewportDurationMillis
            viewportEndMillis = (viewportEndMillis + deltaMillis).coerceIn(minEnd, maxOf(minEnd, currentDataMaxMillis))
        }
    }

    // Keeps targetTimeMillis at targetFraction of the chart width for the given duration, clamped to
    // the data range. Used by pinch, double-tap and fling.
    fun applyViewport(targetTimeMillis: Long, targetFraction: Float, requestedDurationMillis: Long) {
        val newDuration = requestedDurationMillis.coerceIn(MIN_VIEWPORT_MILLIS, currentMaxViewportMillis)
        var newStart = targetTimeMillis - (targetFraction * newDuration).toLong()
        var newEnd = newStart + newDuration
        if (newStart < currentDataMinMillis) {
            newEnd += currentDataMinMillis - newStart
            newStart = currentDataMinMillis
        }
        if (newEnd > currentDataMaxMillis) {
            newStart -= newEnd - currentDataMaxMillis
            newEnd = currentDataMaxMillis
        }
        newStart = newStart.coerceAtLeast(currentDataMinMillis)
        viewportDurationMillis = newEnd - newStart
        viewportEndMillis = newEnd
    }

    // After each refresh, glide to the newest data when the viewport is still at the live edge.
    LaunchedEffect(nowTimestamp, forecastEndMillis) {
        val startEnd = viewportEndMillis
        val targetEnd = liveEdgeMillis(viewportDurationMillis)
        val atLiveEdge = startEnd >= lastFollowedEndMillis - LIVE_EDGE_TOLERANCE_MILLIS
        if (atLiveEdge && targetEnd != startEnd) {
            flingJob?.cancel()
            lastFollowedEndMillis = targetEnd
            animate(0f, 1f, animationSpec = tween(SCROLL_TO_LATEST_MILLIS, easing = FastOutSlowInEasing)) { fraction, _ ->
                viewportEndMillis = startEnd + ((targetEnd - startEnd) * fraction).toLong()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectChartGestures(
                    onTouchDown = { flingJob?.cancel() },
                    onGesture = { centroid, pan, zoom ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f) {
                            val oldDuration = viewportDurationMillis
                            val oldStart = viewportEndMillis - oldDuration
                            val centroidFraction = ((centroid.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                            val centroidTimeMillis = oldStart + (centroidFraction * oldDuration).toLong()
                            val requestedDuration = (oldDuration / zoom).toLong()
                            val newDurationForPanScale = requestedDuration
                                .coerceIn(MIN_VIEWPORT_MILLIS, currentMaxViewportMillis)
                            val millisPerPx = newDurationForPanScale / chartWidthPx
                            val adjustedTargetMillis = centroidTimeMillis - (pan.x * millisPerPx).toLong()
                            applyViewport(adjustedTargetMillis, centroidFraction, requestedDuration)
                        }
                    },
                    onFlingVelocity = { velocityPxPerSec ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f && abs(velocityPxPerSec) > MIN_FLING_VELOCITY_PX_PER_SEC) {
                            val durationAtFlingStart = viewportDurationMillis
                            val millisPerPx = durationAtFlingStart / chartWidthPx
                            flingJob = coroutineScope.launch {
                                var previousValue = 0f
                                AnimationState(initialValue = 0f, initialVelocity = velocityPxPerSec)
                                    .animateDecay(exponentialDecay()) {
                                        val deltaMillis = ((value - previousValue) * millisPerPx).toLong()
                                        previousValue = value
                                        var newEnd = viewportEndMillis - deltaMillis
                                        var newStart = newEnd - durationAtFlingStart
                                        var hitBoundary = false
                                        if (newStart < currentDataMinMillis) {
                                            newStart = currentDataMinMillis
                                            newEnd = newStart + durationAtFlingStart
                                            hitBoundary = true
                                        }
                                        if (newEnd > currentDataMaxMillis) {
                                            newEnd = currentDataMaxMillis
                                            newStart = newEnd - durationAtFlingStart
                                            hitBoundary = true
                                        }
                                        viewportEndMillis = newEnd
                                        if (hitBoundary) cancelAnimation()
                                    }
                            }
                        }
                    },
                    onInspectStart = { x ->
                        flingJob?.cancel()
                        tapSelection = null
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        inspectX = x
                    },
                    onInspectMove = { x -> inspectX = x },
                    onInspectEnd = { inspectX = null },
                    onTap = { tapPosition ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f) {
                            val duration = viewportDurationMillis
                            val start = viewportEndMillis - duration
                            val tapFraction = ((tapPosition.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                            val tapTimeMillis = start + (tapFraction * duration).toLong()
                            // A dose or reading counts as "hit" when it is within this many pixels of the tap.
                            val matchMillis = (TAP_MATCH_PX / chartWidthPx * duration).toLong()
                            val nearestBolus = currentBoluses.minByOrNull { abs(it.timestamp - tapTimeMillis) }
                            val nearestReading = currentReadings.minByOrNull { abs(it.timestamp - tapTimeMillis) }
                            tapSelection = when {
                                nearestBolus != null && abs(nearestBolus.timestamp - tapTimeMillis) <= matchMillis ->
                                    ChartTapSelection.Bolus(nearestBolus)

                                nearestReading != null && abs(nearestReading.timestamp - tapTimeMillis) <= matchMillis ->
                                    ChartTapSelection.Bg(nearestReading)

                                else                                                                              -> null
                            }
                        }
                    },
                    onDoubleTap = { tapPosition ->
                        tapSelection = null
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f) {
                            zoomCycleIndex = (zoomCycleIndex + 1) % ZOOM_CYCLE_MILLIS.size
                            val oldDuration = viewportDurationMillis
                            val oldStart = viewportEndMillis - oldDuration
                            val tapFraction = ((tapPosition.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                            val tapTimeMillis = oldStart + (tapFraction * oldDuration).toLong()
                            applyViewport(tapTimeMillis, tapFraction, ZOOM_CYCLE_MILLIS[zoomCycleIndex])
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewportStartMillis = viewportEndMillis - viewportDurationMillis
            val sorted = readingsAsc.sliceByMillis(viewportStartMillis, viewportEndMillis) { it.timestamp }

            val leftGutter = LEFT_GUTTER.toPx()
            val chartWidth = size.width - leftGutter

            val axisLabelHeight = textMeasurer.measure("00", style = TextStyle(fontSize = 10.sp)).size.height.toFloat()
            val bottomAxisPx = BOTTOM_LABEL_GAP.toPx() + axisLabelHeight + BOTTOM_SAFETY_MARGIN.toPx()

            val basalStripPx = BASAL_STRIP_HEIGHT.toPx()
            val glucoseTop = basalStripPx + STRIP_TO_GLUCOSE_GAP.toPx()
            val iobBottom = size.height - bottomAxisPx
            val iobTop = iobBottom - IOB_STRIP_HEIGHT.toPx()
            val glucoseBottom = iobTop - GLUCOSE_TO_IOB_GAP.toPx()
            val glucoseChartHeight = (glucoseBottom - glucoseTop).coerceAtLeast(1f)

            // Y range in user units, from the visible readings/predictions plus the target marks.
            val visibleValues = sorted.map { it.value } +
                visiblePredictions.sliceByMillis(viewportStartMillis, viewportEndMillis) { it.timestamp }.map { it.value }
            val yMin = (visibleValues.minOrNull() ?: lowMark).coerceAtMost(lowMark)
            val yMax = (visibleValues.maxOrNull() ?: highMark).coerceAtLeast(highMark)
            val ySpan = (yMax - yMin).coerceAtLeast(0.1)

            fun xFor(millis: Long): Float =
                leftGutter + ((millis - viewportStartMillis).toFloat() / viewportDurationMillis) * chartWidth

            fun yFor(value: Double): Float {
                val clamped = value.coerceIn(yMin, yMax)
                return glucoseBottom - (((clamped - yMin) / ySpan).toFloat()) * glucoseChartHeight
            }

            // Target BG range as a light translucent green band .
            val targetTop = yFor(highMark)
            val targetBottom = yFor(lowMark)
            drawRect(
                color = targetRangeColor,
                topLeft = Offset(leftGutter, targetTop),
                size = Size(chartWidth, (targetBottom - targetTop).coerceAtLeast(0f)),
            )

            // Horizontal gridlines with value labels.
            val yStep = chooseYGridStep(ySpan)
            var yValue = ceil(yMin / yStep) * yStep
            while (yValue < yMax) {
                val y = yFor(yValue)
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(leftGutter, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = textMeasurer.measure(numberFormat.format(yValue), style = TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(label, topLeft = Offset(Y_LABEL_INSET.toPx(), y - label.size.height / 2f))
                yValue += yStep
            }

            // // Dashed low/high target lines.
            // val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            // drawLine(
            //     color = lowColor,
            //     start = Offset(leftGutter, yFor(lowMark)),
            //     end = Offset(size.width, yFor(lowMark)),
            //     strokeWidth = 1.5.dp.toPx(),
            //     pathEffect = dash,
            // )
            // drawLine(
            //     color = highColor,
            //     start = Offset(leftGutter, yFor(highMark)),
            //     end = Offset(size.width, yFor(highMark)),
            //     strokeWidth = 1.5.dp.toPx(),
            //     pathEffect = dash,
            // )

            // Time grid lines; spacing adapts to keep lines roughly a finger apart
            val tickIntervalMillis = chooseTimeGridInterval(viewportDurationMillis, chartWidth)
            var tick = (viewportStartMillis / tickIntervalMillis) * tickIntervalMillis
            if (tick < viewportStartMillis) tick += tickIntervalMillis
            while (tick <= viewportEndMillis) {
                val x = xFor(tick)
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, iobBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = textMeasurer.measure(dateUtil.timeString(tick), style = TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(label, topLeft = Offset(x - label.size.width / 2f, iobBottom + BOTTOM_LABEL_GAP.toPx()))
                tick += tickIntervalMillis
            }

            // Current time line.
            if (nowTimestamp in viewportStartMillis..viewportEndMillis) {
                val x = xFor(nowTimestamp)
                drawLine(
                    color = labelColor.copy(alpha = 0.7f),
                    start = Offset(x, 0f),
                    end = Offset(x, iobBottom),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Basal strip: 0 U/hr at the top, filled area grows downward.
            val visibleBasal = basalSegments.sliceByMillis(viewportStartMillis, viewportEndMillis) { it.timestamp }
            if (visibleBasal.size >= 2) {
                val stripTop = 0f
                val stripBottom = basalStripPx

                fun basalYFor(rate: Double): Float {
                    val fraction = (rate / basalCeiling).coerceIn(0.0, 1.0)
                    return stripTop + (fraction * (stripBottom - stripTop)).toFloat()
                }

                val fillPath = Path()
                val linePath = Path()
                visibleBasal.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp)
                    val y = basalYFor(point.value)
                    if (index == 0) {
                        fillPath.moveTo(x, stripTop)
                        fillPath.lineTo(x, y)
                        linePath.moveTo(x, y)
                    } else {
                        // Step: hold the previous rate until this transition.
                        val prevY = basalYFor(visibleBasal[index - 1].value)
                        fillPath.lineTo(x, prevY)
                        linePath.lineTo(x, prevY)
                        fillPath.lineTo(x, y)
                        linePath.lineTo(x, y)
                    }
                }
                val lastX = xFor(visibleBasal.last().timestamp.coerceAtMost(viewportEndMillis))
                fillPath.lineTo(lastX, stripTop)
                fillPath.close()
                drawPath(fillPath, color = basalColor.copy(alpha = 0.3f))
                drawPath(linePath, color = basalColor, style = Stroke(width = 1.5.dp.toPx()))
            }

            // Predictions (forecast) as dashed lines per type.
            val predictionDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            visiblePredictions.groupBy(BgDataPoint::type).forEach { (type, values) ->
                val color = predictionColors[type] ?: return@forEach
                val points = values
                    .filter { it.timestamp in viewportStartMillis..viewportEndMillis }
                    .map { Offset(xFor(it.timestamp), yFor(it.value)) }
                if (points.size >= 2) {
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(line, color = color.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), pathEffect = predictionDash))
                }
            }

            // Target line (temp target or profile target midpoint). Include the bounding points so
            // the step line spans the full width at any scroll position instead of vanishing when
            // both compressed endpoints fall outside the viewport.
            val visibleTargets = targetPointsAsc.sliceByMillis(viewportStartMillis, viewportEndMillis, includeBounds = true) { it.timestamp }
            if (visibleTargets.size >= 2) {
                val path = Path()
                visibleTargets.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp)
                    val y = yFor(point.value)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, yFor(visibleTargets[index - 1].value))
                        path.lineTo(x, y)
                    }
                }
                // Clip to the glucose plot area so the bounding points that lie outside the viewport
                // do not paint over the Y-axis labels, the basal strip, or the time-axis labels.
                clipRect(left = leftGutter, top = glucoseTop, right = size.width, bottom = glucoseBottom) {
                    drawPath(path, color = targetColor.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx()))
                }
            }

            // Glucose line and colored dots.
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = Offset(xFor(a.timestamp), yFor(a.value)),
                    end = Offset(xFor(b.timestamp), yFor(b.value)),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            sorted.forEach { reading ->
                drawCircle(
                    color = rangeColor(reading.range),
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(reading.timestamp), yFor(reading.value)),
                )
            }

            // Bolus markers sit just above the BG curve at the time of the dose.
            boluses.filter { it.timestamp in viewportStartMillis..viewportEndMillis }.forEach { bolus ->
                val x = xFor(bolus.timestamp)
                val nearbyValue = sorted.minByOrNull { abs(it.timestamp - bolus.timestamp) }?.value
                val curveY = nearbyValue?.let { yFor(it) } ?: (glucoseTop + BOLUS_MARKER_TOP_MARGIN.toPx())
                val apexY = (curveY - 14.dp.toPx()).coerceAtLeast(glucoseTop + BOLUS_MARKER_TOP_MARGIN.toPx())
                val markerPath = Path().apply {
                    moveTo(x - 4.dp.toPx(), apexY - 8.dp.toPx())
                    lineTo(x + 4.dp.toPx(), apexY - 8.dp.toPx())
                    lineTo(x, apexY)
                    close()
                }
                drawPath(markerPath, color = bolusColor)
                if (bolus.amount >= BOLUS_VALUE_THRESHOLD_UNITS) {
                    val amountLabel = textMeasurer.measure(bolus.label, style = TextStyle(fontSize = 9.sp, color = bolusColor))
                    drawText(
                        amountLabel,
                        topLeft = Offset(x - amountLabel.size.width / 2f, apexY - 8.dp.toPx() - amountLabel.size.height - 2.dp.toPx()),
                    )
                }
            }

            // Carb markers: triangle below the nearest reading, sized by grams.
            carbEntries.filter { it.timestamp in viewportStartMillis..viewportEndMillis }.forEach { carb ->
                val x = xFor(carb.timestamp)
                val grams = carb.amount
                val nearbyValue = sorted.minByOrNull { abs(it.timestamp - carb.timestamp) }?.value
                val centerY = nearbyValue?.let { yFor(it) }?.plus(20.dp.toPx()) ?: (glucoseBottom - 20.dp.toPx())
                val width = minOf(6.0 + grams * 0.25, 20.0).dp.toPx()
                val markerHeight = width * 0.9f
                val gramsLabel = textMeasurer.measure(carb.label, style = TextStyle(fontSize = 9.sp, color = carbColor))
                val topY = (centerY - markerHeight / 2f)
                    .coerceAtMost(glucoseBottom - markerHeight - gramsLabel.size.height - 2.dp.toPx())
                    .coerceAtLeast(glucoseTop)
                val markerPath = Path().apply {
                    moveTo(x, topY)
                    lineTo(x + width / 2f, topY + markerHeight)
                    lineTo(x - width / 2f, topY + markerHeight)
                    close()
                }
                drawPath(markerPath, color = carbColor)
                drawText(gramsLabel, topLeft = Offset(x - gramsLabel.size.width / 2f, topY + markerHeight + 1.dp.toPx()))
            }

            // COB curve in the IOB strip, on its own grams scale, drawn under IOB.
            val cobPoints = cobPointsAsc.sliceByMillis(viewportStartMillis, viewportEndMillis) { it.timestamp }
            val maxCobGrams = cobPoints.maxOfOrNull { it.value } ?: 0.0
            if (cobPoints.size >= 2 && maxCobGrams > 0.0) {
                val cobScale = maxCobGrams.coerceAtLeast(10.0)
                val fillPath = Path()
                val linePath = Path()
                cobPoints.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp)
                    val fraction = (point.value / cobScale).coerceIn(0.0, 1.0)
                    val y = iobBottom - (fraction * (iobBottom - iobTop)).toFloat()
                    if (index == 0) {
                        fillPath.moveTo(x, iobBottom)
                        fillPath.lineTo(x, y)
                        linePath.moveTo(x, y)
                    } else {
                        fillPath.lineTo(x, y)
                        linePath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(xFor(cobPoints.last().timestamp), iobBottom)
                fillPath.close()
                drawPath(fillPath, color = cobColor.copy(alpha = 0.2f))
                drawPath(linePath, color = cobColor, style = Stroke(width = 1.5.dp.toPx()))
            }

            // IOB curve.
            val iobPoints = iobPointsAsc.sliceByMillis(viewportStartMillis, viewportEndMillis) { it.timestamp }
            if (iobPoints.size >= 2) {
                val maxIob = iobPoints.maxOf { it.value }.coerceAtLeast(0.5)
                fun iobYFor(units: Double): Float {
                    val fraction = (units / maxIob).coerceIn(0.0, 1.0)
                    return iobBottom - (fraction * (iobBottom - iobTop)).toFloat()
                }
                val fillPath = Path()
                val linePath = Path()
                iobPoints.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp)
                    val y = iobYFor(point.value)
                    if (index == 0) {
                        fillPath.moveTo(x, iobBottom)
                        fillPath.lineTo(x, y)
                        linePath.moveTo(x, y)
                    } else {
                        fillPath.lineTo(x, y)
                        linePath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(xFor(iobPoints.last().timestamp), iobBottom)
                fillPath.close()
                drawPath(fillPath, color = iobColor.copy(alpha = 0.35f))
                drawPath(linePath, color = iobColor, style = Stroke(width = 1.5.dp.toPx()))
            }

            // Inspected point: a line through all bands and a highlighted dot on the reading.
            selectedReading?.let { reading ->
                val selectionX = xFor(reading.timestamp)
                if (selectionX in leftGutter..size.width) {
                    drawLine(
                        color = labelColor.copy(alpha = 0.9f),
                        start = Offset(selectionX, 0f),
                        end = Offset(selectionX, iobBottom),
                        strokeWidth = 2.dp.toPx(),
                    )
                    val dotCenter = Offset(selectionX, yFor(reading.value))
                    drawCircle(rangeColor(reading.range), radius = 7.5.dp.toPx(), center = dotCenter)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = dotCenter)
                }
            }

            // Selected bolus: a vertical marker line so the tapped dose is easy to spot.
            selectedBolus?.let { bolus ->
                val selectionX = xFor(bolus.timestamp)
                if (selectionX in leftGutter..size.width) {
                    drawLine(
                        color = bolusColor.copy(alpha = 0.9f),
                        start = Offset(selectionX, 0f),
                        end = Offset(selectionX, iobBottom),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }

        selectedReading?.let { reading ->
            ChartSelectionPill(
                time = dateUtil.timeString(reading.timestamp),
                glucose = numberFormat.format(reading.value),
                glucoseColor = rangeColor(reading.range),
                iob = selectedIob?.let { numberFormat.format(it) + " U" } ?: "–",
                cob = selectedCob?.let { "${it.roundToInt()} g" } ?: "–",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            )
        }

        selectedBolus?.let { bolus ->
            ChartBolusPill(
                time = dateUtil.timeString(bolus.timestamp),
                bolus = bolus.label,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            )
        }

        // Info button overlaid at the top-end of the graph.
        IconButton(
            onClick = { showPredictionInfo = true },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.trio_graph_prediction_info),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (showPredictionInfo) {
        PredictionLegendBottomSheet(onDismiss = { showPredictionInfo = false })
    }
}

private fun chooseYGridStep(span: Double): Double = when {
    span <= 8.0   -> 1.0
    span <= 20.0  -> 2.0
    span <= 120.0 -> 20.0
    else          -> 50.0
}

// Candidate spacings for the vertical time grid, smallest first.
private val TIME_GRID_INTERVALS_MILLIS = longArrayOf(
    5 * 60_000L,
    15 * 60_000L,
    30 * 60_000L,
    HOUR_MILLIS,
    2 * HOUR_MILLIS,
    4 * HOUR_MILLIS,
    8 * HOUR_MILLIS,
    12 * HOUR_MILLIS,
    24 * HOUR_MILLIS,
)

// Pick the smallest interval that keeps grid lines at least ~120px apart for the current zoom.
private fun chooseTimeGridInterval(durationMillis: Long, widthPx: Float): Long {
    val minimumInterval = (durationMillis * (120f / widthPx.coerceAtLeast(1f))).toLong()
    return TIME_GRID_INTERVALS_MILLIS.firstOrNull { it >= minimumInterval } ?: TIME_GRID_INTERVALS_MILLIS.last()
}

/** What a tap on the chart has selected: a glucose reading or a bolus dose. */
private sealed interface ChartTapSelection {
    data class Bg(val reading: BgDataPoint) : ChartTapSelection
    data class Bolus(val bolus: BolusGraphPoint) : ChartTapSelection
}
