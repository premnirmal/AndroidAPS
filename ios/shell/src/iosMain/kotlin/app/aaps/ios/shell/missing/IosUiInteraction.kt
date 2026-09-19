package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.ui.UiInteraction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Half real, half placeholder.
 *
 * [stopAlarm] is the real thing: it is the same one line as Android, because `dismissAllAlarms` is on
 * the shared `NotificationManager`.
 *
 * [runAlarm] is not, and it is deliberately left undone rather than guessed at.
 *
 * [mainActivity] and [errorHelperActivity] have no iOS meaning at all. They exist so Android code
 * can build an `Intent`, and every reader of them is androidMain - checked, not assumed. They
 * answer with this class so the type is satisfied; reading either on iOS is a bug in the caller,
 * not a value to rely on.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosUiInteraction(
    private val aapsLogger: AAPSLogger,
    // Provider: the registry builds the notification platform, which would be a cycle back to here.
    private val notificationManager: () -> NotificationManager
) : UiInteraction {

    override val mainActivity: KClass<*> get() = IosUiInteraction::class
    override val errorHelperActivity: KClass<*> get() = IosUiInteraction::class

    override fun runAlarm(status: String, title: String) {
        aapsLogger.notOnIosYet("UiInteraction.runAlarm ($title: $status)")
    }

    /** Real. Clears the registry's alarms and cancels the notifications. */
    override fun stopAlarm(reason: String) {
        notificationManager().dismissAllAlarms()
    }
}
