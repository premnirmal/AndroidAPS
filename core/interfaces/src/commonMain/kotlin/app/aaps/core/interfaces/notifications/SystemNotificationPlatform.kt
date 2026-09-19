package app.aaps.core.interfaces.notifications

/**
 * The part of notification handling that only the operating system can do.
 *
 * Everything about *which* notifications exist - posting, replacing, expiry, and dismissal - is
 * plain logic and lives in the shared notification manager. This interface owns platform display.
 */
interface SystemNotificationPlatform {

    /**
     * Show the system notification for [notification], replacing any earlier one with its
     * `instanceKey`.
     *
     * The whole record is passed rather than a chosen few fields, because what to show is a
     * platform decision and the shared registry should not decide it in advance.
     *
     * [title] is resolved by the registry because it needs a `TextResolver`, which is not something
     * a platform implementation should have to carry.
     *
     * An implementation may legitimately decide to show nothing at all.
     */
    fun show(notification: AapsNotification, title: String)

    /** Take one notification out of the system tray. Does nothing when it is not there. */
    fun cancel(instanceKey: Int)

    /** Take every notification this app posted out of the system tray. */
    fun cancelAll()

    /**
     * Register interest in dismissals the user made outside the app, by swiping the system
     * notification away. Called once during start up.
     */
    fun onDismissed(callback: (instanceKey: Int) -> Unit)
}
