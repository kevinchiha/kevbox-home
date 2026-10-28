package cloud.samlo.couch.kevbox.buttons

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

data class BtState(val on: Boolean, val connected: Int)

/** Bluetooth as the Bluetooth button shows it: on or off, and how many paired devices are connected. */
object BluetoothState {
    /**
     * A box with no adapter reads as off. The connected count needs BLUETOOTH_CONNECT, which the
     * provisioning script grants; without it the count is 0 and on/off still shows.
     */
    @SuppressLint("MissingPermission")
    fun read(context: Context): BtState {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return BtState(false, 0)
        val on = try {
            adapter.isEnabled
        } catch (e: SecurityException) {
            false
        }
        if (!on) return BtState(false, 0)
        val connected = try {
            // No public call says whether a bonded device is connected; the hidden one is greylisted.
            val isConnected = BluetoothDevice::class.java.getMethod("isConnected")
            adapter.bondedDevices.orEmpty().count { isConnected.invoke(it) == true }
        } catch (e: Exception) {
            0
        }
        return BtState(true, connected)
    }

    /**
     * Calls [onChange] on the main thread when Bluetooth turns on or off or a device connects or
     * drops. Exported on purpose: these broadcasts come from the Bluetooth process, not
     * system_server, so a not-exported receiver never hears them. All three are protected
     * broadcasts no other app can send.
     */
    fun watch(context: Context, onChange: (BtState) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange(read(context))
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }

    fun unwatch(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered.
        }
    }
}
