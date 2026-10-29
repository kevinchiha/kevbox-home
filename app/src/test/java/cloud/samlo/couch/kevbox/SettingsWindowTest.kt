package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.restart.WindowRoot
import cloud.samlo.couch.kevbox.restart.pickSettingsRoot
import cloud.samlo.couch.kevbox.restart.seenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsWindowTest {
    private val settings = setOf("com.android.tv.settings", "com.android.settings")

    @Test
    fun applicationWindowWinsEvenWhenNotFirst() {
        val windows = listOf(
            WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"),
            WindowRoot("com.android.tv.settings", false, "settings overlay"),
            WindowRoot("com.android.tv.settings", true, "settings page"),
        )
        val active = WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher")
        assertEquals("settings page", pickSettingsRoot(windows, active, settings))
    }

    @Test
    fun nonApplicationSettingsWindowWhenNoApplicationOne() {
        val windows = listOf(
            WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"),
            WindowRoot("com.android.tv.settings", false, "settings overlay"),
        )
        assertEquals("settings overlay", pickSettingsRoot(windows, null, settings))
    }

    @Test
    fun activeWhenTheListIsEmpty() {
        val active = WindowRoot("com.android.tv.settings", true, "active")
        assertEquals("active", pickSettingsRoot(emptyList(), active, settings))
    }

    @Test
    fun activeWhenNoWindowIsSettings() {
        val windows = listOf(
            WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"),
            WindowRoot(null, false, "no package"),
        )
        val active = WindowRoot("com.android.settings", true, "active")
        assertEquals("active", pickSettingsRoot(windows, active, settings))
    }

    @Test
    fun nullWhenNothingIsSettings() {
        val windows = listOf(WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"))
        val active = WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher")
        assertNull(pickSettingsRoot(windows, active, settings))
        assertNull(pickSettingsRoot(emptyList(), null, settings))
        assertNull(pickSettingsRoot(emptyList(), WindowRoot(null, true, "no package"), settings))
    }

    @Test
    fun timeoutNamesThePickedSettingsPackage() {
        val windows = listOf(WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"))
        assertEquals("com.android.tv.settings", seenText("com.android.tv.settings", windows, null))
    }

    @Test
    fun timeoutWithoutSettingsNamesEveryWindowSeen() {
        val windows = listOf(
            WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher"),
            WindowRoot("com.android.systemui", false, "bar"),
            WindowRoot(null, false, "no package"),
        )
        val active = WindowRoot("com.google.android.apps.tv.launcherx", true, "launcher")
        assertEquals("com.google.android.apps.tv.launcherx,com.android.systemui,?", seenText(null, windows, active))
        assertEquals("com.netflix.ninja", seenText(null, emptyList(), WindowRoot("com.netflix.ninja", true, "active")))
    }

    @Test
    fun timeoutSaysNoneOnlyWhenNothingWasSeen() {
        assertEquals("none", seenText(null, emptyList(), null))
    }
}
