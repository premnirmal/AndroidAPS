package app.aaps.implementation.notifications

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.androidNotification.AlarmNotificationManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The Android half of the notification split: which of the three outcomes a notification gets.
 *
 * This is the logic that used to sit inside `NotificationManagerImpl` and could not be reached from
 * a test, because that class also owned the registry. It matters enough to pin: getting it wrong
 * either alerts the user twice for one alarm, or silently drops a notification they asked to see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSystemNotificationPlatformTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences: Preferences = mock()
    private val alarmNotificationManager: AlarmNotificationManager = mock()
    private val notificationHolder: NotificationHolder = mock()
    private val iconsProvider: IconsProvider = mock()

    private lateinit var sut: AndroidSystemNotificationPlatform

    @Before
    fun setUp() {
        whenever(iconsProvider.getIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        whenever(iconsProvider.getNotificationIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        sut = AndroidSystemNotificationPlatform(
            aapsLogger = mock<AAPSLogger>(),
            context = context,
            preferences = preferences,
            iconsProvider = iconsProvider,
            notificationHolder = { notificationHolder },
            alarmNotificationManager = { alarmNotificationManager }
        )
    }

    private fun notification(
        level: NotificationLevel,
        actions: List<NotificationAction> = emptyList()
    ) = AapsNotification(
        id = NotificationId.NEW_VERSION_DETECTED,
        instanceKey = 42,
        text = "text",
        level = level,
        actions = actions
    )

    @Test
    fun `an urgent alarm is posted as a visual alert`() {
        sut.show(notification(NotificationLevel.URGENT), "Urgent")

        verify(alarmNotificationManager).postAlarmNotification(
            notificationKey = eq(42), title = eq("Urgent"), body = eq("text"), urgent = eq(true)
        )
        verify(preferences, never()).get(any<BooleanKey>())
    }

    @Test
    fun `without the preference nothing is shown at all`() {
        whenever(preferences.get(BooleanKey.AlertUrgentAsAndroidNotification)).thenReturn(false)

        sut.show(notification(NotificationLevel.NORMAL), "Info")

        verify(alarmNotificationManager, never()).postAlarmNotification(any(), any(), any(), any())
    }

    @Test
    fun `a notification carrying actions is not shown in the tray`() {
        // Actions are answered in the app, so a tray copy would be a dead end.
        whenever(preferences.get(BooleanKey.AlertUrgentAsAndroidNotification)).thenReturn(true)

        sut.show(notification(NotificationLevel.NORMAL, actions = listOf(mock())), "Info")

        verify(alarmNotificationManager, never()).postAlarmNotification(any(), any(), any(), any())
    }

    @Test
    fun `cancel all clears the alarms but leaves the ongoing notification alone`() {
        // cancelAll() deliberately does not call NotificationManager.cancelAll(): that would also take
        // down the foreground service notification carrying the loop status.
        sut.cancelAll()

        verify(alarmNotificationManager).cancelAlarm()
    }

    @Test
    fun `cancelling one notification clears its alarm notification`() {
        sut.cancel(7)

        verify(alarmNotificationManager).cancelAlarmNotification(7)
    }

    @Test
    fun `a dismissal from the tray is reported with its instance key`() {
        var dismissed: Int? = null
        sut.onDismissed { dismissed = it }

        // Showing is what registers the receiver, so do that first, then post the delete broadcast.
        whenever(preferences.get(BooleanKey.AlertUrgentAsAndroidNotification)).thenReturn(true)
        sut.show(notification(NotificationLevel.NORMAL), "Info")
        context.sendBroadcast(
            Intent(NotificationManager.DISMISS_ACTION)
                .putExtra("instanceKey", 42)
        )
        ShadowLooper.idleMainLooper()

        assertThat(dismissed).isEqualTo(42)
    }
}
