package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.ui.R
import app.aaps.core.interfaces.R as CoreInterfacesR
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrioAddActionsSheet(
    onDismiss: () -> Unit,
    onBolusClick: () -> Unit,
    onCarbsClick: () -> Unit,
    onWizardClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AapsSpacing.extraLarge + AapsSpacing.small,
                    vertical = AapsSpacing.large
                ),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
        ) {
            Text(
                text = stringResource(R.string.trio_add_action),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = onBolusClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Bloodtype, contentDescription = null)
                Text(
                    text = stringResource(CoreInterfacesR.string.bolus),
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
            Button(
                onClick = onCarbsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Restaurant, contentDescription = null)
                Text(
                    text = stringResource(CoreInterfacesR.string.carbs),
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
            Button(
                onClick = onWizardClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Calculate, contentDescription = null)
                Text(
                    text = stringResource(CoreUiR.string.boluswizard),
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrioAddActionsSheetPreview() {
    AapsTheme {
        TrioAddActionsSheet(
            onDismiss = {},
            onBolusClick = {},
            onCarbsClick = {},
            onWizardClick = {}
        )
    }
}
