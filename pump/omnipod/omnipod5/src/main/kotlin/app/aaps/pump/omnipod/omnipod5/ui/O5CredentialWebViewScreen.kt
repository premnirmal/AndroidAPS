package app.aaps.pump.omnipod.omnipod5.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Name the web page uses to reach the app bridge: the page calls
 * `bridge.postMessage(jsonString)` to hand back the Omnipod 5 credential.
 */
private const val BRIDGE_NAME = "aapsKeymanagerBridge"

/**
 * Origins the bridge accepts messages from. "*" allows every origin, which is fine for the
 * stubbed URL; tighten this once the real credential page URL is known.
 */
private val ALLOWED_ORIGIN_RULES = setOf("*")

/**
 * Full-screen WebView shown instead of the manual credential import screen when no Omnipod 5
 * credential is installed yet. It loads [url] (a pairing/credential page) and listens for a
 * single message posted through `bridge.postMessage(...)` using
 * [WebViewCompat.addWebMessageListener]. The received message is expected to be the credential
 * JSON string; it is handed back through [onCredentialReceived].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun O5CredentialWebViewScreen(
    url: String,
    onCredentialReceived: (String) -> Unit,
    onCredentialError: (Throwable) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnCredentialReceived by rememberUpdatedState(onCredentialReceived)
    val currentOnCredentialError by rememberUpdatedState(onCredentialError)

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "This device's WebView does not support the credential bridge. " +
                    "Please update Android System WebView and try again.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Keep navigation inside the WebView instead of opening an external browser.
                webViewClient = WebViewClient()
                try {
                    WebViewCompat.addWebMessageListener(
                        this,
                        BRIDGE_NAME,
                        ALLOWED_ORIGIN_RULES
                    ) { _, message, _, _, _ ->
                        if (message.type == WebMessageCompat.TYPE_STRING) {
                            message.data?.let { currentOnCredentialReceived(it) }
                        }
                    }
                    loadUrl(url)
                } catch (e: UnsupportedOperationException) {
                    currentOnCredentialError(e)
                }
            }
        }
    )
}
