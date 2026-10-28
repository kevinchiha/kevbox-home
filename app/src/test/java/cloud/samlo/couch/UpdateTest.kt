package cloud.samlo.couch

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun versionsCompareNumerically() {
        assertTrue(Versions.isNewer("0.10.0", "0.2.0"))
        assertTrue(Versions.isNewer("v0.3.0", "0.2.0"))
        assertTrue(Versions.isNewer("1.0", "0.9.9"))
        assertFalse(Versions.isNewer("0.2.0", "0.2.0"))
        assertFalse(Versions.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun preReleaseTagsCountAsTheirNumbers() {
        assertTrue(Versions.isNewer("v0.3.0-beta.1", "0.2.0"))
        assertFalse(Versions.isNewer("v0.3.0-rc.1", "0.3.0"))
        assertEquals(listOf(0, 3, 0), Versions.parse("0.3.0-beta"))
    }

    @Test
    fun junkIsNeverNewer() {
        assertFalse(Versions.isNewer("latest", "0.2.0"))
        assertFalse(Versions.isNewer("0.3.0", ""))
        assertNull(Versions.parse("1.2.3.4"))
        assertNull(Versions.parse("+1.0"))
        assertNull(Versions.parse("1..2"))
        assertEquals(listOf(1, 2, 0), Versions.parse("v1.2"))
    }

    @Test
    fun updateChecksAreOnByDefaultAndCanBeTurnedOff() {
        assertTrue(Config().checkUpdates)
        assertFalse(Config.fromJson("""{"checkUpdates":false}""").checkUpdates)
        assertFalse(Config.fromJson(Config.fromJson("""{"checkUpdates":false}""").toJson()).checkUpdates)
    }
}
