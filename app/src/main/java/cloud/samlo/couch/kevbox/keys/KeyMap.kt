package cloud.samlo.couch.kevbox.keys

import org.json.JSONArray
import org.json.JSONObject

/** One remote mapping. At least one code is set; exactly one of [pkg] and [action] is set. */
data class KeyEntry(val keycode: Int?, val scancode: Int?, val pkg: String?, val action: String?)

/**
 * The remote's extra buttons: which key opens which app, or goes Home, or opens the drawer.
 * Plain Kotlin and org.json only, so the JVM tests run it as it is.
 */
object KeyMap {
    const val HOME = "home"
    const val DRAWER = "drawer"
    private val ACTIONS = setOf(HOME, DRAWER)

    /**
     * The KevBox remote's buttons. Keep these values: scancode 207 (Prime Video here) is Linux's
     * KEY_PLAY on many remotes, which is why [match] only looks at scancodes for unknown keycodes.
     */
    val DEFAULT: List<KeyEntry> = listOf(
        KeyEntry(193, 416, "tv.kevbox", null),
        KeyEntry(194, 207, "tv.kevbox.live", null),
        KeyEntry(190, 425, "org.smarttube.stable", null),
        KeyEntry(3, 172, null, HOME),
        KeyEntry(284, 216, null, DRAWER),
    )

    /** Valid entries of [array], in order; anything else is skipped. */
    fun parse(array: JSONArray): List<KeyEntry> =
        (0 until array.length()).mapNotNull { entry(array.opt(it)) }

    fun toJson(entries: List<KeyEntry>): JSONArray = JSONArray().apply {
        for (e in entries) {
            put(JSONObject().apply {
                e.keycode?.let { put("keycode", it) }
                e.scancode?.let { put("scancode", it) }
                e.pkg?.let { put("package", it) }
                e.action?.let { put("action", it) }
            })
        }
    }

    /**
     * The first entry whose keycode equals [keyCode]; only when [keyCode] is 0 (KEYCODE_UNKNOWN),
     * the first whose scancode equals [scanCode]; null when none.
     */
    fun match(entries: List<KeyEntry>, keyCode: Int, scanCode: Int): KeyEntry? = when {
        keyCode != 0 -> entries.firstOrNull { it.keycode == keyCode }
        scanCode != 0 -> entries.firstOrNull { it.scancode == scanCode }
        else -> null
    }

    private fun entry(value: Any?): KeyEntry? {
        val o = value as? JSONObject ?: return null
        val keycode = code(o, "keycode")
        val scancode = code(o, "scancode")
        // A code present with the wrong type spoils the entry rather than being silently dropped.
        if (keycode == BAD || scancode == BAD || (keycode == null && scancode == null)) return null
        if (o.has("package") == o.has("action")) return null
        val pkg = (o.opt("package") as? String)?.takeIf { it.isNotBlank() }
        val action = (o.opt("action") as? String)?.takeIf { it in ACTIONS }
        if (pkg == null && action == null) return null
        return KeyEntry(keycode, scancode, pkg, action)
    }

    /** The positive integer under [name], null when absent, [BAD] when present but not one. */
    private fun code(o: JSONObject, name: String): Int? {
        if (!o.has(name)) return null
        val n = when (val v = o.opt(name)) {
            is Int -> v.toLong()
            is Long -> v
            else -> return BAD
        }
        return if (n in 1..Int.MAX_VALUE) n.toInt() else BAD
    }

    /** Marks a code that is present but invalid; real codes are positive. */
    private const val BAD = -1
}
