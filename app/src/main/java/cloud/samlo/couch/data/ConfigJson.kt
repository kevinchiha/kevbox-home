package cloud.samlo.couch.data

import cloud.samlo.couch.kevbox.config.KevBoxConfig
import org.json.JSONArray
import org.json.JSONObject

/** The on-disk shape of [Config]. Key names are a user-facing contract: never rename them. */
internal object ConfigJson {
    fun encode(c: Config): JSONObject = KevBoxConfig.encode(c, couch(c))

    private fun couch(c: Config): JSONObject = JSONObject()
        .put("pinnedApps", JSONArray(c.pinnedApps))
        .put("hiddenApps", JSONArray(c.hiddenApps.sorted()))
        .put("hiddenInputs", JSONArray(c.hiddenInputs.sorted()))
        .put("names", JSONObject(c.names))
        .put("inputIcons", JSONObject(c.inputIcons))
        .put("channels", JSONObject(c.channels))
        .put("accent", c.accent)
        .put("wallpaper", c.wallpaper)
        .put("dim", c.dim)
        .put("clock24h", c.clock24h)
        .put("subreddit", c.subreddit)
        .put("wallpaperHours", c.wallpaperHours)
        .put("weather", c.weather)
        .put("weatherCity", c.weatherCity)
        .put("fahrenheit", c.fahrenheit)
        .put("checkUpdates", c.checkUpdates)

    /**
     * Every field is optional; a missing one keeps what [into] already holds. Keys of removed
     * features (godmode, hud, sync, pre-releases, ambient, the remote relay, the Rows layout) are
     * ignored and dropped on the next save.
     * The KevBox keys, `wallpaper` among them, go through [KevBoxConfig.decode] at the end.
     */
    fun decode(json: JSONObject, into: Config) {
        couch(json, into)
        KevBoxConfig.decode(json, into)
    }

    private fun couch(json: JSONObject, into: Config) = with(into) {
        json.strings("pinnedApps")?.let { pinnedApps = it }
        json.strings("hiddenApps")?.let { hiddenApps = it.toSet() }
        json.strings("hiddenInputs")?.let { hiddenInputs = it.toSet() }
        names = json.stringMap("names")
        inputIcons = json.stringMap("inputIcons")
        channels = json.optJSONObject("channels")?.let { o -> o.keys().asSequence().associateWith { o.optBoolean(it) } }
            ?: emptyMap()
        accent = Accents.migrate(json.optString("accent", accent))
        dim = json.optString("dim", dim)
        clock24h = json.optBoolean("clock24h", clock24h)
        subreddit = json.optString("subreddit", subreddit)
        wallpaperHours = json.optInt("wallpaperHours", wallpaperHours)
        weather = json.optBoolean("weather", weather)
        weatherCity = json.optString("weatherCity", weatherCity)
        fahrenheit = json.optBoolean("fahrenheit", fahrenheit)
        checkUpdates = json.optBoolean("checkUpdates", checkUpdates)
    }

    private fun JSONObject.strings(name: String): List<String>? {
        val array = optJSONArray(name) ?: return null
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun JSONObject.stringMap(name: String): Map<String, String> {
        val o = optJSONObject(name) ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
}
