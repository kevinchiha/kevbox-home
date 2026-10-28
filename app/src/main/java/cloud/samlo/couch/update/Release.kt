package cloud.samlo.couch.update

import cloud.samlo.couch.kevbox.update.VersionJson
import org.json.JSONObject

/**
 * A release published on tv.kevbox.dev: its name, the code that orders builds, the APK and the
 * SHA-256 that vouches for it. [version] keeps Couch's name because the Update chip and About read it.
 */
class Release(val version: String, val versionCode: Long, val apkUrl: String, val sha256: String, val notes: String) {
    /** Stored under version.json's own key names, so [deserialize] reuses its rules. */
    fun serialize(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", version)
        .put("url", apkUrl)
        .put("sha256", sha256)
        .put("notes", notes)
        .toString()

    companion object {
        /** A stored release, or null for broken text or Couch's older stored shape. */
        fun deserialize(text: String): Release? = VersionJson.read(text)
    }
}
