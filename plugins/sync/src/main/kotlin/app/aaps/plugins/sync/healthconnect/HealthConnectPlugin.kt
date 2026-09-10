package app.aaps.plugins.sync.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.Metadata
import androidx.health.connect.client.units.BloodGlucose
import app.aaps.core.data.model.GV
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.plugins.sync.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectPlugin @Inject constructor(
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .alwaysEnabled()
        .neverVisible()
        .pluginName(R.string.health_connect),
    aapsLogger,
    rh
) {

    private var scope: CoroutineScope? = null
    private var client: HealthConnectClient? = null

    override suspend fun onStart() {
        super.onStart()
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return
        client = HealthConnectClient.getOrCreate(context)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        persistenceLayer.observeAnyChange()
            .filter { changes -> GV::class in changes }
            .collectResilient(newScope, aapsLogger, LTag.CORE) {
                writeRecentGlucoseValues()
            }
        writeRecentGlucoseValues()
    }

    override suspend fun onStop() {
        scope?.cancel()
        scope = null
        client = null
        super.onStop()
    }

    private suspend fun writeRecentGlucoseValues() {
        val healthConnectClient = client ?: return
        val permission = HealthPermission.getWritePermission(BloodGlucoseRecord::class)
        if (permission !in healthConnectClient.permissionController.getGrantedPermissions()) return

        val end = System.currentTimeMillis()
        val readings = persistenceLayer.getBgReadingsDataFromTime(end - SYNC_WINDOW, end, true)
            .filter { it.isValid && it.value > 0.0 }
        if (readings.isEmpty()) return

        healthConnectClient.insertRecords(readings.map { it.toBloodGlucoseRecord() })
    }

    private fun GV.toBloodGlucoseRecord(): BloodGlucoseRecord =
        BloodGlucoseRecord(
            time = Instant.ofEpochMilli(timestamp),
            zoneOffset = ZoneOffset.ofTotalSeconds((utcOffset / 1000).toInt()),
            metadata = Metadata(clientRecordId = "androidaps-glucose-$id"),
            level = BloodGlucose.milligramsPerDeciliter(value),
            specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
            mealType = BloodGlucoseRecord.MEAL_TYPE_UNKNOWN,
            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
        )

    private companion object {
        const val SYNC_WINDOW = 7 * 24 * 60 * 60 * 1000L
    }
}
