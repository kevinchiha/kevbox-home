package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.home.StatusLine
import cloud.samlo.couch.kevbox.home.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class StatusLineTest {
    // The English strings, as plain lambdas.
    private val words = StatusLine.Words(
        seasonEpisode = { s, e -> "S$s E$e" },
        episode = { e -> "E$e" },
        minutesLeft = { m -> "$m min left" },
        hoursLeft = { h, m -> "$h h $m left" },
        opened = { at -> "Opened $at" },
    )
    private val hour = 3_600_000L

    private fun left(ms: Long, season: String? = "2", episode: String? = "4") =
        Watch("Severance", season, episode, 2 * hour - ms, 2 * hour)

    @Test
    fun `show with season and episode reads title, S E and minutes left`() {
        assertEquals("Severance · S2 E4 · 31 min left", StatusLine.text(left(31 * 60_000L + 40_000L), null, words))
    }

    @Test
    fun `an hour or more reads h and two-digit minutes`() {
        assertEquals("Severance · S2 E4 · 1 h 05 left", StatusLine.text(left(65 * 60_000L), null, words))
        assertEquals("Severance · S2 E4 · 1 h 00 left", StatusLine.text(left(60 * 60_000L), null, words))
    }

    @Test
    fun `episode parts drop out when unknown`() {
        val ms = 31 * 60_000L
        assertEquals("Severance · E4 · 31 min left", StatusLine.text(left(ms, season = null), null, words))
        assertEquals("Severance · 31 min left", StatusLine.text(left(ms, episode = " "), null, words))
        assertEquals("Severance · 31 min left", StatusLine.text(left(ms, season = null, episode = null), null, words))
    }

    @Test
    fun `no position or no duration falls back to Opened`() {
        val noPosition = Watch("Severance", "2", "4", 0, 2 * hour)
        val noDuration = Watch("Severance", "2", "4", hour, 0)
        assertEquals("Opened today at 10:20", StatusLine.text(noPosition, "today at 10:20", words))
        assertEquals("Opened today at 10:20", StatusLine.text(noDuration, "today at 10:20", words))
    }

    @Test
    fun `no watch and no last-opened gives null`() {
        assertNull(StatusLine.text(null, null, words))
    }

    @Test
    fun `minutes left keep ASCII digits on an Arabic TV`() {
        // The real strings.xml templates, filled in by StatusLine itself.
        val real = StatusLine.words("S%1\$s E%2\$s", "E%1\$s", "%1\$d min left", "%1\$d h %2\$s left", "Opened %1\$s")
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("Severance · S2 E4 · 31 min left", StatusLine.text(left(31 * 60_000L), null, real))
            assertEquals("Severance · S2 E4 · 1 h 05 left", StatusLine.text(left(65 * 60_000L), null, real))
        } finally {
            Locale.setDefault(before)
        }
    }
}
