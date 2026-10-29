package cloud.samlo.couch.kevbox

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.kevbox.home.AppLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nothing on the box can un-hide an app, so only the provision file's `hidden` may hide one. */
class DefaultHiddenTest {
    private val projectivy = "com.spocky.projengmenu"
    private val sony = "com.sony.dtv.promos"
    private val launchable = mapOf(projectivy to "Projectivy", sony to "Promos", "tv.kevbox.home" to "KevBox Home")

    @Test
    fun aFreshConfigHidesNothing() {
        val config = Config()
        assertTrue(config.hiddenApps.isEmpty())
        assertTrue(config.hidden.isEmpty())
    }

    @Test
    fun formerlyHiddenAppsReachTheColumnAndTheDrawer() {
        val config = Config.fromJson("""{"apps":["$projectivy"]}""")
        assertTrue(config.hiddenApps.isEmpty())
        assertEquals(listOf(projectivy), AppLists.column(config.apps, config.hidden, launchable.keys))
        val drawer = AppLists.drawer(launchable, emptyList(), config.drawer, config.hidden, "tv.kevbox.home")
        assertEquals(listOf(projectivy, sony), drawer)
    }

    @Test
    fun theProvisionFilesHiddenStillHides() {
        val config = Config.fromJson("""{"hidden":["$projectivy"]}""")
        assertTrue(config.hiddenApps.isEmpty())
        val drawer = AppLists.drawer(launchable, config.apps, config.drawer, config.hidden, "tv.kevbox.home")
        assertEquals(listOf(sony), drawer)
    }
}
