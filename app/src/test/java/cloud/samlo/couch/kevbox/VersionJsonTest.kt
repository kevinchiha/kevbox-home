package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.update.VersionJson
import cloud.samlo.couch.update.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionJsonTest {
    private val hash = "0123456789abcdef".repeat(4)
    private val apk = "https://tv.kevbox.dev/home/kevbox-home-0.8.1.1.apk"

    private fun json(
        code: String = "80101",
        name: String? = "\"0.8.1.1\"",
        url: String = apk,
        sha256: String = hash,
    ): String {
        val nameField = name?.let { "\"versionName\":$it," }.orEmpty()
        return """{"versionCode":$code,$nameField"url":"$url","sha256":"$sha256","profiles":[]}"""
    }

    @Test
    fun readsTheKevBoxTvShape() {
        val release = VersionJson.parse(json(), 80100)
        assertNotNull(release)
        assertEquals("0.8.1.1", release!!.version)
        assertEquals(80101L, release.versionCode)
        assertEquals(apk, release.apkUrl)
        assertEquals(hash, release.sha256)
        assertEquals("", release.notes)
        assertNull(VersionJson.parse(json(), 80101))
        assertNull(VersionJson.parse(json(), 90000))
    }

    @Test
    fun refusesForeignOrBrokenReleases() {
        assertNull(VersionJson.parse(json(url = "http://tv.kevbox.dev/home/a.apk"), 1))
        assertNull(VersionJson.parse(json(url = "https://tv.kevbox.dev.example.com/home/a.apk"), 1))
        assertNull(VersionJson.parse(json(url = "https://example.com/tv.kevbox.dev/x.apk"), 1))
        assertNull(VersionJson.parse(json(url = "https://tv.kevbox.dev@example.com/x.apk"), 1))
        assertNull(VersionJson.parse(json(sha256 = hash.drop(1)), 1))
        assertNull(VersionJson.parse(json(sha256 = hash.dropLast(1) + "g"), 1))
        assertNull(VersionJson.parse(json(code = "\"80101\""), 1))
        assertNull(VersionJson.parse(json(code = "80101.5"), 1))
        assertNull(VersionJson.parse(json(name = null), 1))
        assertNull(VersionJson.parse("[]", 1))
        assertNull(VersionJson.parse("not json", 1))
    }

    @Test
    fun theHashCheckIgnoresCase() {
        assertTrue(VersionJson.matches(hash.uppercase(), hash))
        assertTrue(VersionJson.matches(hash, hash.uppercase()))
        assertFalse(VersionJson.matches(hash, hash.dropLast(1) + "0"))
        assertFalse(VersionJson.matches("", ""))
        assertFalse(VersionJson.matches("  ", "  "))
    }

    @Test
    fun aStoredReleaseRoundTrips() {
        val release = Release("0.8.1.1", 80101, apk, hash, "Faster drawer")
        val back = Release.deserialize(release.serialize())
        assertNotNull(back)
        assertEquals("0.8.1.1", back!!.version)
        assertEquals(80101L, back.versionCode)
        assertEquals(apk, back.apkUrl)
        assertEquals(hash, back.sha256)
        assertEquals("Faster drawer", back.notes)
        assertNull(Release.deserialize("""{"version":"0.3.0","apk":"https://x/a.apk","sha256":"https://x/a.sha256"}"""))
    }

    @Test
    fun withNoTestFeedOnlyTvKevboxDevIsRead() {
        // The unit-test build is given no kevbox.updateFeed, as a release build never is.
        assertEquals(VersionJson.RELEASE_URL, VersionJson.URL)
        assertEquals("https://tv.kevbox.dev/home/version.json", VersionJson.RELEASE_URL)
        assertTrue(VersionJson.trusted(apk, null))
        assertFalse(VersionJson.trusted("http://10.0.2.2:8080/kevbox-home.apk", null))
    }

    @Test
    fun theTestFeedIsDebugOnlyAndMustBeAWebUrl() {
        val feed = "http://10.0.2.2:8080/version.json"
        assertEquals(feed, VersionJson.feedOf(true, " $feed "))
        assertNull(VersionJson.feedOf(false, feed))
        assertNull(VersionJson.feedOf(true, ""))
        assertNull(VersionJson.feedOf(true, "file:///sdcard/version.json"))
        assertNull(VersionJson.feedOf(true, "http://user@10.0.2.2/version.json"))
        assertNull(VersionJson.feedOf(true, "not a url"))
    }

    @Test
    fun aTestFeedTrustsOnlyItsOwnHost() {
        val feed = "http://10.0.2.2:8080/version.json"
        val local = "http://10.0.2.2:8080/kevbox-home.apk"
        assertTrue(VersionJson.trusted(local, feed))
        assertTrue(VersionJson.trusted("https://10.0.2.2/kevbox-home.apk", feed))
        assertFalse(VersionJson.trusted(apk, feed))
        assertFalse(VersionJson.trusted("http://user@10.0.2.2:8080/a.apk", feed))
        assertFalse(VersionJson.trusted("ftp://10.0.2.2/a.apk", feed))
        assertNotNull(VersionJson.read(json(url = local), feed))
        assertNull(VersionJson.read(json(url = local), null))
    }
}
