package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.alerts.IosReminderScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The identifier round trip, which is the one piece of real logic in the iOS platform.
 *
 * Everything else here hands work to `UNUserNotificationCenter` and cannot be checked without a
 * person swiping a notification away. This part can: a dismissal arrives as the identifier string
 * that was posted, and turning it back into an instance key is what connects the two. Get it wrong
 * and dismissals are silently ignored, because the registry is asked to drop a key that never
 * existed.
 */
class IosSystemNotificationPlatformTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private val platform = IosSystemNotificationPlatform(SilentLogger)

    @Test
    fun `an instance key survives the round trip`() {
        assertEquals(42, platform.instanceKeyOf(platform.identifier(42)))
    }

    /** Instance keys for the multi-instance ids start at 10000 and climb. */
    @Test
    fun `a large instance key survives the round trip`() {
        assertEquals(10_001, platform.instanceKeyOf(platform.identifier(10_001)))
    }

    /** Another app's notification, or one this class never posted, must not map to a key. */
    @Test
    fun `an identifier without our prefix is not ours`() {
        assertNull(platform.instanceKeyOf("42"))
        assertNull(platform.instanceKeyOf("other-app-42"))
    }

    /** The prefix alone, or a non-numeric tail, is not a key either. */
    @Test
    fun `a malformed identifier is not a key`() {
        assertNull(platform.instanceKeyOf("aaps-"))
        assertNull(platform.instanceKeyOf("aaps-abc"))
    }

    // ---------------------------------------------------------------------------------------------
    // A swipe must not answer an alarm
    // ---------------------------------------------------------------------------------------------

    private fun notification(instanceKey: Int, withActions: Boolean) = AapsNotification(
        id = NotificationId.NS_ALARM,
        instanceKey = instanceKey,
        text = "test",
        level = NotificationLevel.URGENT,
        actions = if (withActions) listOf(NotificationAction(TextRef.Literal("Snooze")) {}) else emptyList()
    )

    /**
     * The reason this guard exists.
     *
     * Every notification posted here is swipeable - the category must carry `customDismissAction` or
     * dismissals are never reported at all - and the registry turns a reported dismissal into
     * `dismiss(handle)`, which drops the notification. On an urgent Nightscout alarm that meant the
     * swipe threw away the card with the snooze buttons and never acknowledged Nightscout. Android
     * forbids the gesture outright with
     * `setOngoing(true)`; iOS has no such flag, so it is refused here instead.
     */
    @Test
    fun `a swipe does not answer a notification carrying actions`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))

        assertFalse(platform.clearedByDismissal(7))
    }

    /** Nothing to lose when there is no action to lose, so the swipe means what it looks like. */
    @Test
    fun `a swipe clears a notification with no actions`() {
        platform.rememberIfUnanswered(notification(7, withActions = false))

        assertTrue(platform.clearedByDismissal(7))
    }

    /** An unknown key was never posted by this class, or was already dealt with. */
    @Test
    fun `a swipe clears a notification this class is not holding`() {
        assertTrue(platform.clearedByDismissal(99))
    }

    /** Answering it in the app cancels it, and then the guard has to let go. */
    @Test
    fun `once cancelled the guard releases the key`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))
        platform.forget(7)

        assertTrue(platform.clearedByDismissal(7))
    }

    /** "Dismiss all alarms" is a deliberate answer, unlike a swipe. */
    @Test
    fun `dismiss all releases every held key`() {
        platform.rememberIfUnanswered(notification(7, withActions = true))
        platform.rememberIfUnanswered(notification(8, withActions = true))
        platform.forgetAll()

        assertTrue(platform.clearedByDismissal(7))
        assertTrue(platform.clearedByDismissal(8))
    }

    // ---------------------------------------------------------------------------------------------
    // What "dismiss all alarms" is allowed to remove
    // ---------------------------------------------------------------------------------------------

    /**
     * The reminder prefix is read from the scheduler itself rather than written out here, so renaming
     * it cannot quietly re-open the hole this test exists for.
     */
    @Test
    fun `dismiss all leaves a scheduled automation reminder alone`() {
        val reminder = "${IosReminderScheduler.IDENTIFIER_PREFIX}3"

        // The trap: the reminder id starts with this class's own "aaps-" prefix, so anything cruder
        // than the key parse - a prefix match, or the removeAll* pair this used to call - deletes a
        // reminder the user is still waiting on, and it never rings.
        assertEquals(listOf("aaps-42"), platform.ownIdentifiers(listOf("aaps-42", reminder)))
    }

    /** `IosLoopNotifier.NOTIFICATION_ID`, spelled out because it lives in another module. */
    @Test
    fun `dismiss all leaves the loop notification alone`() {
        assertEquals(emptyList<String>(), platform.ownIdentifiers(listOf("aaps-loop")))
    }

    @Test
    fun `dismiss all removes the alarms this class posted`() {
        val ours = listOf(platform.identifier(1), platform.identifier(10_001))

        assertEquals(ours, platform.ownIdentifiers(ours))
    }

    @Test
    fun `dismiss all leaves another app's notification alone`() {
        assertEquals(emptyList<String>(), platform.ownIdentifiers(listOf("other-app-42", "42")))
    }

}
