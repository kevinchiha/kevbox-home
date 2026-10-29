package cloud.samlo.couch.kevbox.update

import android.annotation.SuppressLint
import android.content.Context
import cloud.samlo.couch.data.Prefs

/**
 * Silent attempts made for one versionCode, and whether Android asked for a confirm screen for it.
 * Only the latest versionCode is kept: a new one starts with no attempts and not refused.
 */
object SilentState {
    private const val CODE = "update:silentCode"
    private const val ATTEMPTS = "update:silentAttempts"
    private const val REFUSED = "update:silentRefused"

    class Snapshot(val attempts: Int, val refused: Boolean)

    fun snapshot(context: Context, versionCode: Long): Snapshot {
        val prefs = Prefs.of(context)
        return of(prefs.getLong(CODE, -1L), prefs.getInt(ATTEMPTS, 0), prefs.getBoolean(REFUSED, false), versionCode)
    }

    /**
     * Counts one attempt for [versionCode]. Written with `commit`, not `apply`: the attempt is
     * counted right before the install, and the install kills this process.
     */
    fun recordAttempt(context: Context, versionCode: Long) = synchronized(this) {
        val now = snapshot(context, versionCode)
        save(context, versionCode, now.attempts + 1, now.refused)
    }

    /** Marks [versionCode] refused: Android wanted a screen, so silent install stops trying it. */
    fun markRefused(context: Context, versionCode: Long) = synchronized(this) {
        save(context, versionCode, snapshot(context, versionCode).attempts, true)
    }

    /** The state for [versionCode], given what is stored; state for another code counts as none. */
    internal fun of(storedCode: Long, attempts: Int, refused: Boolean, versionCode: Long): Snapshot =
        if (storedCode == versionCode) Snapshot(attempts, refused) else Snapshot(0, false)

    @SuppressLint("ApplySharedPref") // see recordAttempt
    private fun save(context: Context, versionCode: Long, attempts: Int, refused: Boolean) {
        Prefs.of(context).edit()
            .putLong(CODE, versionCode)
            .putInt(ATTEMPTS, attempts)
            .putBoolean(REFUSED, refused)
            .commit()
    }
}
