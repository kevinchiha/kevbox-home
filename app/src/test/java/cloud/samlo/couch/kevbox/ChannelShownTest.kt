package cloud.samlo.couch.kevbox

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Programs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelShownTest {
    private fun channel(browsable: Boolean) = Programs.ChannelInfo(7, "tv.kevbox", "Continue Watching", browsable)

    @Test
    fun aChannelTheLauncherNeverMadeBrowsableIsShown() {
        // Google TV leaves every app's channel at browsable = 0.
        assertTrue(channel(browsable = false).shown(Config()))
    }

    @Test
    fun aBrowsableChannelIsShown() {
        assertTrue(channel(browsable = true).shown(Config()))
    }

    @Test
    fun aChannelSwitchedOffInSettingsStaysOff() {
        val config = Config().apply { channels = mapOf("7" to false) }
        assertFalse(channel(browsable = true).shown(config))
    }
}
