package cloud.samlo.couch.kevbox

import android.view.KeyEvent
import cloud.samlo.couch.kevbox.keys.KeyEntry
import cloud.samlo.couch.kevbox.keys.KeyFilter
import cloud.samlo.couch.kevbox.keys.KeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFilterTest {
    private val acted = mutableListOf<KeyEntry>()
    private var entries: List<KeyEntry> = KeyMap.DEFAULT
    private var installed = true
    private val filter = KeyFilter({ entries }, { installed }, { acted += it })

    private fun down(key: Int, scan: Int, repeat: Int = 0) = filter.onKey(KeyEvent.ACTION_DOWN, key, scan, repeat)
    private fun up(key: Int, scan: Int) = filter.onKey(KeyEvent.ACTION_UP, key, scan, 0)

    @Test
    fun mappedKeyActsOnceOnTheUp() {
        assertTrue(down(193, 416))
        assertTrue(down(193, 416, 1))
        assertTrue(down(193, 416, 2))
        assertTrue(acted.isEmpty())
        assertTrue(up(193, 416))
        assertEquals(listOf("tv.kevbox"), acted.map { it.pkg })
    }

    @Test
    fun unmappedKeyPassesThroughUntouched() {
        assertFalse(down(24, 115))
        assertFalse(down(24, 115, 1))
        assertFalse(down(24, 115, 2))
        assertFalse(up(24, 115))
        entries = emptyList()
        assertFalse(down(193, 416))
        assertFalse(down(193, 416, 1))
        assertFalse(up(193, 416))
        assertTrue(acted.isEmpty())
    }

    @Test
    fun missingPackagePassesThroughActionsDoNot() {
        installed = false
        assertFalse(down(193, 416))
        assertFalse(up(193, 416))
        assertTrue(acted.isEmpty())
        assertTrue(down(3, 172))
        assertTrue(up(3, 172))
        assertEquals(listOf(KeyMap.HOME), acted.map { it.action })
    }

    @Test
    fun anUpWithoutItsDownPassesThrough() {
        assertFalse(up(193, 416))
        assertTrue(acted.isEmpty())
    }

    @Test
    fun entryIsFixedAtKeyDown() {
        assertTrue(down(193, 416))
        entries = listOf(KeyEntry(193, null, "tv.kevbox.live", null))
        assertTrue(up(193, 416))
        assertEquals(listOf("tv.kevbox"), acted.map { it.pkg })
    }

    @Test
    fun twoHeldKeysEachActOnTheirOwnUp() {
        assertTrue(down(193, 416))
        assertTrue(down(3, 172))
        assertTrue(up(3, 172))
        assertEquals(listOf(KeyMap.HOME), acted.map { it.action })
        assertTrue(up(193, 416))
        assertEquals(listOf(null, "tv.kevbox"), acted.map { it.pkg })
    }

    @Test
    fun otherActionsPassThrough() {
        assertTrue(down(193, 416))
        // 2 is ACTION_MULTIPLE, deprecated, so named by value here.
        assertFalse(filter.onKey(2, 193, 416, 0))
        assertTrue(acted.isEmpty())
    }
}
