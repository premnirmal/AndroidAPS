package app.aaps.trio.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.compose.AapsTheme

/** Readout for the point being inspected on the chart: time, glucose, IOB and COB. */
@Composable
fun ChartSelectionPill(
    time: String,
    glucose: String,
    glucoseColor: Color,
    iob: String,
    cob: String,
    modifier: Modifier = Modifier,
) {
    val insulinColor = AapsTheme.generalColors.trioInsulin
    val carbColor = AapsTheme.generalColors.cobPrediction
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PillItem(Icons.Filled.WaterDrop, glucose, glucoseColor)
            PillItem(Icons.Filled.Vaccines, iob, insulinColor)
            PillItem(Icons.Filled.Restaurant, cob, carbColor)
        }
    }
}

@Composable
private fun PillItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Readout for a bolus dose tapped on the chart: time and insulin amount. */
@Composable
fun ChartBolusPill(
    time: String,
    bolus: String,
    modifier: Modifier = Modifier,
) {
    val insulinColor = AapsTheme.generalColors.trioInsulin
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PillItem(Icons.Filled.Vaccines, bolus, insulinColor)
        }
    }
}
