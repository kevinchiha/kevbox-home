package cloud.samlo.couch.kevbox

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.kevbox.keys.KeyMap
import cloud.samlo.couch.kevbox.provision.Provisioner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProvisionerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun appliesTheFileAndKeepsTheRest() {
        val dir = folder.newFolder()
        Config().apply { checkUpdates = false; apps = listOf("x") }.save(dir)
        val pushed = """{"apps":["tv.kevbox"],"radio":1}"""
        File(dir, Provisioner.FILE).writeText(pushed)
        File(dir, Provisioner.APPLIED).writeText("old")
        val lines = mutableListOf<String>()

        assertEquals(Provisioner.Outcome.APPLIED, Provisioner.apply(dir) { lines += it })
        val saved = Config.load(dir)
        assertEquals(listOf("tv.kevbox"), saved.apps)
        assertFalse(saved.checkUpdates)
        assertEquals(pushed, File(dir, Provisioner.APPLIED).readText())
        assertFalse(File(dir, Provisioner.FILE).exists())
        assertEquals(listOf("applied kevbox-home.json: apps"), lines)

        val before = File(dir, Config.FILE).readBytes()
        assertEquals(Provisioner.Outcome.NONE, Provisioner.apply(dir) { lines += it })
        assertEquals(1, lines.size)
        assertArrayEquals(before, File(dir, Config.FILE).readBytes())
    }

    @Test
    fun aNonObjectIsRejected() {
        for (text in listOf("[1,2]", "not json")) {
            val dir = folder.newFolder()
            Config().apply { apps = listOf("x") }.save(dir)
            val before = File(dir, Config.FILE).readBytes()
            File(dir, Provisioner.FILE).writeText(text)
            val lines = mutableListOf<String>()

            assertEquals(Provisioner.Outcome.REJECTED, Provisioner.apply(dir) { lines += it })
            assertArrayEquals(before, File(dir, Config.FILE).readBytes())
            assertEquals(text, File(dir, Provisioner.REJECTED).readText())
            assertFalse(File(dir, Provisioner.FILE).exists())
            assertEquals(1, lines.size)
            assertTrue(lines[0], lines[0].startsWith("rejected"))
        }
    }

    @Test
    fun aRefusedRenameKeepsTheFileAndFails() {
        val dir = folder.newFolder()
        val pushed = """{"apps":["tv.kevbox"]}"""
        File(dir, Provisioner.FILE).writeText(pushed)
        // A folder with something in it can be neither deleted, renamed over nor written as a file.
        File(dir, Provisioner.APPLIED).mkdir()
        File(dir, "${Provisioner.APPLIED}/keep").writeText("x")
        val lines = mutableListOf<String>()

        assertEquals(Provisioner.Outcome.FAILED, Provisioner.apply(dir) { lines += it })
        assertEquals(pushed, File(dir, Provisioner.FILE).readText())
        assertEquals(1, lines.size)
        assertTrue(lines[0], lines[0].startsWith("rename failed kevbox-home.json"))
    }

    @Test
    fun aPushStillInFlightIsIgnored() {
        val dir = folder.newFolder()
        File(dir, "${Provisioner.FILE}.tmp").writeText("""{"apps":["tv.kevbox"]}""")
        val lines = mutableListOf<String>()
        assertEquals(Provisioner.Outcome.NONE, Provisioner.apply(dir) { lines += it })
        assertTrue(lines.isEmpty())
        assertFalse(File(dir, Config.FILE).exists())
    }

    @Test
    fun theExampleFileApplies() {
        val dir = folder.newFolder()
        File("../kevbox/provision/kevbox-home.example.json").copyTo(File(dir, Provisioner.FILE))

        assertEquals(Provisioner.Outcome.APPLIED, Provisioner.apply(dir) {})
        val saved = Config.load(dir)
        assertEquals(KeyMap.DEFAULT, saved.keys)
        assertEquals(listOf("tv.kevbox.live", "tv.kevbox", "org.smarttube.stable"), saved.apps)
        assertEquals(
            listOf(
                "com.surfshark.vpnclient.android", "com.rma.netpulsetv", "com.android.vending",
                "nextapp.fx", "com.esaba.downloader", "org.videolan.vlc",
            ),
            saved.drawer,
        )
    }
}
