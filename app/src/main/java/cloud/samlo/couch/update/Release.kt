package cloud.samlo.couch.update

import cloud.samlo.couch.kevbox.update.SilentBlock
import cloud.samlo.couch.kevbox.update.VersionJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * A release published on tv.kevbox.dev: its name, the code that orders builds, the APK and the
 * SHA-256 that vouches for it. [version] keeps Couch's name because the Update chip and About read it.
 * [silent] is version.json's silent-install block; [silentAfterBoxMs] is its `after` moved onto this
 * box's clock when the release was fetched, so later checks need no network.
 */
class Release(
    val version: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val silent: SilentBlock? = null,
    val silentAfterBoxMs: Long? = null,
) {
    /** Stored under version.json's own key names, so [deserialize] reuses its rules. */
    fun serialize(): String {
        val json = JSONObject()
            .put("versionCode", versionCode)
            .put("versionName", version)
            .put("url", apkUrl)
            .put("sha256", sha256)
            .put("notes", notes)
        if (silent != null) {
            json.put("silent", JSONObject().put("after", silent.afterMs).put("earlyUsers", JSONArray(silent.earlyUsers)))
            if (silentAfterBoxMs != null) json.put(AFTER_BOX, silentAfterBoxMs)
        }
        return json.toString()
    }

    /** This release with [afterBoxMs] as its box-clock `after`; dropped when there is no silent block. */
    fun withSilentAfterBox(afterBoxMs: Long?): Release =
        Release(version, versionCode, apkUrl, sha256, notes, silent, afterBoxMs?.takeIf { silent != null })

    companion object {
        private const val AFTER_BOX = "silentAfterBoxMs"

        /** A stored release, or null for broken text or Couch's older stored shape. */
        fun deserialize(text: String): Release? {
            val release = VersionJson.read(text) ?: return null
            val afterBox = when (val raw = JSONObject(text).opt(AFTER_BOX)) {
                is Int -> raw.toLong()
                is Long -> raw
                else -> null
            }
            return release.withSilentAfterBox(afterBox)
        }
    }
}
