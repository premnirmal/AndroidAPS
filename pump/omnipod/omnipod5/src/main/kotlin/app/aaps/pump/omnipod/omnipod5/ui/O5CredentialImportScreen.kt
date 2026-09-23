package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData

/**
 * Settings screen for importing an Omnipod 5 credential and viewing/removing already-installed
 * ones. No dosing/pairing/connection actions live here - purely credential management, feeding
 * [O5RegistrationData] for whenever actual O5 pairing is attempted elsewhere.
 *
 * Wired in via [app.aaps.pump.omnipod.omnipod5.ui.compose.OmnipodO5ComposeContent] and
 * [app.aaps.pump.omnipod.omnipod5.ui.O5CertificateStoreScreen] - reached from the certificate
 * store subpage of the plugin settings, and auto-routed to from "Activate Pod" when no
 * registration credentials are installed yet.
 */
@Composable
fun O5CredentialImportScreen(
    viewModel: O5CredentialImportViewModel,
    rh: ResourceHelper
) {
    val inputText by viewModel.inputText.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val installedCredentials by viewModel.installedCredentials.collectAsState()
    val showImportFromAssets by viewModel.allowImportFromAssets.collectAsState()

    O5CredentialImportContent(
        inputText = inputText,
        inputChanged = viewModel::onInputChanged,
        importInput = viewModel::importCurrentInput,
        importFromAssets = viewModel::importCredentialFromAssets,
        removeCredential = viewModel::removeCredential,
        importResult = importResult,
        installedCredentials = installedCredentials,
        showImportFromAssets = showImportFromAssets,
    )
}

@Composable
private fun O5CredentialImportContent(
    inputText: String,
    inputChanged: (String) -> Unit,
    importInput: () -> Unit,
    importFromAssets: () -> Unit,
    removeCredential: (Long) -> Unit,
    importResult: ImportResult,
    installedCredentials: List<InstalledCredentialRow>,
    showImportFromAssets: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Omnipod 5 Credential",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (installedCredentials.isEmpty()) {
            if (showImportFromAssets) {
                Text(
                    text = "Credential found in assets, click import to continue",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = importFromAssets,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import from assets")
                }
            } else {
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
            }
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

        if (installedCredentials.isNotEmpty()) {
            Text(
                text = "Installed credentials",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedCredentials.forEach { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Controller 0x%08X".format(row.controllerId),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = row.source.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = { removeCredential(row.controllerId) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportContent() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.None,
            installedCredentials = emptyList(),
            showImportFromAssets = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportContentFromAssets() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.None,
            installedCredentials = emptyList(),
            showImportFromAssets = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportedCredentialsContent() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.None,
            installedCredentials = listOf(InstalledCredentialRow(123, O5RegistrationData.O5RegistrationSource.IMPORTED)),
            showImportFromAssets = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportAssetFailureContent() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.Failure("Failed to import foobar"),
            installedCredentials = emptyList(),
            showImportFromAssets = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportFailureContent() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.Failure("Failed to import cert"),
            installedCredentials = emptyList(),
            showImportFromAssets = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportSuccesssContent() {
    MaterialTheme {
        O5CredentialImportContent(
            inputText = "",
            inputChanged = {},
            importInput = {},
            importFromAssets = {},
            removeCredential = {},
            importResult = ImportResult.Success(123),
            installedCredentials = listOf(InstalledCredentialRow(123, O5RegistrationData.O5RegistrationSource.IMPORTED)),
            showImportFromAssets = false,
        )
    }
}
