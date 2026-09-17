package app.aaps.persistentNotification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Metric
import androidx.core.app.NotificationCompat.Metric.FixedFloat
import androidx.core.app.NotificationCompat.Metric.FixedInt
import androidx.core.app.NotificationCompat.MetricStyle
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.lifecycle.Observer
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.apsAdjustedTargetMgdl
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.extensions.generateCOBString
import app.aaps.core.ui.extensions.toStringShort
import app.aaps.core.utils.DeferredForegroundStart
import app.aaps.plugins.main.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.round

@Suppress("PrivatePropertyName")
// Registers itself into the every-build plugin bucket at order 0, replacing the @Binds @IntKey(0) in
// PersistentNotificationModule.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(0)
@SingleIn(AppScope::class)
@Inject
class PersistentNotificationPlugin(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val activePlugins: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val rxBus: RxBus,
    private val context: Context,
    private val notificationHolder: NotificationHolder,
    private val dummyServiceHelper: DummyServiceHelper,
    private val iconsProvider: IconsProvider,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val config: Config,
    private val decimalFormatter: DecimalFormatter,
    private val ch: ConcentrationHelper,
    private val loop: Loop,
    private val persistenceLayer: PersistenceLayer,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val dateUtil: DateUtil,
    private val trendCalculator: TrendCalculator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .pluginName(TextRef.AndroidRes(R.string.ongoingnotificaction))
        .enableByDefault(true)
        .alwaysEnabled(true)
        .showInList { false }
        .description(TextRef.AndroidRes(R.string.description_persistent_notification)),
    aapsLogger, rh
) {

    // For Android Auto
    // Intents are not declared in manifest and not consumed, this is intentionally because actually we can't do anything with
    private val READ_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_READ"
    private val REPLY_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_REPLY"
    private val CONVERSATION_ID = "conversation_id"
    private val EXTRA_VOICE_REPLY = "extra_voice_reply"
    // End Android auto

    private var scope: CoroutineScope? = null
    private val deferredStart = DeferredForegroundStart()
    private var lastAutoNotificationContent: String = ""
    private val carConnection by lazy { CarConnection(context) }
    private val carConnectionObserver = Observer<Int> { connectionType ->
        val connected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION
        if (connected != isAndroidAutoConnected) {
            isAndroidAutoConnected = connected
            lastAutoNotificationContent = ""
            triggerNotificationUpdate(includeAuto = connected)
        }
    }

    @Volatile
    private var isAndroidAutoConnected = false

    @OptIn(FlowPreview::class)
    override suspend fun onStart() {
        super.onStart()
        notificationHolder.createNotificationChannel()
        withContext(Dispatchers.Main.immediate) {
            carConnection.type.observeForever(carConnectionObserver)
        }
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        rxBus.toFlow(EventRefreshOverview::class)
            .collectResilient(newScope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { triggerNotificationUpdate() }
        rxBus.toFlow(EventInitializationChanged::class)
            .collectResilient(newScope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { triggerNotificationUpdate() }
        rxBus.toFlow(EventAutosensCalculationFinished::class)
            .collectResilient(newScope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { triggerNotificationUpdate() }
        /// Android Auto - debounced to prevent rapid pop-ups
        // Flow's debounce means the same thing as Rx's: emit once the source has been quiet for the
        // period. UNDISPATCHED is not claimed here - merge subscribes to its sources in child
        // coroutines that are dispatched, so the window survives it. Harmless for this one: the worst
        // case is a notification refresh that a later event triggers anyway.
        merge(
            rxBus.toFlow(EventRefreshOverview::class).map { },
            rxBus.toFlow(EventInitializationChanged::class).map { },
            rxBus.toFlow(EventAutosensCalculationFinished::class).map { }
        )
            .debounce(10_000L)
            .collectResilient(newScope, aapsLogger, LTag.CORE) { triggerNotificationUpdate(includeAuto = isAndroidAutoConnected) }
        /// End Android Auto
    }

    override suspend fun onStop() {
        withContext(Dispatchers.Main.immediate) {
            carConnection.type.removeObserver(carConnectionObserver)
        }
        isAndroidAutoConnected = false
        scope?.cancel()
        scope = null
        deferredStart.cancel()
        dummyServiceHelper.stopService(context)
        super.onStop()
    }

    private fun triggerNotificationUpdate(includeAuto: Boolean = false) {
        runBlocking { updateNotification(includeAuto) }
        deferredStart.start { dummyServiceHelper.startService(context) }
    }

    private suspend fun updateNotification(includeAuto: Boolean = false) {
        if (!config.appInitialized) return
        val pump = activePlugins.activePump
        var line1: String?
        var line2: String? = null
        var line3: String? = null
        var bgStatusChipText: String? = null
        var bgMetric: Metric? = null
        var metricValue: Metric.MetricValue? = null
        var androidAutoReplyAction: NotificationCompat.Action? = null
        var androidAutoReadAction: NotificationCompat.Action? = null
        if (profileFunction.isProfileValid("Notification")) {
            val lastBG = iobCobCalculator.ads.lastBg()
            val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            val units = profileFunction.getUnits()
            if (lastBG != null) {
                val bgValueText = profileUtil.fromMgdlToStringInUnits(lastBG.recalculated)
                val fromMgdlToUnits = profileUtil.fromMgdlToUnits(lastBG.recalculated)
                metricValue  = if (units == GlucoseUnit.MMOL) {
                    FixedFloat(
                        fromMgdlToUnits.round(1).toFloat(),
                        units.displayLabel
                    )
                } else {
                    FixedInt(
                        fromMgdlToUnits.toInt(),
                        units.displayLabel
                    )
                }
                val trendSymbol = (trendCalculator.getTrendArrow(iobCobCalculator.ads)
                    ?.takeIf { it != TrendArrow.NONE } ?: TrendArrow.FLAT).symbol
                bgStatusChipText = "$bgValueText$trendSymbol"
                line1 = "$bgValueText $trendSymbol"
                if (glucoseStatus != null) {
                    line1 += " " + profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                } else {
                    line1 += " " + rh.gs(R.string.old_data)
                }
            } else {
                line1 = rh.gs(app.aaps.core.ui.R.string.missed_bg_readings)
            }
            val activeTemp = processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
            line1 += if (activeTemp != null) {
                " • " + activeTemp.toStringShort(rh) + " "
            } else {
                " • " + rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, ch.fromPump(pump.baseBasalRate)) + " "
            }
            val profileName = profileFunction.getProfileName()
            //IOB
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val cobInfo = iobCobCalculator.getCobInfo("PersistentNotificationPlugin")
            line2 =
                rh.gs(app.aaps.core.ui.R.string.treatments_iob_label_string) + " " + rh.gs(R.string.notification_iob_short, bolusIob.iob + basalIob.basaliob) + " • " + rh.gs(app.aaps.core.ui.R.string.cob) + ": " + cobInfo.generateCOBString(decimalFormatter)
            metricValue?.let {
                bgMetric = Metric(
                    it,
                    line2,
                )
            }
            line3 = profileName
        } else {
            line1 = rh.gs(app.aaps.core.ui.R.string.no_profile_set)
        }
        val content = "$line1|$line2|$line3"
        if (includeAuto && content == lastAutoNotificationContent) return
        if (includeAuto) lastAutoNotificationContent = content
        val builder = NotificationCompat.Builder(context, notificationHolder.channelID)
        builder.setOngoing(true)
        if (!includeAuto) {
            applyLiveUpdate(
                builder = builder,
                bgStatusChipText = bgStatusChipText,
                bgMetric = bgMetric,
            )
        }
        builder.setOnlyAlertOnce(true)
        builder.setCategory(if (includeAuto) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
        builder.setSmallIcon(iconsProvider.getNotificationIcon())
        builder.setContentTitle(line1)
        if (line2 != null) builder.setContentText(line2)
        if (line3 != null) builder.setSubText(line3)
        /// Android Auto
        if (includeAuto) {
            val msgReadIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(READ_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(context.packageName)
            val msgReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationHolder.notificationID,
                msgReadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val msgReplyIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(REPLY_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(context.packageName)
            val msgReplyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationHolder.notificationID,
                msgReplyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            // Build a RemoteInput for receiving voice input from devices
            val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY).build()
            val appPerson = Person.Builder()
                .setName(bgStatusChipText ?: line1)
                .setKey(context.packageName)
                .build()
            val devicePerson = Person.Builder()
                .setName(bgStatusChipText ?: line1)
                .setKey("$CONVERSATION_ID-device-user")
                .build()
            val androidAutoMessagingStyle = NotificationCompat.MessagingStyle(devicePerson)
                .setConversationTitle(bgStatusChipText)
                .setGroupConversation(false)
                .addMessage((line1), System.currentTimeMillis(), appPerson)
            androidAutoReplyAction = NotificationCompat.Action.Builder(
                iconsProvider.getNotificationIcon(),
                rh.gs(R.string.android_auto_reply),
                msgReplyPendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .addRemoteInput(remoteInput)
                .build()
            androidAutoReadAction = NotificationCompat.Action.Builder(
                iconsProvider.getNotificationIcon(),
                rh.gs(R.string.android_auto_mark_as_read),
                msgReadPendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
            builder.setLargeIcon(BitmapFactory.decodeResource(context.resources, iconsProvider.getIcon()))
            builder.setStyle(androidAutoMessagingStyle)
            builder.addInvisibleAction(androidAutoReplyAction)
            builder.addInvisibleAction(androidAutoReadAction)
        }
        /// End Android Auto
        builder.setContentIntent(notificationHolder.openAppIntent())
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = builder.build()
        mNotificationManager.notify(notificationHolder.notificationID, notification)
        notificationHolder.notification = notification
    }

    private fun applyLiveUpdate(
        builder: NotificationCompat.Builder,
        bgStatusChipText: String?,
        bgMetric: Metric?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        builder.setRequestPromotedOngoing(true)
        if (!bgStatusChipText.isNullOrBlank()) {
            builder.setShortCriticalText(bgStatusChipText)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && bgMetric != null) {
            builder.setStyle(
                MetricStyle()
                    .addMetric(bgMetric)
                    .setCriticalMetric(0)
            )
        }
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }
}
