package cloud.samlo.couch

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import cloud.samlo.couch.data.Prefs
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.keys.KeyActions
import cloud.samlo.couch.kevbox.keys.KeyFilter
import cloud.samlo.couch.kevbox.keys.VolumeRepeat
import cloud.samlo.couch.kevbox.restart.ForceStop

/**
 * Three jobs that need an accessibility service. It filters the remote's keys through the
 * provisioned map, so app buttons open KevBox's apps from anywhere; a key with no mapping passes
 * through untouched. Filtering keys stops Android repeating a held key, so [VolumeRepeat] makes the
 * held-volume ramp itself while the volume events still pass on. It presses Force stop for Restart app
 * ([ForceStop]). And on TVs that hardwire HOME to the stock launcher, it replaces that launcher's
 * window with KevBox Home when one appears (see `res/xml/home_accessibility.xml`).
 */
class HomeAccessibilityService : AccessibilityService() {
    private var filter: KeyFilter? = null
    private var restartRun: ForceStop? = null
    private var volume: VolumeRepeat? = null
    private val volumeHandler = Handler(Looper.getMainLooper())

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Watches volume keys only and never consumes them, so the system still takes each press.
        volume?.onKey(event.action, event.keyCode, event.repeatCount)
        return filter?.onKey(event.action, event.keyCode, event.scanCode, event.repeatCount) ?: false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!isStockHome(event.packageName?.toString(), event.className?.toString())) return
        openCouch()
    }

    override fun onInterrupt() {
        volume?.stop()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = this
        val actions = KeyActions(this)
        filter = KeyFilter(actions::entries, actions::installed, actions::act)
        val audio = getSystemService(AudioManager::class.java)
        volume = VolumeRepeat(
            schedule = { delay, run -> volumeHandler.postDelayed({ run() }, delay) },
            cancel = { volumeHandler.removeCallbacksAndMessages(null) },
            adjust = { raise ->
                audio?.adjustSuggestedStreamVolume(
                    if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    AudioManager.FLAG_SHOW_UI,
                )
            },
            timeoutMs = ViewConfiguration.getKeyRepeatTimeout().toLong(),
            delayMs = ViewConfiguration.getKeyRepeatDelay().toLong(),
        )
        // Remember that HOME capture is wanted, so it can be restored after Android drops it.
        wanted(this, true)
        // A self-update restarts the app and leaves the stock launcher showing; come back to KevBox Home.
        val prefs = Prefs.of(this)
        val since = System.currentTimeMillis() - prefs.getLong(REOPEN, 0L)
        prefs.edit().remove(REOPEN).apply()
        if (since in 0..REOPEN_WINDOW_MS) openCouch()
    }

    override fun onDestroy() {
        if (running === this) running = null
        restartRun?.cancel()
        restartRun = null
        volume?.stop()
        volume = null
        super.onDestroy()
    }

    /** Through the HOME intent, never an explicit MainActivity start: that could make a second Home. */
    private fun openCouch() = KevBox.goHome(this)

    /** Starts a Restart app run for [pkg], cancelling any run still in progress. */
    private fun runRestart(pkg: String) {
        restartRun?.cancel()
        restartRun = ForceStop(this, pkg).also { it.start() }
    }

    companion object {
        private const val WANTED = "homeCaptureWanted"
        private const val REOPEN = "update:reopenAt"
        /**
         * Launchers whose package also hosts other screens, mapped to their home screen: Fire TV
         * Settings lives in Amazon's, the profile chooser in Google TV's. Only the home screen is
         * replaced. Every other launcher in `res/xml/home_accessibility.xml` is replaced whole.
         */
        private val SHARED_LAUNCHERS = mapOf(
            "com.amazon.tv.launcher" to "com.amazon.tv.launcher.ui.HomeActivity_vNext",
            "com.google.android.apps.tv.launcherx" to "com.google.android.apps.tv.launcherx.home.HomeActivity",
        )

        /**
         * The TV settings apps, listed in `res/xml/home_accessibility.xml` only so Restart app can
         * press Force stop there. They are never a home screen, so opening settings never bounces.
         */
        private val SETTINGS_APPS = setOf("com.android.tv.settings", "com.android.settings")

        /** True when a window from [pkg] with [cls] is the stock home screen KevBox Home should replace. */
        fun isStockHome(pkg: String?, cls: String?): Boolean {
            if (pkg in SETTINGS_APPS) return false
            val home = SHARED_LAUNCHERS[pkg] ?: return true
            return cls == home
        }
        /** An update that hasn't landed by then was cancelled or failed: never reopen on a later connect. */
        private const val REOPEN_WINDOW_MS = 10 * 60_000L
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

        /** The connected service, so Restart app can hand it the Force stop presses; null when off. */
        @Volatile private var running: HomeAccessibilityService? = null

        /**
         * Runs Restart app's Force stop and relaunch for [pkg] inside the connected service. False
         * when no service is connected, so the caller can tell the member to do it by hand.
         */
        fun restart(pkg: String): Boolean {
            val service = running ?: return false
            service.runRestart(pkg)
            return true
        }

        fun wanted(context: Context, value: Boolean) =
            Prefs.of(context).edit().putBoolean(WANTED, value).apply()

        /** Set just before an update is committed and cleared if it fails; the reconnecting service then reopens KevBox Home. */
        fun reopenAfterUpdate(context: Context, pending: Boolean) {
            val edit = Prefs.of(context).edit()
            if (pending) edit.putLong(REOPEN, System.currentTimeMillis()) else edit.remove(REOPEN)
            edit.commit()
        }

        /**
         * Android removes an app's accessibility service from the enabled list whenever the app is
         * force-stopped, which silently hands HOME back to the stock launcher. With
         * WRITE_SECURE_SETTINGS (granted over ADB), put it back. Never enables it on its own:
         * only after the service has run once.
         */
        fun restore(context: Context) {
            if (!Prefs.of(context).getBoolean(WANTED, false)) return
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
            if (isEnabled(context)) return
            val me = ComponentName(context, HomeAccessibilityService::class.java).flattenToString()
            val resolver = context.contentResolver
            val services = enabledServices(context)
            try {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (services + me).distinct().joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (e: SecurityException) {
                // Permission revoked since the check: nothing to do.
            }
        }

        /** True while Android has the service turned on. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeAccessibilityService::class.java).flattenToString()
            return me in enabledServices(context) &&
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        }

        private fun enabledServices(context: Context) =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() }
    }
}
