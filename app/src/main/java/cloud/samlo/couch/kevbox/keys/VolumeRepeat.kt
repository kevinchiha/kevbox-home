package cloud.samlo.couch.kevbox.keys

/**
 * The held-volume ramp, made by KevBox Home. An accessibility service that filters keys breaks
 * Android's own repeat for the keys it passes through, so with the service on a held Volume key
 * changed the volume by one step only. This class only watches: the caller still passes every
 * volume event on, so the system handles the press itself and one press stays one step.
 *
 * A volume key-down with repeat count 0 calls [adjust] after [timeoutMs], then every [delayMs],
 * until that key's up. A down of the other volume key moves the repeat to it, as Android does.
 * Plain values in, so it runs in JVM tests.
 */
class VolumeRepeat(
    private val schedule: (delayMs: Long, run: () -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val adjust: (raise: Boolean) -> Unit,
    private val timeoutMs: Long,
    private val delayMs: Long,
) {
    /** The volume keycode being held, or null when no repeat is running. */
    private var held: Int? = null

    /** [action] is KeyEvent.ACTION_DOWN or ACTION_UP. Never consumes anything: there is no return value. */
    fun onKey(action: Int, keyCode: Int, repeatCount: Int) {
        if (keyCode != VOLUME_UP && keyCode != VOLUME_DOWN) return
        when (action) {
            // Repeats the system still sends are ignored: the schedule below already runs.
            DOWN -> if (repeatCount == 0) {
                cancel()
                held = keyCode
                schedule(timeoutMs, ::tick)
            }
            UP -> if (keyCode == held) stop()
        }
    }

    /** Ends any repeat in progress. */
    fun stop() {
        cancel()
        held = null
    }

    private fun tick() {
        val key = held ?: return
        adjust(key == VOLUME_UP)
        schedule(delayMs, ::tick)
    }

    private companion object {
        /** KeyEvent's values, kept literal so this class needs no Android stubs. */
        const val DOWN = 0
        const val UP = 1
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
    }
}
