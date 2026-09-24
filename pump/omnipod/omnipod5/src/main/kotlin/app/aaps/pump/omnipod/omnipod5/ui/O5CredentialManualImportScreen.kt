package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper

/**
 * Manual credential import screen: lets the user paste a credential string and install it.
 * Reached from the "import" action in the top app bar of
 * [O5CredentialImportScreen]/[O5CertificateStoreScreen]. Purely credential management - no
 * dosing/pairing actions.
 */
@Composable
fun O5CredentialManualImportScreen(
    viewModel: O5CredentialImportViewModel,
    rh: ResourceHelper
) {
    val inputText by viewModel.inputText.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    O5CredentialManualImportContent(
        inputText = inputText,
        inputChanged = viewModel::onInputChanged,
        importInput = viewModel::importCurrentInput,
        importResult = importResult,
    )
}

@Composable
private fun O5CredentialManualImportContent(
    inputText: String,
    inputChanged: (String) -> Unit,
    importInput: () -> Unit,
    importResult: ImportResult,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Import Omnipod 5 Credential",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Paste a credential string obtained from a trusted source. This does not " +
                "pair with a pod by itself - it only makes the credential available for pairing.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = inputChanged,
            label = { Text("Credential string") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            singleLine = false
        )

        Button(
            onClick = importInput,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import")
        }

        when (importResult) {
            is ImportResult.Success -> Text(
                text = "Imported credential for controller 0x%08X".format(importResult.controllerId),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )

            is ImportResult.Failure -> Text(
                text = importResult.reason,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge
            )

            ImportResult.None       -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewManualImportContent() {
    MaterialTheme {
        O5CredentialManualImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importResult = ImportResult.None,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewManualImportFailureContent() {
    MaterialTheme {
        O5CredentialManualImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importResult = ImportResult.Failure("Failed to import cert"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewManualImportSuccessContent() {
    MaterialTheme {
        O5CredentialManualImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importResult = ImportResult.Success(123),
        )
    }
}
