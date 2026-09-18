package app.aaps.core.interfaces.notifications

/**
 * Intent extras used to carry alarm payload from notification PendingIntents into ErrorActivity.
 * Lives in core/interfaces so both the activity (ui module) and the notification builder
 * (implementation module) can reference the same keys without a cross-module dependency.
 */
object AlarmIntent {

    /** Alarm status / body text. */
    const val EXTRA_STATUS = "status"

    /** Alarm title. */
    const val EXTRA_TITLE = "title"

}
