package app.aaps.core.interfaces.ui

import kotlin.reflect.KClass

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx.java))
 */
interface UiInteraction {

    /** The main activity of the application. */
    val mainActivity: KClass<*>

    /** The activity for displaying error information. */
    val errorHelperActivity: KClass<*>

    /**
     * Show ErrorHelperActivity.
     * @param status message inside dialog
     * @param title title of dialog
     */
    fun runAlarm(status: String, title: String)

    /**
     * Dismisses the current alarm UI and notifications.
     * Per-AAPS-notification cancellation happens internally inside the implementation module.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)
}
