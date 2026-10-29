package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.update.SilentReason
import cloud.samlo.couch.kevbox.update.SilentRules
import cloud.samlo.couch.kevbox.update.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibilityTest {
    private val min = 60_000L
    private val hour = 60 * min

    @Test
    fun theCountSaysWhetherHomeIsOnScreen() {
        val count = Visibility.Count()
        assertEquals(false, count.onScreen)
        count.start()
        assertEquals(true, count.onScreen)
        // Home opens the drawer: the drawer starts before Home stops.
        count.start()
        assertEquals(1, count.stop())
        assertEquals(true, count.onScreen)
        assertEquals(0, count.stop())
        assertEquals(false, count.onScreen)
    }

    @Test
    fun aStopWithNothingStartedMakesVisibilityUnknown() {
        val count = Visibility.Count()
        assertEquals(0, count.stop())
        assertNull(count.onScreen)
        count.start()
        assertNull(count.onScreen)
    }

    @Test
    fun unknownVisibilityCountsAsOnScreen() {
        // Hidden for an hour, but unknown: only the 3 h idle rule can call it quiet.
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, null, hour, 2 * hour))
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, null, hour, 3 * hour))
    }

    @Test
    fun hiddenTimeCountsFromTheLaterOfStartAndStop() {
        val now = 10 * hour
        assertEquals(15 * min, Visibility.sinceHidden(lastStart = now - hour, lastStop = now - 15 * min, now = now))
        // Stored start after the stored stop: the process died while Home was on screen.
        assertEquals(5 * min, Visibility.sinceHidden(lastStart = now - 5 * min, lastStop = now - hour, now = now))
    }

    @Test
    fun hiddenForFifteenMinutesIsQuiet() {
        val now = 10 * hour
        val hidden = Visibility.sinceHidden(now - hour, now - 15 * min, now)
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, false, hidden, null))
        val justLeft = Visibility.sinceHidden(now - hour, now - 15 * min + 1, now)
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, false, justLeft, null))
        // Something playing in another app wins, however long Home has been hidden.
        assertEquals(SilentReason.PLAYING, SilentRules.quiet(true, false, hidden, null))
    }

    @Test
    fun nothingStoredOrTimesFromBeforeAReboot() {
        val now = 20 * min
        assertEquals(now, Visibility.sinceHidden(null, null, now))
        // Elapsed time restarts at boot: a stored time above now is from before it.
        assertEquals(now, Visibility.sinceHidden(lastStart = 5 * hour, lastStop = 6 * hour, now = now))
        assertEquals(10 * min, Visibility.sinceHidden(lastStart = 10 * min, lastStop = 6 * hour, now = now))
    }

    @Test
    fun keyTimeFallsBackToTheLastStart() {
        val now = 10 * hour
        assertEquals(min, Visibility.sinceKey(keyAt = now - min, startedAt = now - hour, now = now))
        assertEquals(hour, Visibility.sinceKey(keyAt = null, startedAt = now - hour, now = now))
        // A newer start than the last key: coming back to Home counts as use.
        assertEquals(min, Visibility.sinceKey(keyAt = now - hour, startedAt = now - min, now = now))
        assertNull(Visibility.sinceKey(null, null, now))
    }

    @Test
    fun onScreenNeedsThreeHoursWithoutAKey() {
        val now = 10 * hour
        val idle = Visibility.sinceKey(keyAt = now - 3 * hour, startedAt = now - 4 * hour, now = now)
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, true, 0, idle))
        val pressed = Visibility.sinceKey(keyAt = now - 3 * hour + 1, startedAt = now - 4 * hour, now = now)
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, true, 0, pressed))
        // No key and no start in this process: never idle.
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, true, 0, Visibility.sinceKey(null, null, now)))
    }
}
