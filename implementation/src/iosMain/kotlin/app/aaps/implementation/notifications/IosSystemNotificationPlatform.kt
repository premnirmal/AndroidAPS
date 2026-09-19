package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.IosNotificationDelegate
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionCustomDismissAction
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The system tray half of notifications on iOS.
 *
 * iOS keeps far more of this than Android does. There is no channel to create and no `PendingIntent`
 * to build. The system decides how to present the notification.
 *
 * Permission is requested once, lazily, on the first notification. Asking in the constructor would
 * put the system prompt in front of the user during start up, before anything has explained why the
 * app wants it.
 */
class IosSystemNotificationPlatform(
    private val aapsLogger: AAPSLogger
) : SystemNotificationPlatform {

    /**
     * Instance keys whose notification carries actions and has not been answered yet.
     *
     * These are the ones a swipe must not clear. See [onDismissed] for what went wrong without it.
     * Entries leave only through [cancel] or [cancelAll] - that is, when the notification is
     * genuinely finished with, rather than when the user brushed it off the screen.
     */
    private val unanswered = mutableSetOf<Int>()

    /**
     * Resolved on first use, not in the constructor.
     *
     * `currentNotificationCenter()` needs an app bundle and throws
     * `bundleProxyForCurrentProcess is nil` without one, so constructing this class eagerly made it
     * impossible to build outside a running app - including in a test. Nothing here needs the centre
     * until something is actually posted.
     */
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }
    private var authorizationAsked = false

    /**
     * iOS shows every notification, unlike Android.
     *
     * `AlertUrgentAsAndroidNotification` is not consulted, and that is deliberate: it decides whether
     * AAPS raises an OS notification at all, and on iOS that choice belongs to the user in Settings.
     * The key says so itself now - it is marked Android only.
     *
     * `actions` **is** still ignored, and that one is a real gap rather than a decision - a
     * notification carrying actions reaches the tray here with none of them attached, where Android
     * suppresses it so it can be answered in the app.
     */
    override fun show(notification: AapsNotification, title: String) {
        ensureAuthorization()
        rememberIfUnanswered(notification)
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(notification.text)
            setSound(UNNotificationSound.defaultSound)
            // Without the category the dismiss callback never fires - see onDismissed.
            setCategoryIdentifier(CATEGORY)
            setInterruptionLevel(
                if (notification.level == NotificationLevel.URGENT) UNNotificationInterruptionLevelTimeSensitive
                else UNNotificationInterruptionLevelActive
            )
        }
        // A repeated identifier replaces the delivered notification rather than adding another,
        // which is the behaviour the registry expects when it reposts the same id.
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier(notification.instanceKey),
            content = content,
            trigger = null
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Cannot post notification: $error")
        }
    }

    /**
     * Notes, before posting, that this notification must survive a swipe.
     *
     * Split from [show] only so it can be exercised: [show] reaches `UNUserNotificationCenter`,
     * which needs an app bundle a test binary does not have.
     */
    internal fun rememberIfUnanswered(notification: AapsNotification) {
        if (notification.actions.isNotEmpty()) unanswered += notification.instanceKey
    }

    /** The counterpart: this notification is finished with, however that came about. */
    internal fun forget(instanceKey: Int) {
        unanswered -= instanceKey
    }

    /** Same, for the "dismiss all alarms" path. */
    internal fun forgetAll() {
        unanswered.clear()
    }

    override fun cancel(instanceKey: Int) {
        forget(instanceKey)
        val ids = listOf(identifier(instanceKey))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    /**
     * Only the alarms this class posted, and only the delivered ones.
     *
     * This was `removeAllPendingNotificationRequests()` plus `removeAllDeliveredNotifications()`,
     * and both halves were wrong. This class never creates a pending request at all - every [show]
     * posts with `trigger = null`, so it is delivered straight away - but
     * `IosReminderScheduler` does, and its scheduled reminders are named `aaps-reminder-N`, which
     * sits inside this class's own `aaps-` prefix. Muting alarms therefore deleted every automation
     * reminder the user was still waiting on, and none of them ever rang. The delivered half swept
     * up the loop's `aaps-loop` notification for the same reason.
     *
     * Android draws exactly this line and says why in `AndroidSystemNotificationPlatform.cancelAll`:
     * "Dismiss all alarms" means take *my* alarms out of the tray, not empty the tray. [instanceKeyOf]
     * is what decides ownership here, so a `aaps-reminder-3` or an `aaps-loop` is left alone because
     * neither tail parses to a key.
     */
    override fun cancelAll() {
        // "Dismiss all alarms" is an answer, given deliberately, so these are finished with.
        forgetAll()
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val posted = delivered.orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier }
            val ids = ownIdentifiers(posted)
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    /**
     * The identifiers [cancelAll] is allowed to remove.
     *
     * Split out so it can be checked without a notification centre, which a test binary has no
     * bundle for. The whole safety of [cancelAll] is in this one filter.
     */
    internal fun ownIdentifiers(identifiers: List<String>): List<String> =
        identifiers.filter { instanceKeyOf(it) != null }

    /**
     * Learn about notifications the user swiped away outside the app.
     *
     * Two things are needed, and missing either one makes this silently never fire: a delegate on
     * the shared centre, and a category carrying `customDismissAction` - without that iOS reports
     * taps but not dismissals, which is the trap, because the code looks right and nothing arrives.
     *
     * The delegate is not set here. There is one slot for the whole app and `setDelegate` replaces
     * whatever was in it, so [IosNotificationDelegate] owns it and routes; a second owner would
     * silently stop the first one's callbacks.
     */
    override fun onDismissed(callback: (instanceKey: Int) -> Unit) {
        IosNotificationDelegate.register(setOf(dismissibleCategory())) { actionId, notificationId ->
            if (actionId != UNNotificationDismissActionIdentifier) return@register false
            // A tap is deliberately not a dismissal: the notification goes away, but the user asked
            // to *see* the thing, so it stays in the in-app list.
            val instanceKey = instanceKeyOf(notificationId) ?: return@register true
            if (clearedByDismissal(instanceKey)) callback(instanceKey)
            else aapsLogger.debug(LTag.NOTIFICATION, "Swipe ignored for $instanceKey: it still carries an unanswered action")
            true
        }
    }

    /**
     * Whether swiping this notification away is allowed to count as answering it.
     *
     * No, when it carries actions nobody has used yet - and that case is why this exists.
     *
     * Every notification posted here is swipeable, because the category has to carry
     * `customDismissAction` for dismissals to be reported at all. The registry turns a reported
     * dismissal into `dismiss(handle)`, which drops the notification. On an urgent Nightscout alarm
     * the swipe - the first gesture anyone reaches for - threw away the card holding the three
     * snooze buttons, never acknowledged Nightscout and never recorded a snooze, so the same alarm
     * returned on the next push.
     *
     * Android forbids exactly this rather than handling it: `AlarmNotificationManager` posts with
     * `setOngoing(true)`, and says why - "so the user can't swipe to dismiss". iOS has no ongoing
     * flag, so the guard has to be here instead. The banner does go from the tray, which iOS will
     * not undo; what does not happen is the alarm being treated as answered.
     */
    internal fun clearedByDismissal(instanceKey: Int): Boolean = instanceKey !in unanswered

    private fun dismissibleCategory(): UNNotificationCategory =
        UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY,
            actions = emptyList<Any>(),
            intentIdentifiers = emptyList<Any>(),
            options = UNNotificationCategoryOptionCustomDismissAction
        )

    internal fun identifier(instanceKey: Int) = "$IDENTIFIER_PREFIX$instanceKey"

    /** The reverse of [identifier]. Null for anything this class did not post. */
    internal fun instanceKeyOf(identifier: String): Int? =
        if (identifier.startsWith(IDENTIFIER_PREFIX)) identifier.removePrefix(IDENTIFIER_PREFIX).toIntOrNull()
        else null

    private fun ensureAuthorization() {
        if (authorizationAsked) return
        authorizationAsked = true
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Notification permission failed: $error")
            else aapsLogger.debug(LTag.NOTIFICATION, "Notification permission granted=$granted")
        }
    }

    companion object {

        private const val IDENTIFIER_PREFIX = "aaps-"

        /** Named once: it has to match between the posted content and the registered category. */
        private const val CATEGORY = "aaps-notification"
    }
}
