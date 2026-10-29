package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.update.SilentBlock
import cloud.samlo.couch.kevbox.update.SilentReason
import cloud.samlo.couch.kevbox.update.SilentRules
import cloud.samlo.couch.kevbox.update.SilentState
import cloud.samlo.couch.update.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentRulesTest {
    private val hash = "0123456789abcdef".repeat(4)
    private val apk = "https://tv.kevbox.dev/home/kevbox-home-0.8.1.1.apk"
    private val after = 1_761_700_000_000L
    private val min = 60_000L
    private val hour = 60 * min
    private val fresh = SilentState.Snapshot(0, false)

    private fun release(afterBox: Long? = after, block: Boolean = true) =
        Release("0.8.1.1", 80101, apk, hash, "", if (block) SilentBlock(after, emptyList()) else null, afterBox)

    private fun eligible(
        sdk: Int = 31,
        release: Release = release(),
        now: Long = after,
        canInstall: Boolean = true,
        state: SilentState.Snapshot = fresh,
    ) = SilentRules.eligible(sdk, release, now, canInstall, state)

    private fun holds(
        sdk: Int = 31,
        release: Release = release(),
        now: Long = after,
        canInstall: Boolean = true,
        state: SilentState.Snapshot = fresh,
    ) = SilentRules.holdsPrompt(sdk, release, now, canInstall, state)

    // AC-3

    @Test
    fun `a release past its after on Android 12 with the permission is eligible`() {
        assertEquals(SilentReason.ELIGIBLE, eligible())
        assertEquals(SilentReason.ELIGIBLE, eligible(sdk = 34, now = after + 100 * hour))
        assertEquals(SilentReason.ELIGIBLE, eligible(state = SilentState.Snapshot(2, false)))
    }

    @Test
    fun `each failed check has its own reason`() {
        assertEquals(SilentReason.OLD_ANDROID, eligible(sdk = 30))
        assertEquals(SilentReason.NO_BLOCK, eligible(release = release(afterBox = null, block = false)))
        assertEquals(SilentReason.NOT_YET, eligible(now = after - 1))
        assertEquals(SilentReason.NO_PERMISSION, eligible(canInstall = false))
        assertEquals(SilentReason.REFUSED, eligible(state = SilentState.Snapshot(0, true)))
        assertEquals(SilentReason.OUT_OF_ATTEMPTS, eligible(state = SilentState.Snapshot(3, false)))
        assertEquals(SilentReason.OUT_OF_ATTEMPTS, eligible(state = SilentState.Snapshot(7, false)))
    }

    @Test
    fun `checks run in the order the rules list them`() {
        assertEquals(SilentReason.OLD_ANDROID, eligible(sdk = 28, release = release(afterBox = null, block = false)))
        assertEquals(SilentReason.NOT_YET, eligible(now = after - 1, canInstall = false))
        assertEquals(SilentReason.NO_PERMISSION, eligible(canInstall = false, state = SilentState.Snapshot(3, true)))
        assertEquals(SilentReason.REFUSED, eligible(state = SilentState.Snapshot(3, true)))
    }

    @Test
    fun `the wait counts on the box clock after`() {
        // The block's own after is past, but on the box clock it is an hour away.
        val behind = release(afterBox = after + hour)
        assertEquals(SilentReason.NOT_YET, eligible(release = behind, now = after + hour - 1))
        assertEquals(SilentReason.ELIGIBLE, eligible(release = behind, now = after + hour))
        // A stored release with no box-clock after falls back on the block's.
        assertEquals(SilentReason.ELIGIBLE, eligible(release = release(afterBox = null), now = after))
        assertEquals(SilentReason.NOT_YET, eligible(release = release(afterBox = null), now = after - 1))
    }

    // AC-2

    @Test
    fun `now is the server Date when the response had one`() {
        assertEquals(500L, SilentRules.serverNow(500L, 900L))
        assertEquals(900L, SilentRules.serverNow(null, 900L))
        assertEquals(900L, SilentRules.serverNow(0L, 900L))
        assertEquals(900L, SilentRules.serverNow(-5L, 900L))
    }

    @Test
    fun `after moves onto the box clock by the gap between the two clocks`() {
        // Box clock a day behind the server: the box reaches after a day "earlier" by its own count.
        val day = 24 * hour
        val serverNow = after - hour
        val boxNow = serverNow - day
        val stored = SilentRules.onBoxClock(release(afterBox = null), serverNow, boxNow)
        assertEquals(after - day, stored.silentAfterBoxMs)
        assertEquals(SilentBlock(after, emptyList()), stored.silent)
        // An hour of box time later it is due, the same instant the server reaches after.
        assertEquals(SilentReason.ELIGIBLE, eligible(release = stored, now = boxNow + hour))
        assertEquals(SilentReason.NOT_YET, eligible(release = stored, now = boxNow + hour - 1))
    }

    @Test
    fun `with no server Date the box clock is used as is`() {
        val stored = SilentRules.onBoxClock(release(afterBox = null), null, after - hour)
        assertEquals(after, stored.silentAfterBoxMs)
    }

    @Test
    fun `a release with no block gets no box-clock after`() {
        assertNull(SilentRules.onBoxClock(release(afterBox = null, block = false), 5L, 9L).silentAfterBoxMs)
    }

    @Test
    fun `moving after never overflows`() {
        assertEquals(Long.MAX_VALUE, SilentRules.afterOnBox(Long.MAX_VALUE - 1, 0L, 10L))
        assertEquals(after, SilentRules.afterOnBox(after, Long.MIN_VALUE, Long.MAX_VALUE))
    }

    // AC-4

    @Test
    fun `out of sight for 15 minutes with nothing playing is quiet`() {
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, false, 15 * min, null))
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, false, 5 * hour, 1L))
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, false, 15 * min - 1, null))
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, false, null, 5 * hour))
    }

    @Test
    fun `on screen with no key for 3 hours and nothing playing is quiet`() {
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, true, null, 3 * hour))
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, true, 5 * hour, 3 * hour - 1))
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, true, 5 * hour, null))
    }

    @Test
    fun `unknown visibility counts as on screen`() {
        assertEquals(SilentReason.QUIET, SilentRules.quiet(false, null, null, 3 * hour))
        assertEquals(SilentReason.IN_USE, SilentRules.quiet(false, null, 5 * hour, 3 * hour - 1))
    }

    @Test
    fun `anything playing wins in both cases`() {
        assertEquals(SilentReason.PLAYING, SilentRules.quiet(true, false, 5 * hour, 5 * hour))
        assertEquals(SilentReason.PLAYING, SilentRules.quiet(true, true, 5 * hour, 5 * hour))
        assertEquals(SilentReason.PLAYING, SilentRules.quiet(true, null, null, null))
    }

    // AC-10

    @Test
    fun `the prompt is held while silent install can still handle the release`() {
        assertTrue(holds())
        assertTrue(holds(now = after - 10 * hour))
        assertTrue(holds(now = after + 72 * hour - 1))
        assertTrue(holds(state = SilentState.Snapshot(2, false)))
    }

    @Test
    fun `the prompt shows 72 hours after after, on the box clock`() {
        assertFalse(holds(now = after + 72 * hour))
        val behind = release(afterBox = after - hour)
        assertFalse(holds(release = behind, now = after + 71 * hour))
        assertTrue(holds(release = behind, now = after + 71 * hour - 1))
    }

    @Test
    fun `the prompt shows when silent install cannot handle the release`() {
        assertFalse(holds(sdk = 30))
        assertFalse(holds(canInstall = false))
        assertFalse(holds(release = release(afterBox = null, block = false)))
        assertFalse(holds(state = SilentState.Snapshot(0, true)))
        assertFalse(holds(state = SilentState.Snapshot(3, false)))
    }

    // AC-9: leaving Home never re-schedules over a run in progress

    @Test
    fun `leaving Home queues a run only on Android 12+ with no run in progress`() {
        assertTrue(SilentRules.queuesAfterLeaving(31, running = false))
        assertFalse(SilentRules.queuesAfterLeaving(31, running = true))
        assertFalse(SilentRules.queuesAfterLeaving(30, running = false))
    }

    // AC-3: attempts and refusals belong to one versionCode

    @Test
    fun `state stored for another versionCode starts over`() {
        val same = SilentState.of(80101, 2, true, 80101)
        assertEquals(2, same.attempts)
        assertTrue(same.refused)
        val other = SilentState.of(80101, 2, true, 80102)
        assertEquals(0, other.attempts)
        assertFalse(other.refused)
    }
}
