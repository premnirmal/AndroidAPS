package app.aaps.implementation.androidNotification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Builds silent, visual Android notifications for AAPS alarms. */
@SingleIn(AppScope::class)
@Inject
class AlarmNotificationManager(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val iconsProvider: IconsProvider,
    private val uiInteractionProvider: () -> UiInteraction
) {

    companion object {

        const val GROUP_ID = "aaps_alarm_group"
        const val CHANNEL_ALARM_VISUAL = "aaps_alarm_visual_v1"
        const val NOTIFICATION_ID_FULL_SCREEN = 4712
        const val ALARM_ID_OFFSET = 100_000

        private const val WAKE_REQUEST_CODE = 4713
        private const val SCREEN_WAKE_DELAY_MS = 1_500L
        private val LEGACY_SOUND_CHANNELS = listOf(
            "aaps_alarm_fullscreen",
            "aaps_alarm_fullscreen_silent",
            "aaps_alarm_alarm_alarm",
            "aaps_alarm_alarm_notify",
            "aaps_alarm_boluserror_alarm",
            "aaps_alarm_boluserror_notify",
            "aaps_alarm_error_alarm",
            "aaps_alarm_error_notify",
            "aaps_alarm_urgentalarm_alarm",
            "aaps_alarm_urgentalarm_notify"
        )
    }

    private val mgr: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val activeAlarmKeys: MutableSet<Int> = mutableSetOf()
    private val channels: Unit by lazy { createChannels() }

    private fun createChannels() {
        mgr.createNotificationChannelGroup(NotificationChannelGroup(GROUP_ID, "AAPS Alarms"))
        LEGACY_SOUND_CHANNELS.forEach(mgr::deleteNotificationChannel)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM_VISUAL,
                "Urgent alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
                group = GROUP_ID
                description = "Visual, heads-up, and vibration alerts for urgent alarms."
            }
        )
    }

    private fun openAppPendingIntent(): PendingIntent? {
        val mainActivity = uiInteractionProvider().mainActivity
        return TaskStackBuilder.create(context).run {
            addParentStack(mainActivity.java)
            addNextIntent(Intent(context, mainActivity.java))
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    fun postFullScreenAlarm(status: String, title: String) {
        channels
        val intent = Intent(context, uiInteractionProvider().errorHelperActivity.java).apply {
            putExtra(AlarmIntent.EXTRA_STATUS, status)
            putExtra(AlarmIntent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_FULL_SCREEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM_VISUAL)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(title)
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        try {
            mgr.notify(NOTIFICATION_ID_FULL_SCREEN, notification)
            aapsLogger.debug(LTag.NOTIFICATION, "Posted full-screen alarm: $title - $status")
        } catch (ex: SecurityException) {
            aapsLogger.error(
                LTag.NOTIFICATION,
                "Failed to post full-screen alarm \"$title\" — POST_NOTIFICATIONS likely revoked",
                ex
            )
        }

        scheduleScreenWakeAndLaunch(pendingIntent)
    }

    private fun scheduleScreenWakeAndLaunch(activityPendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + SCREEN_WAKE_DELAY_MS
        val show = PendingIntent.getActivity(
            context,
            WAKE_REQUEST_CODE,
            Intent(context, uiInteractionProvider().mainActivity.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val wakeOperation = PendingIntent.getBroadcast(
            context,
            WAKE_REQUEST_CODE,
            Intent(context, AlarmScreenWakeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), wakeOperation)
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), activityPendingIntent)
        aapsLogger.debug(LTag.NOTIFICATION, "Scheduled screen-wake and activity launch in ${SCREEN_WAKE_DELAY_MS}ms")
    }

    fun postAlarmNotification(
        notificationKey: Int,
        title: String,
        body: String,
        urgent: Boolean
    ) {
        channels
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_VISUAL)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, iconsProvider.getIcon()))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent())
        builder.setVibrate(
            if (urgent) longArrayOf(1000, 1000, 1000, 1000)
            else longArrayOf(0, 100, 50, 100, 50)
        )

        val systemId = ALARM_ID_OFFSET + notificationKey
        try {
            synchronized(activeAlarmKeys) {
                mgr.notify(systemId, builder.build())
                activeAlarmKeys.add(notificationKey)
            }
            aapsLogger.debug(LTag.NOTIFICATION, "Posted alarm notification key=$notificationKey: $title - $body")
        } catch (ex: SecurityException) {
            aapsLogger.error(
                LTag.NOTIFICATION,
                "Failed to post alarm notification \"$title\" key=$notificationKey — POST_NOTIFICATIONS likely revoked",
                ex
            )
        }
    }

    fun cancelAlarmNotification(notificationKey: Int) {
        val cancelled = synchronized(activeAlarmKeys) {
            if (activeAlarmKeys.remove(notificationKey)) {
                mgr.cancel(ALARM_ID_OFFSET + notificationKey)
                true
            } else {
                false
            }
        }
        if (cancelled) aapsLogger.debug(LTag.NOTIFICATION, "Cancelled alarm notification key=$notificationKey")
    }

    fun cancelAlarm() {
        mgr.cancel(NOTIFICATION_ID_FULL_SCREEN)
        synchronized(activeAlarmKeys) {
            activeAlarmKeys.forEach { mgr.cancel(ALARM_ID_OFFSET + it) }
            activeAlarmKeys.clear()
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Cancelled all AAPS alarm notifications")
    }
}
