package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.home.Watch
import cloud.samlo.couch.kevbox.home.WatchLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLineTest {
    private val words = WatchLine.words("S%1\$s E%2\$s", "E%1\$s", "%1\$d min left", "%1\$d h %2\$s left")

    private fun line(season: String?, episode: String?, title: String?, positionMs: Long, durationMs: Long) =
        WatchLine.text(Watch(season, episode, title, positionMs, durationMs), words)

    @Test
    fun anEpisodeShowsItsNumberTitleAndTimeLeft() {
        assertEquals("S1 E2 · The Rescue · 24 min left", line("1", "2", "The Rescue", 60_000, 25 * 60_000))
    }

    @Test
    fun aLongFilmShowsHoursAndMinutesLeft() {
        assertEquals("1 h 05 left", line(null, null, null, 10 * 60_000, 75 * 60_000))
    }

    @Test
    fun aMadeUpDurationShowsNoTimeLeft() {
        // KevBox TV's stand-in when it only knows a percentage: 100 s.
        assertEquals("S2 E4", line("2", "4", null, 40_000, 100_000))
    }

    @Test
    fun nothingKnownIsNoLine() {
        assertNull(line(null, null, " ", 0, 0))
    }
}
