package cloud.samlo.couch.kevbox

import cloud.samlo.couch.R
import cloud.samlo.couch.kevbox.buttons.BtState
import cloud.samlo.couch.kevbox.buttons.ButtonText
import cloud.samlo.couch.kevbox.buttons.Link
import cloud.samlo.couch.kevbox.buttons.NetState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ButtonTextTest {
    private fun wifi(level: Int) = ButtonText.network(NetState(Link.WIFI, level, true, false))

    @Test
    fun wifiTextFollowsTheLevel() {
        assertEquals(R.string.kevbox_wifi_strong, wifi(4))
        assertEquals(R.string.kevbox_wifi_strong, wifi(3))
        assertEquals(R.string.kevbox_wifi_fair, wifi(2))
        assertEquals(R.string.kevbox_wifi_weak, wifi(1))
        assertEquals(R.string.kevbox_wifi_weak, wifi(0))
        assertEquals(R.string.kevbox_wifi_strong, wifi(7))
        assertEquals(R.string.kevbox_wifi_weak, wifi(-1))
    }

    @Test
    fun ethernetAndNoNetworkIgnoreLevelAndVpn() {
        assertEquals(R.string.kevbox_ethernet, ButtonText.network(NetState(Link.ETHERNET, 0, false, true)))
        assertEquals(R.string.kevbox_no_network, ButtonText.network(NetState(Link.NONE, 4, true, true)))
    }

    @Test
    fun bluetoothTextAndCount() {
        assertEquals(R.string.kevbox_bluetooth_off to null, ButtonText.bluetooth(BtState(false, 2)))
        assertEquals(R.string.kevbox_bluetooth_on to null, ButtonText.bluetooth(BtState(true, 0)))
        assertEquals(R.string.kevbox_bluetooth_connected to 2, ButtonText.bluetooth(BtState(true, 2)))
    }

    @Test
    fun bluetoothCountKeepsAsciiDigitsOnAnArabicTv() {
        val templates = mapOf(
            R.string.kevbox_bluetooth_off to "Bluetooth · off",
            R.string.kevbox_bluetooth_on to "Bluetooth · on",
            R.string.kevbox_bluetooth_connected to "Bluetooth · %1\$d connected",
        )
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("Bluetooth · 12 connected", ButtonText.bluetoothText(BtState(true, 12)) { templates.getValue(it) })
            assertEquals("Bluetooth · on", ButtonText.bluetoothText(BtState(true, 0)) { templates.getValue(it) })
        } finally {
            Locale.setDefault(before)
        }
    }
}
