package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme

/**
 * How today's readings are spread over the glucose ranges, in percent of all readings.
 *
 * The five shares add up to 100. The low mark and the high mark come from the user
 * preferences, the very low and very high limits are fixed.
 *
 * @param veryLowPercent Share below the very low limit
 * @param lowPercent Share between the very low limit and the low mark
 * @param inRangePercent Share between the low mark and the high mark
 * @param highPercent Share between the high mark and the very high limit
 * @param veryHighPercent Share above the very high limit
 */
data class TimeInRangeToday(
    val veryLowPercent: Double,
    val lowPercent: Double,
    val inRangePercent: Double,
    val highPercent: Double,
    val veryHighPercent: Double
)

/** Parts not larger than this share of the bar are left out, they would only be a thin line. */
private const val MIN_VISIBLE_PERCENT = 0.5

/**
 * Bar showing how today's time is spread over the glucose ranges, from very low on the left
 * to very high on the right. Every part is drawn with rounded ends and a small gap to the next
 * one. When there is no reading yet, an empty track is drawn instead.
 *
 * The colours are the shared glucose range colours, so the bar matches the glucose chart and
 * the statistics screen.
 *
 * @param timeInRange Today's shares, or null when there is no reading yet
 * @param modifier Modifier for the bar
 * @param height Height of the bar
 */
@Composable
fun TimeInRangeDistributionBar(
    timeInRange: TimeInRangeToday?,
    modifier: Modifier = Modifier,
    height: Dp = AapsSpacing.small
) {
    val colors = AapsTheme.generalColors
    val parts = timeInRange?.let {
        listOf(
            it.veryLowPercent to colors.bgVeryLow,
            it.lowPercent to colors.bgLow,
            it.inRangePercent to colors.bgInRange,
            it.highPercent to colors.bgHigh,
            it.veryHighPercent to colors.bgVeryHigh
        ).filter { (percent, _) -> percent > MIN_VISIBLE_PERCENT }
    } ?: emptyList()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        if (parts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            return@Row
        }
        parts.forEach { (percent, color) ->
            Box(
                modifier = Modifier
                    .weight(percent.toFloat())
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
