package cloud.samlo.couch.kevbox

import android.view.KeyEvent
import cloud.samlo.couch.kevbox.keys.VolumeRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeRepeatTest {
    /** A fake clock: scheduled runs wait here until [advance] passes their due time. */
    private var now = 0L
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()
    private var scheduled = 0
    private val adjusts = mutableListOf<Boolean>()

    private val repeat = VolumeRepeat(
        schedule = { delay, run -> scheduled++; pending += (now + delay) to run },
        cancel = { pending.clear() },
        adjust = { adjusts += it },
        timeoutMs = TIMEOUT,
        delayMs = DELAY,
    )

    private fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = pending.filter { it.first <= end }.minByOrNull { it.first } ?: break
            pending.remove(next)
            now = next.first
            next.second()
        }
        now = end
    }

    private fun down(key: Int, repeatCount: Int = 0) = repeat.onKey(KeyEvent.ACTION_DOWN, key, repeatCount)
    private fun up(key: Int) = repeat.onKey(KeyEvent.ACTION_UP, key, 0)

    @Test
    fun heldKeyRepeatsAfterTheTimeoutUntilItsUp() {
        down(UP)
        advance(TIMEOUT - 1)
        assertTrue(adjusts.isEmpty())
        advance(1)
        assertEquals(listOf(true), adjusts)
        advance(DELAY * 3)
        assertEquals(listOf(true, true, true, true), adjusts)
        up(UP)
        advance(DELAY * 10)
        assertEquals(4, adjusts.size)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun shortPressNeverAdjusts() {
        down(DOWN)
        advance(TIMEOUT / 2)
        up(DOWN)
        advance(TIMEOUT * 4)
        assertTrue(adjusts.isEmpty())
    }

    @Test
    fun systemRepeatsDoNotRestartTheTimer() {
        down(DOWN)
        advance(TIMEOUT)
        down(DOWN, 1)
        down(DOWN, 2)
        advance(DELAY)
        assertEquals(listOf(false, false), adjusts)
        // The first schedule plus one per adjust; the repeats added none.
        assertEquals(3, scheduled)
    }

    @Test
    fun otherVolumeKeySwitchesTheRepeat() {
        down(UP)
        advance(TIMEOUT)
        assertEquals(listOf(true), adjusts)
        down(DOWN)
        advance(TIMEOUT - 1)
        assertEquals(listOf(true), adjusts)
        // The first key's up is not the one repeating now: nothing changes.
        up(UP)
        advance(1)
        advance(DELAY)
        assertEquals(listOf(true, false, false), adjusts)
        up(DOWN)
        advance(DELAY * 5)
        assertEquals(3, adjusts.size)
    }

    @Test
    fun stopEndsTheRepeat() {
        down(UP)
        advance(TIMEOUT)
        repeat.stop()
        advance(DELAY * 5)
        assertEquals(listOf(true), adjusts)
        // A later up after stop changes nothing.
        up(UP)
        advance(DELAY * 5)
        assertEquals(1, adjusts.size)
    }

    @Test
    fun nonVolumeKeysNeverSchedule() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_MUTE, 193)) {
            down(key)
            down(key, 1)
            up(key)
        }
        advance(TIMEOUT * 10)
        assertEquals(0, scheduled)
        assertTrue(adjusts.isEmpty())
    }

    private companion object {
        const val UP = KeyEvent.KEYCODE_VOLUME_UP
        const val DOWN = KeyEvent.KEYCODE_VOLUME_DOWN
        const val TIMEOUT = 500L
        const val DELAY = 50L
    }
}
