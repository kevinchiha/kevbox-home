package cloud.samlo.couch.kevbox.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.PersistableBundle
import android.util.Log
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.update.Release
import cloud.samlo.couch.update.UpdateCheck
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the silent update (AC-9): every 6 h with a network, at most every 6 h while Android counts
 * the box as unused (screen off for a while), and once 15 min after the last Home activity stops.
 * Each run refreshes the stored release (the two 6-hourly ones ask tv.kevbox.dev at once, the
 * after-leaving one keeps the once-a-day limit) and then tries [SilentInstall.run], off the main
 * thread. Only one run at a time; [SilentInstall] logs each outcome under [SilentInstall.TAG].
 * Nothing is scheduled below Android 12, where an update always needs the confirm screen.
 */
class SilentJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EXTRA_TRIGGER) ?: TRIGGER_PERIODIC
        if (!running.compareAndSet(false, true)) {
            Log.i(SilentInstall.TAG, SilentInstall.line(trigger, null, SilentReason.BUSY.name, "a silent run is in progress"))
            return false
        }
        val app = applicationContext
        Thread({
            try {
                runOnce(app, trigger)
            } catch (e: Exception) {
                Log.i(SilentInstall.TAG, SilentInstall.line(trigger, null, SilentReason.FAILED.name, e.javaClass.simpleName))
            } finally {
                running.set(false)
                jobFinished(params, false)
                // Home left the screen during this run: queue the 15-minute run now that it can't stop this one.
                if (leftDuringRun.getAndSet(false)) afterLeaving(app)
            }
        }, "kevbox-silent-update").start()
        return true
    }

    /** The run keeps going: SilentInstall cannot stop mid-install, and the next run comes anyway. */
    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val PERIODIC_ID = 0x4b48_0601
        private const val AFTER_LEAVING_ID = 0x4b48_0602
        private const val IDLE_ID = 0x4b48_0603
        private const val PERIOD_MS = 6 * 60 * 60_000L
        private const val EXTRA_TRIGGER = "trigger"
        private const val TRIGGER_PERIODIC = "periodic"
        private const val TRIGGER_LEFT = "left"
        private const val TRIGGER_IDLE = "idle"

        private val running = AtomicBoolean(false)

        /** Home left the screen while a run was in progress; the run queues the 15-minute one when it ends. */
        private val leftDuringRun = AtomicBoolean(false)

        /**
         * Schedules the 6-hourly run and the 6-hourly unused-box run, each unless it is already
         * scheduled. Call on every app start.
         */
        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT < SilentRules.MIN_SDK) return
            schedulePeriodic(context, PERIODIC_ID, TRIGGER_PERIODIC, idle = false)
            // Android's "idle": screen off (or docked) for a while. Catches the box asleep with Home
            // on top, which the 6-hourly run only reaches when it happens to fall in that time.
            schedulePeriodic(context, IDLE_ID, TRIGGER_IDLE, idle = true)
        }

        private fun schedulePeriodic(context: Context, id: Int, trigger: String, idle: Boolean) {
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val pending = scheduler.getPendingJob(id)
                if (pending != null && pending.intervalMillis == PERIOD_MS && pending.isPersisted) return
                val job = JobInfo.Builder(id, component(context))
                    .setPeriodic(PERIOD_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresDeviceIdle(idle)
                    .setPersisted(true)
                    .setExtras(extras(trigger))
                    .build()
                scheduler.schedule(job)
            } catch (e: Exception) {
                Log.w(SilentInstall.TAG, "$trigger job not scheduled: ${e.javaClass.simpleName}")
            }
        }

        /**
         * Queues the run 15 min after Home left the screen. Same job id each time, so a newer stop
         * replaces the pending run and the 15 minutes count from it. While a run is in progress it
         * only notes the stop, since scheduling the same id would stop that run mid-install; the run
         * queues this one when it ends.
         */
        fun afterLeaving(context: Context) {
            if (!SilentRules.queuesAfterLeaving(Build.VERSION.SDK_INT, running.get())) {
                if (Build.VERSION.SDK_INT >= SilentRules.MIN_SDK && running.get()) leftDuringRun.set(true)
                return
            }
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val job = JobInfo.Builder(AFTER_LEAVING_ID, component(context))
                    .setMinimumLatency(SilentRules.OUT_OF_SIGHT_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setExtras(extras(TRIGGER_LEFT))
                    .build()
                scheduler.schedule(job)
            } catch (e: Exception) {
                Log.w(SilentInstall.TAG, "after-leaving job not scheduled: ${e.javaClass.simpleName}")
            }
        }

        /**
         * AC-10 for the Update chip: true while silent install can still handle [release] on this
         * box, so the chip stays hidden. About never asks this.
         */
        fun holdsPrompt(context: Context, release: Release): Boolean {
            val canInstall = try {
                context.packageManager.canRequestPackageInstalls()
            } catch (e: Exception) {
                false
            }
            return SilentRules.holdsPrompt(
                Build.VERSION.SDK_INT, release, System.currentTimeMillis(), canInstall,
                SilentState.snapshot(context, release.versionCode),
            )
        }

        /** One run. Blocking. */
        private fun runOnce(app: Context, trigger: String): SilentReason {
            try {
                UpdateCheck(app, Fetcher(app)).refresh(Config.load(UserFiles(app).dir), force = trigger != TRIGGER_LEFT)
            } catch (e: Exception) {
                // The stored release still counts.
            }
            return SilentInstall.run(app, trigger, { quiet(app) }, { Visibility.onScreen(app) == true })
        }

        /** AC-4, read now: nothing playing, and the screen off, Home out of sight for 15 min or idle on screen for 3 h. */
        private fun quiet(app: Context): SilentReason = SilentRules.quiet(
            playing(app), Visibility.onScreen(app), Visibility.sinceHiddenMs(app), Visibility.sinceKeyMs(), screenOff(app),
        )

        /** True when the screen is off. A screensaver counts as on; an error counts as on. */
        private fun screenOff(app: Context): Boolean = try {
            app.getSystemService(PowerManager::class.java)?.isInteractive == false
        } catch (e: Exception) {
            false
        }

        /** Any app playing sound. An error counts as playing. */
        private fun playing(app: Context): Boolean = try {
            app.getSystemService(AudioManager::class.java)?.isMusicActive ?: true
        } catch (e: Exception) {
            true
        }

        private fun component(context: Context) = ComponentName(context, SilentJob::class.java)

        private fun extras(trigger: String) = PersistableBundle().apply { putString(EXTRA_TRIGGER, trigger) }
    }
}
