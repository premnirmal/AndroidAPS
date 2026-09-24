package app.aaps.trio.ui.compose.overview

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.withTimeoutOrNull

// As in Trio: a finger resting this long (without moving past the touch slop) starts inspecting.
private const val INSPECT_HOLD_MILLIS = 150L

/**
 * One detector for pan, zoom, fling velocity, double-tap and press-and-hold inspect. Two separate
 * detectors on the same touch stream interfere, and detectTransformGestures exposes neither
 * velocity nor the end of a gesture.
 *
 * Inspect: after a still, single-finger hold, [onInspectStart] fires. The rest of that touch reports
 * only [onInspectMove] (no panning) until the finger lifts or a second finger joins, then
 * [onInspectEnd].
 */
suspend fun PointerInputScope.detectChartGestures(
    onTouchDown: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onFlingVelocity: (velocityPxPerSec: Float) -> Unit,
    onDoubleTap: (position: Offset) -> Unit,
    onTap: (position: Offset) -> Unit,
    onInspectStart: (x: Float) -> Unit,
    onInspectMove: (x: Float) -> Unit,
    onInspectEnd: () -> Unit,
) {
    var lastTapDownMillis = -1L
    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onTouchDown()

        val velocityTracker = VelocityTracker()
        velocityTracker.addPointerInputChange(down)
        var totalMovement = 0f
        var pastSlop = false
        var wasMultiTouch = false
        var inspecting = false
        var inspected = false
        var lastPosition = down.position
        val holdStart = SystemClock.uptimeMillis()

        while (true) {
            val holdPending = !inspecting && !pastSlop && !wasMultiTouch
            val event = if (holdPending) {
                val remaining = INSPECT_HOLD_MILLIS - (SystemClock.uptimeMillis() - holdStart)
                if (remaining <= 0) null else withTimeoutOrNull(remaining) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }

            if (event == null) {
                // Held still long enough: start inspecting.
                inspecting = true
                inspected = true
                onInspectStart(lastPosition.x)
                continue
            }

            if (event.changes.size > 1) wasMultiTouch = true

            if (inspecting) {
                if (wasMultiTouch) {
                    // A second finger means pinch, not inspect.
                    inspecting = false
                    onInspectEnd()
                } else {
                    lastPosition = event.changes.first().position
                    onInspectMove(lastPosition.x)
                    event.changes.forEach { it.consume() }
                    if (!event.changes.any { it.pressed }) break
                    continue
                }
            }

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            totalMovement += panChange.getDistance()
            if (!pastSlop && totalMovement > touchSlop) pastSlop = true
            lastPosition = event.changes.first().position

            if (pastSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
            }
            event.changes.forEach { change ->
                if (change.positionChanged()) {
                    velocityTracker.addPointerInputChange(change)
                }
                change.consume()
            }
            if (!event.changes.any { it.pressed }) break
        }

        if (inspecting) onInspectEnd()
        if (inspected) {
            lastTapDownMillis = -1L
            return@awaitEachGesture
        }

        if (!pastSlop) {
            // A tap, not a drag: check whether it completes a double-tap.
            if (lastTapDownMillis >= 0 && down.uptimeMillis - lastTapDownMillis <= doubleTapTimeoutMillis) {
                onDoubleTap(down.position)
                lastTapDownMillis = -1L
            } else {
                onTap(down.position)
                lastTapDownMillis = down.uptimeMillis
            }
        } else {
            lastTapDownMillis = -1L
            // Velocity is meaningless once a second finger joins, so only fling after a single-finger pan.
            if (!wasMultiTouch) {
                onFlingVelocity(velocityTracker.calculateVelocity().x)
            }
        }
    }
}
