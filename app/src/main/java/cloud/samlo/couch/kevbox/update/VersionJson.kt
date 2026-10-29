package cloud.samlo.couch.kevbox.update

import cloud.samlo.couch.update.Release
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Reads `tv.kevbox.dev/home/version.json`, the same shape KevBox TV publishes. Pure Kotlin and
 * org.json, so the rules run in JVM tests: a release is only offered when every field is sound.
 */
object VersionJson {
    const val URL = "https://tv.kevbox.dev/home/version.json"
    private const val HOST = "tv.kevbox.dev"
    private val HEX64 = Regex("[0-9a-fA-F]{64}")

    /** A release worth offering: versionCode above [installedCode], url https on tv.kevbox.dev, sha256 64 hex chars; else null. */
    fun parse(text: String, installedCode: Long): Release? =
        read(text)?.takeIf { it.versionCode > installedCode }

    /** True when [hex] equals [expected], ignoring case. */
    fun matches(hex: String, expected: String): Boolean =
        hex.isNotBlank() && expected.isNotBlank() && hex.equals(expected, ignoreCase = true)

    /**
     * The shape rules alone, with no installed-version filter; [Release.deserialize] uses them too,
     * so a stored release passes the same checks it passed when fetched. Extra keys (KevBox TV's
     * `profiles`) are ignored. A broken `silent` block drops only the block, never the release.
     */
    internal fun read(text: String): Release? = try {
        val json = JSONObject(text)
        // org.json gives Int or Long for a whole number; a quoted or fractional code is refused.
        val code = when (val raw = json.get("versionCode")) {
            is Int -> raw.toLong()
            is Long -> raw
            else -> 0L
        }
        val name = json.get("versionName") as? String
        val url = json.getString("url")
        val sha256 = json.getString("sha256")
        if (code > 0 && !name.isNullOrBlank() && trusted(url) && HEX64.matches(sha256)) {
            Release(name, code, url, sha256, json.optString("notes", ""), SilentBlock.parse(json.opt("silent")))
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * https on exactly tv.kevbox.dev, with no user-info. The SHA-256 comes from the same server, but
     * the host pin keeps a typo in version.json from sending every box somewhere else.
     */
    private fun trusted(url: String): Boolean {
        val uri = URI(url)
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.rawUserInfo == null
    }
}

/**
 * version.json's `silent` block: from [afterMs] (epoch ms, server clock) the release may install with
 * no screen. [earlyUsers] is read so the shape matches KevBox TV and KevBox Live, but Home has no
 * sign-in, so nothing uses it.
 */
data class SilentBlock(val afterMs: Long, val earlyUsers: List<String>) {
    companion object {
        /**
         * The block, or null for anything else: `after` must be a positive bare JSON integer (not
         * `"5"`, `1.5` or null), `earlyUsers` missing, null or an array of strings. Never throws.
         */
        fun parse(raw: Any?): SilentBlock? = try {
            val json = raw as? JSONObject
            val afterMs = when (val after = json?.opt("after")) {
                is Int -> after.toLong()
                is Long -> after
                else -> 0L
            }
            val earlyUsers = when (val users = json?.opt("earlyUsers")) {
                null, JSONObject.NULL -> emptyList()
                is JSONArray -> (0 until users.length()).map { users.opt(it) as? String }
                else -> listOf(null)
            }
            if (afterMs > 0 && null !in earlyUsers) SilentBlock(afterMs, earlyUsers.filterNotNull()) else null
        } catch (e: Exception) {
            null
        }
    }
}
