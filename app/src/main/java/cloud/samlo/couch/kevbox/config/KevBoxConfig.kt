package cloud.samlo.couch.kevbox.config

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.kevbox.keys.KeyMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * The KevBox keys of `config.json` and of the provision file, which share one schema. This is the
 * only parser of that schema: [Config.fromJson] and the provisioner both come through [decode].
 * A key with the wrong type is skipped on its own and keeps the saved value, the way ConfigJson
 * treats a broken Couch value; it never throws the whole file away.
 */
object KevBoxConfig {
    /** Every top-right button a setup file may name. */
    val BUTTONS: List<String> = listOf("wifi", "vpn", "bluetooth", "speedtest", "settings")
    /** The buttons when none are saved: all but Bluetooth, which Kevin dropped on 2026-10-30. */
    val DEFAULT_BUTTONS: List<String> = BUTTONS - "bluetooth"
    const val DEFAULT_SPEEDTEST = "com.rma.netpulsetv"
    val WALLPAPERS: Set<String> = setOf("daily", "reddit", "gradient", "custom")

    /** Copies each KevBox key in [json] whose value has the right type into [into]; returns the names applied. */
    fun decode(json: JSONObject, into: Config): Set<String> {
        val applied = linkedSetOf<String>()
        fun apply(name: String, ok: Boolean) {
            if (ok) applied += name
        }
        apply("apps", packages(json, "apps")?.also { into.apps = it } != null)
        apply("drawer", packages(json, "drawer")?.also { into.drawer = it } != null)
        apply("hidden", packages(json, "hidden")?.also { into.hidden = it.toSet() } != null)
        apply("buttons", strings(json, "buttons")?.also { list ->
            into.buttons = list.filter { it in BUTTONS }.distinct()
        } != null)
        apply("speedtestApp", (json.opt("speedtestApp") as? String)?.takeIf { it.isNotBlank() }
            ?.also { into.speedtestApp = it } != null)
        apply("keys", json.optJSONArray("keys")?.also { into.keys = KeyMap.parse(it) } != null)
        apply("wallpaper", (json.opt("wallpaper") as? String)?.takeIf { it in WALLPAPERS }
            ?.also { into.wallpaper = it } != null)
        return applied
    }

    /** Writes apps, drawer, hidden, buttons, speedtestApp and keys into [json]; returns [json]. */
    fun encode(c: Config, json: JSONObject): JSONObject = json
        .put("apps", JSONArray(c.apps))
        .put("drawer", JSONArray(c.drawer))
        .put("hidden", JSONArray(c.hidden.sorted()))
        .put("buttons", JSONArray(c.buttons))
        .put("speedtestApp", c.speedtestApp)
        .put("keys", KeyMap.toJson(c.keys))

    /**
     * The buttons to show: [Config.buttons], or [DEFAULT_BUTTONS] when it is empty. A list without
     * Settings gets it at the end, so Customize stays reachable from the remote.
     */
    fun buttonsOf(c: Config): List<String> = when {
        c.buttons.isEmpty() -> DEFAULT_BUTTONS
        SETTINGS in c.buttons -> c.buttons
        else -> c.buttons + SETTINGS
    }

    private const val SETTINGS = "settings"

    /** A string array as packages: order kept, blanks and repeats dropped. */
    private fun packages(json: JSONObject, name: String): List<String>? =
        strings(json, name)?.filter { it.isNotBlank() }?.distinct()

    /** The array under [name] when every element is a string, else null. */
    private fun strings(json: JSONObject, name: String): List<String>? {
        val array = json.opt(name) as? JSONArray ?: return null
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) out += array.opt(i) as? String ?: return null
        return out
    }
}
