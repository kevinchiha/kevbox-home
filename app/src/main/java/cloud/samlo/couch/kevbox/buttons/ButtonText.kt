package cloud.samlo.couch.kevbox.buttons

import cloud.samlo.couch.R

/**
 * What the Wi-Fi and Bluetooth buttons say when focused, kept apart from the views so the
 * wording can be checked on the JVM.
 */
object ButtonText {
    /** The string resource for the network button's focused text. */
    fun network(state: NetState): Int = when (state.link) {
        Link.ETHERNET -> R.string.kevbox_ethernet
        Link.NONE -> R.string.kevbox_no_network
        Link.WIFI -> when {
            state.level >= 3 -> R.string.kevbox_wifi_strong
            state.level == 2 -> R.string.kevbox_wifi_fair
            else -> R.string.kevbox_wifi_weak
        }
    }

    /** The string resource for the Bluetooth button, and the count it formats (only for kevbox_bluetooth_connected). */
    fun bluetooth(state: BtState): Pair<Int, Int?> = when {
        !state.on -> R.string.kevbox_bluetooth_off to null
        state.connected <= 0 -> R.string.kevbox_bluetooth_on to null
        else -> R.string.kevbox_bluetooth_connected to state.connected
    }
}
