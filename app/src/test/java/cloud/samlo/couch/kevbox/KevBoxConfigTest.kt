package cloud.samlo.couch.kevbox

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.kevbox.config.KevBoxConfig
import cloud.samlo.couch.kevbox.keys.KeyEntry
import cloud.samlo.couch.kevbox.keys.KeyMap
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KevBoxConfigTest {
    @Test
    fun wrongTypesKeepTheSavedValue() {
        val config = Config().apply { apps = listOf("tv.kevbox") }
        val applied = KevBoxConfig.decode(
            JSONObject("""{"apps": "tv.kevbox.live", "buttons": ["vpn","radio","vpn","wifi"], "wallpaper": "bing", "drawer": [1]}"""),
            config,
        )
        assertEquals(listOf("tv.kevbox"), config.apps)
        assertEquals(emptyList<String>(), config.drawer)
        assertEquals("daily", config.wallpaper)
        assertEquals(listOf("vpn", "wifi"), config.buttons)
        assertEquals(setOf("buttons"), applied)
    }

    @Test
    fun listsDropBlanksAndRepeats() {
        val config = Config()
        KevBoxConfig.decode(JSONObject("""{"apps": ["a", "", "b", "a"], "hidden": ["c", " "], "speedtestApp": " "}"""), config)
        assertEquals(listOf("a", "b"), config.apps)
        assertEquals(setOf("c"), config.hidden)
        assertEquals(KevBoxConfig.DEFAULT_SPEEDTEST, config.speedtestApp)
    }

    @Test
    fun configJsonRoundTripsTheKevBoxKeys() {
        val config = Config().apply {
            apps = listOf("tv.kevbox", "tv.kevbox.live")
            drawer = listOf("org.smarttube.stable")
            hidden = setOf("com.sony.dtv.promos")
            buttons = listOf("speedtest", "settings")
            speedtestApp = "org.zwanoo.android.speedtest"
            keys = listOf(KeyEntry(193, null, "tv.kevbox", null), KeyEntry(null, 216, null, KeyMap.DRAWER))
        }
        val copy = Config.fromJson(config.toJson())
        assertEquals(config.apps, copy.apps)
        assertEquals(config.drawer, copy.drawer)
        assertEquals(config.hidden, copy.hidden)
        assertEquals(config.buttons, copy.buttons)
        assertEquals(config.speedtestApp, copy.speedtestApp)
        assertEquals(config.keys, copy.keys)
        assertEquals(KeyMap.DEFAULT, Config.fromJson("{}").keys)
    }

    @Test
    fun anEmptyKeysListLoadsWithNoMappings() {
        assertTrue(Config.fromJson("""{"keys": []}""").keys.isEmpty())
    }

    @Test
    fun aBadWallpaperInConfigJsonKeepsTheDefault() {
        assertEquals("daily", Config.fromJson("""{"wallpaper": "bing"}""").wallpaper)
        assertEquals("reddit", Config.fromJson("""{"wallpaper": "reddit"}""").wallpaper)
    }

    @Test
    fun buttonsFallBackToTheDefaultAndKeepSettings() {
        assertEquals(listOf("wifi", "vpn", "speedtest", "settings"), KevBoxConfig.buttonsOf(Config()))
        assertEquals(listOf("vpn", "wifi", "settings"), KevBoxConfig.buttonsOf(Config().apply { buttons = listOf("vpn", "wifi") }))
        assertEquals(listOf("settings", "vpn"), KevBoxConfig.buttonsOf(Config().apply { buttons = listOf("settings", "vpn") }))
    }
}
