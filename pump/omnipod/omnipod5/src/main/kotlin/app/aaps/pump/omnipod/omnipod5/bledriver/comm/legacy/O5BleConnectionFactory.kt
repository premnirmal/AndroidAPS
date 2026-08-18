package app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.session.O5Connection
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates [O5Connection] instances, parallel to Dash's [LegacyBleConnectionFactory]
 * rather than a modification of it (that factory is hardwired to Dash's
 * [app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager]).
 */
@Singleton
class O5BleConnectionFactory @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val podState: O5PodStateManager,
    private val p256KeyGenerator: P256KeyGenerator
) : BleConnectionFactory {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override fun createConnection(podAddress: String): BleConnection {
        val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")
        val podDevice = adapter.getRemoteDevice(podAddress)
        return O5Connection(podDevice, aapsLogger, config, context, podState, p256KeyGenerator)
    }
}
