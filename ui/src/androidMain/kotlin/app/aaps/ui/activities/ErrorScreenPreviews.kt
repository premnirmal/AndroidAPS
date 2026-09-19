package app.aaps.ui.activities

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.R

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Pump unreachable",
            status = "Last successful communication 25 minutes ago. Check Bluetooth and pump status.",
            appIcon = R.mipmap.ic_launcher,
            onOk = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenShortPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Bolus error",
            status = "Delivery failed.",
            appIcon = R.mipmap.ic_launcher,
            onOk = {}
        )
    }
}
