package cloud.samlo.couch.kevbox

import cloud.samlo.couch.data.Programs
import cloud.samlo.couch.kevbox.home.HomeRows
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {
    private fun channels(pkg: String, vararg names: String) =
        names.mapIndexed { i, name -> Programs.Channel(i.toLong(), pkg, name, emptyList()) }

    private fun picked(pkg: String, vararg names: String) =
        HomeRows.pick(pkg, channels(pkg, *names)).map { it.name }

    @Test
    fun smartTubeShowsRecommendedThenHistory() {
        assertEquals(
            listOf("Recommended", "History"),
            picked("org.smarttube.stable", "Subscriptions", "Recommended", "History", "Liked"),
        )
    }

    @Test
    fun kevBoxLiveFindsItsFrenchRows() {
        assertEquals(
            listOf("Chaînes récentes", "Favoris"),
            picked("tv.kevbox.live", "Favoris", "Chaînes récentes"),
        )
    }

    @Test
    fun kevBoxTvWithOnlyContinueWatchingHasOneRow() {
        assertEquals(listOf("Continue Watching"), picked("tv.kevbox", "Continue Watching"))
    }

    @Test
    fun anUnknownAppShowsItsFirstTwoChannels() {
        assertEquals(listOf("A", "B"), picked("com.example.app", "A", "B", "C"))
    }

    @Test
    fun aKnownAppShowsOnlyTheRowsItsKeywordsFind() {
        // No Recommended row: History alone, not Subscriptions in Recommended's place.
        assertEquals(listOf("History"), picked("org.smarttube.stable", "History", "Subscriptions"))
    }

    @Test
    fun aSecondCopyOfARowNeverFillsAnEmptySlot() {
        // KevBox TV before its Library row, with a stale second Continue Watching channel.
        assertEquals(listOf("Continue Watching"), picked("tv.kevbox", "Continue Watching", "Continue Watching"))
    }
}
