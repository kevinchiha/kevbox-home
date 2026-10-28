package cloud.samlo.couch.kevbox

import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
import cloud.samlo.couch.kevbox.menu.AppMenu.Place.COLUMN
import cloud.samlo.couch.kevbox.menu.AppMenu.Place.DRAWER
import cloud.samlo.couch.kevbox.menu.Uninstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallTest {
    @Test
    fun userAppInTheDrawerCanBeUninstalled() {
        assertTrue(Uninstall.allowed(0, "com.example.game", DRAWER, apps = listOf("tv.kevbox")))
    }

    @Test
    fun systemAppsOnlyWhenUpdated() {
        assertFalse(Uninstall.allowed(FLAG_SYSTEM, "com.example.game", DRAWER, apps = emptyList()))
        assertTrue(Uninstall.allowed(FLAG_SYSTEM or FLAG_UPDATED_SYSTEM_APP, "com.example.game", DRAWER, apps = emptyList()))
    }

    @Test
    fun neverInTheLeftColumn() {
        // An empty app list: the place alone must decide it.
        assertFalse(Uninstall.allowed(0, "com.example.game", COLUMN, apps = emptyList()))
    }

    @Test
    fun neverForAPackageInApps() {
        assertFalse(Uninstall.allowed(0, "tv.kevbox", DRAWER, apps = listOf("tv.kevbox", "tv.kevbox.live")))
    }
}
