package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.update.SilentBlock
import cloud.samlo.couch.kevbox.update.VersionJson
import cloud.samlo.couch.update.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SilentBlockTest {
    private val hash = "0123456789abcdef".repeat(4)
    private val apk = "https://tv.kevbox.dev/home/kevbox-home-0.8.1.1.apk"
    private val after = 1_761_700_000_000L

    /** version.json with [silent] as the raw JSON of its `silent` value, or no key when null. */
    private fun json(silent: String?): String {
        val block = silent?.let { ",\"silent\":$it" }.orEmpty()
        return """{"versionCode":80101,"versionName":"0.8.1.1","url":"$apk","sha256":"$hash"$block}"""
    }

    private fun blockOf(silent: String?): SilentBlock? = VersionJson.parse(json(silent), 80100)!!.silent

    @Test
    fun `a good block is read with its early users`() {
        val block = blockOf("""{"after":$after,"earlyUsers":["a@b.c"," x "]}""")
        assertEquals(SilentBlock(after, listOf("a@b.c", " x ")), block)
    }

    @Test
    fun `early users missing, null or empty mean an empty list`() {
        assertEquals(SilentBlock(after, emptyList()), blockOf("""{"after":$after}"""))
        assertEquals(SilentBlock(after, emptyList()), blockOf("""{"after":$after,"earlyUsers":null}"""))
        assertEquals(SilentBlock(after, emptyList()), blockOf("""{"after":$after,"earlyUsers":[]}"""))
        assertEquals(SilentBlock(5, emptyList()), blockOf("""{"after":5}"""))
    }

    @Test
    fun `a bad block means no silent install but the release is still offered`() {
        val bad = listOf(
            null,
            "null",
            "[]",
            "5",
            "\"x\"",
            "{}",
            """{"after":null}""",
            """{"after":"$after"}""",
            """{"after":1.5}""",
            """{"after":0}""",
            """{"after":-1}""",
            """{"after":99999999999999999999}""",
            """{"after":$after,"earlyUsers":"a@b.c"}""",
            """{"after":$after,"earlyUsers":{}}""",
            """{"after":$after,"earlyUsers":[1]}""",
            """{"after":$after,"earlyUsers":[null]}""",
            """{"after":$after,"earlyUsers":["a",["b"]]}""",
        )
        for (silent in bad) {
            val release = VersionJson.parse(json(silent), 80100)
            assertNotNull("release dropped for $silent", release)
            assertNull("block kept for $silent", release!!.silent)
            assertNull(release.silentAfterBoxMs)
        }
    }

    @Test
    fun `the stored release keeps the block and the box-clock after`() {
        val block = SilentBlock(after, listOf("a@b.c"))
        val release = Release("0.8.1.1", 80101, apk, hash, "Notes", block, after - 60_000L)
        val back = Release.deserialize(release.serialize())!!
        assertEquals("0.8.1.1", back.version)
        assertEquals(80101L, back.versionCode)
        assertEquals("Notes", back.notes)
        assertEquals(block, back.silent)
        assertEquals(after - 60_000L, back.silentAfterBoxMs)
    }

    @Test
    fun `a stored release with no block round-trips with no block`() {
        val back = Release.deserialize(Release("0.8.1.1", 80101, apk, hash, "").serialize())!!
        assertNull(back.silent)
        assertNull(back.silentAfterBoxMs)
    }

    @Test
    fun `a box-clock after with no block is dropped`() {
        val back = Release.deserialize(Release("0.8.1.1", 80101, apk, hash, "", null, after).serialize())!!
        assertNull(back.silentAfterBoxMs)
    }
}
