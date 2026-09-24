package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.pump.omnipod.common.R

/**
 * Full-screen content that hosts the Omnipod 5 credential import WebView (see
 * [O5CredentialWebViewScreen]). The screen loads [url], waits for the page to post the
 * credential JSON back through the message bridge, and imports it via [onImportCredential].
 *
 * - On a successful import [onImported] is called, which opens the Omnipod 5 setup wizard.
 * - On a failed import a "Try again" button is shown that re-launches the WebView (reloads
 *   [url] from scratch).
 */
@Composable
fun O5CredentialImportWebViewScreen(
    url: String,
    onImportCredential: (String) -> Boolean,
    onImported: () -> Unit,
    onFailed: (Throwable) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (failed) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.omnipod_5_credential_import_failed),
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
                    Text(stringResource(R.string.omnipod_5_credential_import_try_again))
                }
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
