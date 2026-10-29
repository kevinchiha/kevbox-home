package cloud.samlo.couch.data

import cloud.samlo.couch.kevbox.config.KevBoxConfig
import cloud.samlo.couch.kevbox.keys.KeyEntry
import cloud.samlo.couch.kevbox.keys.KeyMap
import java.io.File

/**
 * Everything the user can customise, stored in the app's external files folder
 * (`Android/data/tv.kevbox.home/files/config.json`, `tv.kevbox.home.dev` for debug builds) so
 * the provision file and ADB can edit or back it up. Custom images go next to it in
 * `icons/<package>.png` and `wallpaper.jpg`.
 */
class Config {
    // Apps and inputs
    var pinnedApps: List<String> = DEFAULT_PINNED
    var hiddenApps: Set<String> = DEFAULT_HIDDEN
    var hiddenInputs: Set<String> = emptySet()
    /** Custom names, keyed like icons. */
    var names: Map<String, String> = emptyMap()
    /** Input key to an [InputIcon] id. */
    var inputIcons: Map<String, String> = emptyMap()

    // Channels
    /** Channel id to shown or not, overriding what the stock launcher had enabled. */
    var channels: Map<String, Boolean> = emptyMap()

    // Look
    var accent: String = Accents.default
    var wallpaper: String = "daily"
    /** KevBox: how dark the Index backdrop gets (upstream dropped this with the Rows hero). */
    var dim: String = "medium"
    var clock24h = true

    // Wallpaper: "daily" (Bing photo of the day), "reddit", "gradient" or "custom" (wallpaper.jpg)
    var subreddit = DEFAULT_SUBREDDIT
    var wallpaperHours = 3

    // Weather
    var weather = true
    /** Empty means locate by IP address. */
    var weatherCity = ""
    var fahrenheit = false

    /** Once a day, look for a newer release on tv.kevbox.dev. */
    var checkUpdates = true

    // KevBox: read and written by KevBoxConfig, the one parser shared with the provision file
    /** The left column, in order. */
    var apps: List<String> = emptyList()
    /** Shown first in the drawer, in order. */
    var drawer: List<String> = emptyList()
    /** Never in the column or the drawer. */
    var hidden: Set<String> = emptySet()
    /** Empty means KevBoxConfig.BUTTONS. */
    var buttons: List<String> = emptyList()
    var speedtestApp: String = KevBoxConfig.DEFAULT_SPEEDTEST
    var keys: List<KeyEntry> = KeyMap.DEFAULT

    /**
     * Written atomically so a half-written file never wipes the user's setup. Home, the drawer's
     * thread and the provision loader all save; the lock keeps two of them from writing the one
     * temp file at once and mixing their bytes.
     */
    fun save(dir: File?) {
        dir ?: return
        val text = toJson()
        synchronized(SAVE_LOCK) {
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, FILE))
        }
    }

    fun toJson(): String = ConfigJson.encode(this).toString(2)

    companion object {
        const val FILE = "config.json"
        const val DEFAULT_SUBREDDIT = "WidescreenWallpaper"

        /** Defaults stay generic: the user pins their own apps. */
        val DEFAULT_PINNED = emptyList<String>()

        /**
         * Empty: KevBox Home has no screen to un-hide an app, so only the provision file's
         * `hidden` hides one.
         */
        val DEFAULT_HIDDEN = emptySet<String>()

        private val SAVE_LOCK = Any()

        fun load(dir: File?): Config {
            val text = try {
                dir?.let { File(it, FILE) }?.takeIf { it.isFile }?.readText()
            } catch (e: Exception) {
                null
            } ?: return Config()
            return fromJson(text)
        }

        /** Unreadable or malformed text gives the defaults, never a crash. */
        fun fromJson(text: String): Config {
            val config = Config()
            val json = try {
                org.json.JSONObject(text)
            } catch (e: Exception) {
                return config
            }
            ConfigJson.decode(json, config)
            return config
        }
    }
}
