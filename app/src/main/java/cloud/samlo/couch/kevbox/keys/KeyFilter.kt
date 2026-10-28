package cloud.samlo.couch.kevbox.keys

/**
 * Per-press state for mapped keys: consumes the down, its repeats and the up of a matched key and
 * acts once, on the up. Plain values in, so it runs in JVM tests.
 *
 * Acting on the up keeps one press to one action however long the button is held; repeats are
 * swallowed rather than counted, since long-press mappings are a non-goal. The entry is fixed at
 * key-down so a config reload partway through a press cannot change what the press does.
 */
class KeyFilter(
    private val entries: () -> List<KeyEntry>,
    private val installed: (pkg: String) -> Boolean,
    private val act: (KeyEntry) -> Unit,
) {
    /** Presses in progress, keyed by their keycode and scancode, with the entry matched at key-down. */
    private val held = HashMap<Pair<Int, Int>, KeyEntry>()

    /** True when the event is consumed. [action] is KeyEvent.ACTION_DOWN or ACTION_UP. */
    fun onKey(action: Int, keyCode: Int, scanCode: Int, repeatCount: Int): Boolean {
        val press = keyCode to scanCode
        return when (action) {
            DOWN -> if (repeatCount > 0) press in held else down(press, keyCode, scanCode)
            UP -> {
                val entry = held.remove(press) ?: return false
                act(entry)
                true
            }
            else -> false
        }
    }

    private fun down(press: Pair<Int, Int>, keyCode: Int, scanCode: Int): Boolean {
        val entry = KeyMap.match(entries(), keyCode, scanCode) ?: return false
        // An app that isn't installed leaves the key to whatever app is in front.
        if (entry.pkg != null && !installed(entry.pkg)) return false
        held[press] = entry
        return true
    }

    private companion object {
        /** KeyEvent.ACTION_DOWN and ACTION_UP, kept literal so this class needs no Android stubs. */
        const val DOWN = 0
        const val UP = 1
    }
}
