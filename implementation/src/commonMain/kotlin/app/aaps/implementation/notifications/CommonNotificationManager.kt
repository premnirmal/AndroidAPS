package app.aaps.implementation.notifications

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The notification registry, with nothing platform specific in it.
 *
 * This is the half of notification handling that is the same everywhere: which notifications are
 * live, which replaces which, and when they expire. The system tray is reached through
 * [SystemNotificationPlatform].
 *
 * Both platforms use this now. `NotificationManagerImpl` is gone: Android keeps its channel,
 * receiver and `NotificationCompat` code in `AndroidSystemNotificationPlatform`, and iOS in
 * `IosSystemNotificationPlatform`. There is one registry.
 */
class CommonNotificationManager(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val platform: SystemNotificationPlatform,
    scope: CoroutineScope
) : NotificationManager {

    private val _notifications = MutableStateFlow<List<AapsNotification>>(emptyList())
    override val notifications: StateFlow<List<AapsNotification>> = _notifications.asStateFlow()

    /**
     * Guards every read-modify-write of [_notifications].
     *
     * The Android class says this with `@Synchronized`, which does not exist in common code. The
     * lock is not reentrant, so private helpers state in their own docs that the caller must hold
     * it - they never take it themselves.
     */
    private val lock = AapsLock()

    /** Only ever touched under [lock], so a plain Int is enough. */
    private var nextInstanceKey = 10_000

    init {
        platform.onDismissed { instanceKey -> dismiss(NotificationHandle(instanceKey)) }

        // Periodic cleanup for expiration when no new posts arrive.
        scope.launch {
            while (true) {
                delay(30_000L)
                cleanUp()
            }
        }
    }

    override fun cleanUp() = lock.withLock { removeExpired() }

    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        validMinutes: Int,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        val now = now()
        return postInternal(
            id = id, text = text, level = level,
            date = now,
            validTo = if (validMinutes > 0) now + validMinutes.toLong().minutes.inWholeMilliseconds else 0L,
            actions = actions, validityCheck = validityCheck
        )
    }

    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle =
        postInternal(
            id = id, text = text, level = level,
            date = date, validTo = validTo,
            actions = actions, validityCheck = validityCheck
        )

    override fun post(
        id: NotificationId,
        textRef: TextRef,
        level: NotificationLevel,
        validMinutes: Int,
        date: Long,
        validTo: Long,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle =
        postInternal(
            id = id, text = rh.gs(textRef), level = level,
            date = date,
            validTo = if (validMinutes > 0) date + validMinutes.toLong().minutes.inWholeMilliseconds else validTo,
            actions = actions, validityCheck = validityCheck
        )

    private fun postInternal(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle = lock.withLock {
        // Clean up expired notifications piggyback on writes.
        removeExpired()

        val current = _notifications.value.toMutableList()
        val instanceKey: Int
        if (id.allowMultiple) {
            instanceKey = nextInstanceKey++
        } else {
            instanceKey = id.ordinal
            // Cancel the replaced notification before showing its replacement.
            current.filter { it.id == id }.forEach { cancelSystemNotification(it) }
            current.removeAll { it.id == id }
        }

        val notification = AapsNotification(
            id = id,
            instanceKey = instanceKey,
            text = text,
            level = level,
            date = date,
            validTo = validTo,
            actions = actions,
            validityCheck = validityCheck
        )
        current.add(notification)
        current.sortBy { it.level.priority }
        _notifications.value = current

        // The platform gets the whole notification and decides what, if anything, to show.
        platform.show(
            notification = notification,
            title = rh.gs(if (level == NotificationLevel.URGENT) CoreUiStrings.urgent_alarm else CoreUiStrings.info)
        )
        aapsLogger.debug(LTag.NOTIFICATION, "Notification posted: [${id.name}] $text")
        NotificationHandle(instanceKey)
    }

    override fun dismiss(id: NotificationId) = lock.withLock {
        removeMatching("Notification dismissed: ${id.name}") { it.id == id }
    }

    override fun dismiss(handle: NotificationHandle) = lock.withLock {
        removeMatching("Notification dismissed by handle: ${handle.instanceKey}") { it.instanceKey == handle.instanceKey }
    }

    /** Dismiss every active urgent alarm and clear its platform notification. */
    override fun dismissAllAlarms() = lock.withLock {
        val current = _notifications.value
        val alarms = current.filter { it.level == NotificationLevel.URGENT }
        if (alarms.isNotEmpty()) {
            alarms.forEach { cancelSystemNotification(it) }
            _notifications.value = current - alarms.toSet()
        }
        platform.cancelAll()
        aapsLogger.debug(LTag.NOTIFICATION, "Dismissed all alarms")
    }

    /** Caller must hold [lock]. */
    private fun removeMatching(logLine: String, predicate: (AapsNotification) -> Boolean) {
        val current = _notifications.value
        val removed = current.filter(predicate)
        if (removed.isEmpty()) return
        removed.forEach { cancelSystemNotification(it) }
        _notifications.value = current - removed.toSet()
        aapsLogger.debug(LTag.NOTIFICATION, logLine)
    }

    /** Caller must hold [lock]. */
    private fun removeExpired() {
        val now = now()
        val current = _notifications.value
        val expired = current.filter { n ->
            (n.validTo != 0L && n.validTo < now) || (n.validityCheck?.invoke() == false)
        }
        if (expired.isEmpty()) return
        expired.forEach { n ->
            cancelSystemNotification(n)
            aapsLogger.debug(LTag.NOTIFICATION, "Notification expired: ${n.text}")
        }
        _notifications.value = current - expired.toSet()
    }

    private fun cancelSystemNotification(n: AapsNotification) = platform.cancel(n.instanceKey)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
