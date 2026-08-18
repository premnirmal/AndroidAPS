@file:Suppress("WildcardImport")

package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

open class BleIO(
    private val aapsLogger: AAPSLogger,
    private var characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    private val gatt: BluetoothGatt,
    private val bleCommCallbacks: BleCommCallbacks,
    private val type: CharacteristicType
) : BleCharacteristicIO {

    /**
     * @return a byte array with the received data or error
     */
    override fun receivePacket(timeoutMs: Long): ByteArray? {
        return try {
            val packet = incomingPackets.poll(timeoutMs, TimeUnit.MILLISECONDS)
            if (packet == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Timeout reading $type packet")
            }
            packet
        } catch (e: InterruptedException) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Interrupted while reading packet: $e")
            null
        }
    }

    /**
     * @param payload the data to send
     */
    @Suppress("ReturnCount", "DEPRECATION")
    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending on $type: ${payload.toHex()}")
        val set = characteristic.setValue(payload)
        if (!set) {
            return BleSendErrorSending("Could set setValue on $type")
        }
        bleCommCallbacks.flushConfirmationQueue()
        val sent = gatt.writeCharacteristic(characteristic)
        if (!sent) {
            return BleSendErrorSending("Could not writeCharacteristic on $type")
        }

        return when (
            val confirmation = bleCommCallbacks.confirmWrite(
                payload,
                type.value,
                DEFAULT_IO_TIMEOUT_MS
            )
        ) {
            is WriteConfirmationError   ->
                BleSendErrorConfirming(confirmation.msg)

            is WriteConfirmationSuccess ->
                BleSendSuccess
        }
    }

    /**
     * Called before sending a new message.
     * The incoming queues should be empty, so we log when they are not.
     */
    override fun flushIncomingQueue(): Boolean {
        var foundRTS = false
        do {
            val found = incomingPackets.poll()?.also {
                aapsLogger.warn(LTag.PUMPBTCOMM, "BleIO: queue not empty, flushing: ${it.toHex()}")
                if (it.isNotEmpty() && it[0] == BleCommandRTS.data[0]) {
                    foundRTS = true
                }
            }
        } while (found != null)
        return foundRTS
    }

    /**
     * Enable notifications/indications on the characteristic, whichever it actually
     * declares support for. This will signal the pod it can start sending back data.
     *
     * Unlike iOS's CoreBluetooth (where `setNotifyValue` picks the correct CCCD value
     * for you automatically, based on the characteristic's own properties - the app
     * never has to choose), Android's BluetoothGatt API requires the caller to inspect
     * `characteristic.properties` and write the matching CCCD value itself. This used
     * to unconditionally write ENABLE_INDICATION_VALUE - correct for Dash's hardware
     * (years of real-hardware use back that up), but never verified against O5's, which
     * may only declare PROPERTY_NOTIFY for these characteristics. A pod whose firmware
     * never receives the CCCD value matching what it actually supports can accept the
     * write without error yet never deliver anything the way it's being asked to -
     * consistent with real O5 pairing attempts consistently disconnecting a few seconds
     * after SPS0 is sent, with no response ever received. Mirrors the same dynamic
     * check [app.aaps.pump.omnipod.common.bledriver.comm.legacy.session.O5Connection
     * .enableHeartbeatNotifications] already uses for the heartbeat characteristic -
     * this is the same decision, just never applied here too, where it actually matters
     * for pairing.
     * @return
     */
    @Suppress("DEPRECATION")
    override fun readyToRead(): BleSendResult {
        gatt.setCharacteristicNotification(characteristic, true)
            .assertTrue("enable notifications")

        val descriptors = characteristic.descriptors
        if (descriptors.size != 1) {
            throw ConnectException("Expecting one descriptor, found: ${descriptors.size}")
        }
        val descriptor = descriptors[0]
        val usesIndicate = usesIndicate(characteristic)
        val enableValue = if (usesIndicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "$type characteristic properties: ${characteristic.properties} (using ${if (usesIndicate) "INDICATE" else "NOTIFY"})"
        )
        descriptor.value = enableValue
        gatt.writeDescriptor(descriptor)
            .assertTrue("enable ${if (usesIndicate) "indications" else "notifications"} on descriptor")

        aapsLogger.debug(LTag.PUMPBTCOMM, "Enabling ${if (usesIndicate) "indications" else "notifications"} for $type")
        val confirmation = bleCommCallbacks.confirmWrite(
            enableValue,
            descriptor.uuid.toString(),
            DEFAULT_IO_TIMEOUT_MS
        )
        return when (confirmation) {
            is WriteConfirmationError   ->
                throw ConnectException(confirmation.msg)

            is WriteConfirmationSuccess ->
                BleSendSuccess
        }
    }

    companion object {
        const val DEFAULT_IO_TIMEOUT_MS = BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS

        /**
         * Isolated from [readyToRead]'s GATT calls so this decision is directly unit
         * testable, without needing the full connect()/readyToRead() mock chain - and
         * the real Android framework's null ENABLE_INDICATION_VALUE/
         * ENABLE_NOTIFICATION_VALUE static-field environment boundary that chain hits in
         * pure-JVM tests (see O5ConnectionTest's documented stopping point) - just to
         * verify which branch gets picked.
         */
        internal fun usesIndicate(characteristic: BluetoothGattCharacteristic): Boolean =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
    }
}

private fun Boolean.assertTrue(operation: String) {
    if (!this) {
        throw ConnectException("Could not $operation")
    }
}
