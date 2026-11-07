package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.home.AppLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListsTest {
    @Test
    fun columnRule() {
        assertEquals(listOf("a", "c"), AppLists.column(listOf("a", "b", "c", "a"), setOf("b"), setOf("a", "c", "d")))
    }

    @Test
    fun addToColumnPutsTheAppLastOnce() {
        assertEquals(listOf("a", "b", "c"), AppLists.addToColumn(listOf("a", "b"), "c"))
        // Already there: the column keeps its order rather than moving or doubling the app.
        assertEquals(listOf("a", "b"), AppLists.addToColumn(listOf("a", "b"), "a"))
        assertEquals(listOf("c"), AppLists.addToColumn(emptyList(), "c"))
    }

    @Test
    fun removeFromColumnSendsTheAppBackToTheDrawer() {
        assertEquals(listOf("tv.kevbox.live", "c"), AppLists.removeFromColumn(listOf("tv.kevbox.live", "vlc", "c", "vlc"), "vlc"))
        assertEquals(listOf("a"), AppLists.removeFromColumn(listOf("a"), "missing"))
        // Once off the column, the drawer lists it again.
        val apps = AppLists.removeFromColumn(listOf("tv.kevbox", "vlc"), "vlc")
        assertEquals(listOf("vlc"), AppLists.drawer(mapOf("tv.kevbox" to "KevBox TV", "vlc" to "VLC"), apps, emptyList(), emptySet(), "self"))
    }

    @Test
    fun kevboxLiveTvAndSmartTubeStayOnHome() {
        val apps = listOf("tv.kevbox.live", "tv.kevbox", "org.smarttube.stable", "com.liskovsoft.smarttubetv.beta", "vlc")
        for (pkg in apps.dropLast(1)) {
            assertFalse(pkg, AppLists.removable(pkg))
            assertEquals(apps, AppLists.removeFromColumn(apps, pkg))
        }
        assertTrue(AppLists.removable("vlc"))
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
