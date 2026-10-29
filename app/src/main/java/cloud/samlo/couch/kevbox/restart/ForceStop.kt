package cloud.samlo.couch.kevbox.restart

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import cloud.samlo.couch.R
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.keys.KeyActions

/**
 * One Restart app run, inside the accessibility service: on the details page AppRestart opened,
 * press the settings app's own Force stop and its OK, then relaunch [pkg].
 *
 * It polls the active window instead of listening for content changes, so the stock launcher's
 * redraws never wake the service. Text is matched exactly, in the settings app's own strings, so
 * it follows the TV's language and never mistakes the confirmation's title ("Force stop") or its
 * sentence for the button. A step that runs past [STEP_MS] leaves the screen as it is and tells
 * the member to finish by hand: any further press would be a guess.
 */
class ForceStop(private val service: AccessibilityService, private val pkg: String) {
    private enum class Step { FIND, CONFIRM, RELAUNCH }

    private val handler = Handler(Looper.getMainLooper())
    private var step = Step.FIND
    private var deadline = 0L
    private var strings: Strings? = null
    private var cancelled = false
    private val tick = Runnable { poll() }

    /** The settings app's own Force stop and OK texts; null where its resource name did not resolve. */
    private class Strings(val forceStop: String?, val ok: String?)

    fun start() {
        next(Step.FIND)
        handler.post(tick)
    }

    fun cancel() {
        cancelled = true
        handler.removeCallbacks(tick)
    }

    private fun next(to: Step) {
        step = to
        deadline = SystemClock.uptimeMillis() + STEP_MS
    }

    private fun poll() {
        if (cancelled) return
        val done = when (step) {
            Step.FIND -> find()
            Step.CONFIRM -> confirm()
            Step.RELAUNCH -> relaunch()
        }
        if (done) return
        if (SystemClock.uptimeMillis() >= deadline) return fail()
        handler.postDelayed(tick, POLL_MS)
    }

    /** True when the run is over; false to poll again. */
    private fun find(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val settings = root.packageName?.toString()?.takeIf { it in SETTINGS } ?: return false
        val texts = strings ?: load(settings).also { strings = it }
        val forceStop = texts.forceStop ?: return false
        val node = exact(root, forceStop)
        if (node == null) {
            // TV settings hides Force stop for a stopped app: a loaded page without it means stopped.
            return if (label()?.let { exact(root, it) } != null) launch() else false
        }
        val target = clickable(node)
        if (!node.isEnabled || target?.isEnabled == false) return launch()
        (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        next(Step.CONFIRM)
        return false
    }

    private fun confirm(): Boolean {
        val ok = strings?.ok ?: return false
        val node = service.rootInActiveWindow?.let { exact(it, ok) } ?: return false
        (clickable(node) ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        next(Step.RELAUNCH)
        return false
    }

    /** Waits for the confirmation to close, so the relaunch is not started under it. */
    private fun relaunch(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val ok = strings?.ok ?: return false
        return if (exact(root, ok) == null) launch() else false
    }

    private fun launch(): Boolean {
        val intent = KeyActions.launchIntent(service, pkg)
        if (intent == null) {
            Log.w(KevBox.TAG, "restart $pkg: no launch intent, not relaunched")
        } else {
            Log.i(KevBox.TAG, "restart $pkg: relaunched")
            service.startActivity(intent)
        }
        return true
    }

    private fun fail() {
        Log.w(KevBox.TAG, "restart $pkg: step $step timed out")
        Toast.makeText(service, R.string.kevbox_restart_by_hand, Toast.LENGTH_LONG).show()
    }

    /** Read from the settings app's Resources, never English fallbacks: a 0 id leaves the text null. */
    private fun load(settings: String): Strings {
        val res = try {
            service.packageManager.getResourcesForApplication(settings)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(KevBox.TAG, "restart $pkg: cannot read $settings's strings", e)
            return Strings(null, null)
        }
        return if (settings == TV_SETTINGS) {
            Strings(named(res, settings, "device_apps_app_management_force_stop"), text(res, android.R.string.ok))
        } else {
            Strings(named(res, settings, "force_stop"), named(res, settings, "dlg_ok"))
        }
    }

    private fun named(res: Resources, settings: String, name: String): String? =
        text(res, res.getIdentifier(name, "string", settings))

    private fun text(res: Resources, id: Int): String? = if (id == 0) null else try {
        res.getString(id).trim().takeIf { it.isNotEmpty() }
    } catch (e: Resources.NotFoundException) {
        null
    }

    /** The app's label: the details page's title, which shows the page has loaded. */
    private fun label(): String? = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().trim().takeIf { it.isNotEmpty() }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** A node whose trimmed text equals [text]. The platform search is a case-insensitive substring match, so it is only a first pass. */
    private fun exact(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByText(text).firstOrNull { it.text?.toString()?.trim() == text }

    /** [node] itself or its nearest clickable ancestor; null when none is clickable. */
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n
    }

    private companion object {
        const val TV_SETTINGS = "com.android.tv.settings"
        val SETTINGS = setOf(TV_SETTINGS, "com.android.settings")
        const val POLL_MS = 100L
        const val STEP_MS = 5_000L
    }
}
