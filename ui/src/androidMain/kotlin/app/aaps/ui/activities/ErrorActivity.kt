package app.aaps.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.SnackbarHostPresence
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import dev.zacsweers.metro.Inject

/** Full-screen visual alarm UI. */
class ErrorActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var snackbarHostPresence: SnackbarHostPresence
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var iconsProvider: IconsProvider
    private var status by mutableStateOf("")
    private var title by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FSI-launched alarms must be able to wake the screen and show over the lock screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        status = intent.getStringExtra(AlarmIntent.EXTRA_STATUS) ?: ""
        title = intent.getStringExtra(AlarmIntent.EXTRA_TITLE) ?: ""
        val appIcon = iconsProvider.getIcon()

        aapsLogger.debug("Error activity displayed: $title - $status")

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil,
                LocalConfig provides config,
                LocalSnackbarHostState provides snackbarHostState
            ) {
                AapsTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            ErrorScreen(
                                title = title,
                                status = status,
                                appIcon = appIcon,
                                onOk = {
                                    uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
                                    stopAlarm("Dismiss")
                                    finish()
                                }
                            )
                        }
                        GlobalSnackbarHost(
                            rxBus = rxBus,
                            snackbarHostPresence = snackbarHostPresence,
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        status = intent.getStringExtra(AlarmIntent.EXTRA_STATUS) ?: ""
        title = intent.getStringExtra(AlarmIntent.EXTRA_TITLE) ?: ""
        aapsLogger.debug("Error activity updated: $title - $status")
    }

    private fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        uiInteraction.stopAlarm(reason)
    }
}
