package cloud.samlo.couch.kevbox.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import cloud.samlo.couch.data.Prefs

/**
 * What the silent update's "not in use" rule (AC-4) needs to know about KevBox Home's own screens:
 * whether one is on screen, how long all of them have been out of sight, and how long since a
 * remote key was pressed. Times are [SystemClock.elapsedRealtime] values, which count from boot.
 *
 * The count of started activities and the key time live in memory. The last start and stop are
 * also stored, so a job in a fresh process knows how long Home has been hidden. Home's three
 * activities report their own start, stop and key-downs; the accessibility service reports every
 * key-down while it runs, in any app.
 */
object Visibility {
    private const val LAST_START = "update:visibleStartAt"
    private const val LAST_STOP = "update:visibleStopAt"

    private val count = Count()

    /** The last activity start in this process, or null. */
    @Volatile private var startedAt: Long? = null

    /** The last key-down in this process, or null. */
    @Volatile private var keyAt: Long? = null

    fun started(activity: Activity) {
        val now = SystemClock.elapsedRealtime()
        count.start()
        startedAt = now
        Prefs.of(activity).edit().putLong(LAST_START, now).apply()
    }

    /** When the last started activity stops, queues the job that runs 15 min later ([SilentJob.afterLeaving]). */
    fun stopped(activity: Activity) {
        val now = SystemClock.elapsedRealtime()
        val left = count.stop()
        // Stored before the job is queued: the job measures its 15 minutes from it.
        Prefs.of(activity).edit().putLong(LAST_STOP, now).apply()
        if (left == 0 && !activity.isChangingConfigurations) SilentJob.afterLeaving(activity)
    }

    fun keyPressed() {
        keyAt = SystemClock.elapsedRealtime()
    }

    /** True while a Home activity is started, false when none is, null when the count went wrong. */
    @Suppress("UNUSED_PARAMETER") // The count lives in memory; the context keeps the interface open.
    fun onScreen(context: Context): Boolean? = count.onScreen

    /**
     * How long every Home activity has been out of sight: since the later of the stored last start
     * and last stop, or since boot when neither is stored. Only meaningful while [onScreen] is false.
     */
    fun sinceHiddenMs(context: Context): Long? {
        val prefs = Prefs.of(context)
        return sinceHidden(stored(prefs, LAST_START), stored(prefs, LAST_STOP), SystemClock.elapsedRealtime())
    }

    /** Since the last key-down, or since the last activity start when no key was pressed; null when neither happened in this process. */
    fun sinceKeyMs(): Long? = sinceKey(keyAt, startedAt, SystemClock.elapsedRealtime())

    private fun stored(prefs: SharedPreferences, key: String): Long? =
        if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    /**
     * The later of [lastStart] and [lastStop] counted back from [now]. A stored time above [now]
     * comes from before a reboot (elapsed time restarts at boot) and is dropped; with nothing left,
     * Home has been hidden since boot, so the answer is [now] itself.
     */
    internal fun sinceHidden(lastStart: Long?, lastStop: Long?, now: Long): Long {
        val last = listOfNotNull(lastStart, lastStop).filter { it in 0..now }.maxOrNull() ?: return now
        return now - last
    }

    /** Since the later of [keyAt] and [startedAt]; null when neither is known. */
    internal fun sinceKey(keyAt: Long?, startedAt: Long?, now: Long): Long? {
        val last = listOfNotNull(keyAt, startedAt).filter { it <= now }.maxOrNull() ?: return null
        return now - last
    }

    /**
     * Started activities. A stop with none started means a start was missed, so the count can no
     * longer be trusted and [onScreen] stays unknown for the rest of the process.
     */
    internal class Count {
        private var started = 0
        private var broken = false

        val onScreen: Boolean? @Synchronized get() = if (broken) null else started > 0

        @Synchronized
        fun start() {
            started++
        }

        /** Returns how many activities are still started. */
        @Synchronized
        fun stop(): Int {
            if (started == 0) broken = true else started--
            return started
        }
    }
}
