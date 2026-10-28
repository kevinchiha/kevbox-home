package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.keys.KeyEntry
import cloud.samlo.couch.kevbox.keys.KeyMap
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {
    private fun pkgFor(keyCode: Int, scanCode: Int) = KeyMap.match(KeyMap.DEFAULT, keyCode, scanCode)?.pkg

    @Test
    fun keycodeFirstScancodeOnlyForAnUnknownKeycode() {
        assertEquals("tv.kevbox", pkgFor(193, 0))
        assertEquals("tv.kevbox", pkgFor(0, 416))
        assertEquals("tv.kevbox.live", pkgFor(194, 207))
        // Play reported as KEY_PLAY (scancode 207) must not open KevBox Live.
        assertNull(KeyMap.match(KeyMap.DEFAULT, 126, 207))
        assertNull(KeyMap.match(KeyMap.DEFAULT, 24, 115))
    }

    @Test
    fun aZeroScancodeNeverMatches() {
        val entries = listOf(KeyEntry(193, null, "a.b", null))
        assertNull(KeyMap.match(entries, 0, 0))
    }

    @Test
    fun defaultMapActions() {
        assertEquals(KeyMap.HOME, KeyMap.match(KeyMap.DEFAULT, 3, 0)?.action)
        assertEquals(KeyMap.DRAWER, KeyMap.match(KeyMap.DEFAULT, 0, 216)?.action)
    }

    @Test
    fun invalidEntriesAreSkipped() {
        val array = JSONArray(
            """[
              {"keycode": 193, "scancode": 416, "package": "tv.kevbox"},
              {"keycode": 1, "package": "a.b", "action": "home"},
              {"keycode": 2},
              {"keycode": 3, "action": "menu"},
              {"package": "a.b"},
              {"keycode": "193", "package": "a.b"},
              "not an object"
            ]""",
        )
        val parsed = KeyMap.parse(array)
        assertEquals(listOf(KeyEntry(193, 416, "tv.kevbox", null)), parsed)
        assertEquals(parsed, KeyMap.parse(KeyMap.toJson(parsed)))
    }

    @Test
    fun defaultRoundTrips() {
        assertEquals(KeyMap.DEFAULT, KeyMap.parse(KeyMap.toJson(KeyMap.DEFAULT)))
    }
}
