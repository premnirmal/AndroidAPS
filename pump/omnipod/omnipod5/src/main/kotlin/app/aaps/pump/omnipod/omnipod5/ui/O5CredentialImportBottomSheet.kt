package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet that hosts the Omnipod 5 credential import WebView (see
 * [O5CredentialWebViewScreen]). The sheet loads [url], waits for the page to post the
 * credential JSON back through the message bridge, and imports it via [onImportCredential].
 *
 * - On a successful import the sheet is dismissed and [onImported] is called, which returns
 *   the user to the Omnipod 5 setup wizard.
 * - On a failed import a "Try again" button is shown that re-launches the WebView (reloads
 *   [url] from scratch).
 *
 * [onDismiss] is called when the user swipes/taps the sheet away without importing a credential.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun O5CredentialImportBottomSheet(
    url: String,
    onImportCredential: (String) -> Boolean,
    onImported: () -> Unit,
    onFailed: (Throwable) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Login to import Omnipod5 certificate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (failed) {
                Text(
                    text = "Could not import the credential. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        failed = false
                        attempt++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try again")
                }
            } else {
                // key(attempt) recreates the WebView so "Try again" reloads the page.
                key(attempt) {
                    O5CredentialWebViewScreen(
                        url = url,
                        onCredentialReceived = { json ->
                            if (onImportCredential(json)) {
                                onImported()
                            } else {
                                failed = true
                            }
                        },
                        onCredentialError = { throwable ->
                            failed = true
                            onFailed(throwable)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
