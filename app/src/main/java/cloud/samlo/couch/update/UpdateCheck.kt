package cloud.samlo.couch.update

import android.content.Context
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.Prefs
import cloud.samlo.couch.kevbox.update.SilentRules
import cloud.samlo.couch.kevbox.update.VersionJson

/**
 * Asks tv.kevbox.dev for the current release at most once a day, like the weather: the answer is
 * kept in preferences, so a failed request changes nothing and the home screen and settings read it
 * offline.
 */
class UpdateCheck(context: Context, private val fetcher: Fetcher) {
    private val prefs = Prefs.of(context)
    private val installedCode = Versions.installedCode(context)

    /**
     * Asks tv.kevbox.dev when the last check is a day old, or at once when [force] is true (the
     * silent-update job and About). Blocking: run it off the loader thread so a slow network never
     * holds up the home screen. True when it found a release newer than this build.
     */
    fun refresh(config: Config, force: Boolean = false): Boolean {
        if (!config.checkUpdates) return false
        val now = System.currentTimeMillis()
        // A clock that jumped backwards (no real-time clock until the network time arrives) counts as due.
        if (!force && now - prefs.getLong(CHECKED_AT, 0L) in 0 until DAY_MS) return false
        // Recorded before the request, so an unreachable server is asked once a day, not on every resume.
        prefs.edit().putLong(CHECKED_AT, now).apply()
        val fetched = fetcher.fetch(VersionJson.URL) ?: return false
        val release = VersionJson.parse(fetched.text, installedCode)
        if (release == null) {
            // Not newer, or broken: a build pulled from the server stops being offered.
            prefs.edit().remove(LATEST_RELEASE).apply()
            return false
        }
        // The silent block's `after` is stored on the box clock, by the server's Date header.
        val stored = SilentRules.onBoxClock(release, fetched.dateMs, System.currentTimeMillis())
        prefs.edit().putString(LATEST_RELEASE, stored.serialize()).apply()
        return pending(config) != null
    }

    /** What the last check found, while it is newer than this build. No network. */
    fun pending(config: Config): Release? {
        if (!config.checkUpdates) return null
        val release = prefs.getString(LATEST_RELEASE, null)?.let(Release::deserialize) ?: return null
        return release.takeIf { it.versionCode > installedCode }
    }

    companion object {
        private const val CHECKED_AT = "update:checkedAt"
        private const val LATEST_RELEASE = "update:latest"
        private const val DAY_MS = 24 * 60 * 60_000L

        /** Drops the stored answer, so the next load asks tv.kevbox.dev again. */
        fun forget(context: Context) {
            Prefs.of(context).edit().remove(CHECKED_AT).remove(LATEST_RELEASE).apply()
        }
    }
}
