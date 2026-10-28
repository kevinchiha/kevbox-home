package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.home.AppLists
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListsTest {
    @Test
    fun columnRule() {
        assertEquals(listOf("a", "c"), AppLists.column(listOf("a", "b", "c", "a"), setOf("b"), setOf("a", "c", "d")))
    }

    @Test
    fun drawerRule() {
        val launchable = mapOf(
            "z" to "Zed", "x" to "Ex", "a" to "apple", "b" to "Banana", "c" to "cherry",
            "self" to "KevBox Home", "h" to "Hidden", "d" to "apple",
        )
        val drawer = AppLists.drawer(
            launchable,
            apps = listOf("a"),
            drawer = listOf("z", "a", "gone", "x"),
            hidden = setOf("h"),
            self = "self",
        )
        // z and x first in their own order; a is in the column; then by label ignoring case, ties by package.
        assertEquals(listOf("z", "x", "d", "b", "c"), drawer)
    }

    @Test
    fun moveClampsToTheEnds() {
        assertEquals(listOf("a", "b", "c"), AppLists.move(listOf("a", "b", "c"), 0, -1))
        assertEquals(listOf("b", "c", "a"), AppLists.move(listOf("a", "b", "c"), 0, 5))
        assertEquals(listOf("a", "c", "b"), AppLists.move(listOf("a", "b", "c"), 2, -1))
    }
}
