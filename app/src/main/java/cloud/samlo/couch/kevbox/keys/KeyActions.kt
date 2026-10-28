package cloud.samlo.couch.kevbox.keys

import android.content.Context
import android.content.Intent
import android.util.Log
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.kevbox.KevBox
import java.io.File

/**
 * The three functions [KeyFilter] needs, backed by the accessibility service. [context] is the
 * service itself: Android lets an enabled accessibility service start activities from the
 * background, which is what lets a mapped key open an app from inside another one.
 */
class KeyActions(private val context: Context) {
    private val dir: File? by lazy { UserFiles(context).dir }
    private var cached: List<KeyEntry> = KeyMap.DEFAULT

    /** Never a real lastModified, so the first call always reads the file. */
    private var stamp = Long.MIN_VALUE

    /**
     * `Config.keys`, re-read only when config.json's lastModified changes. That one stat is the
     * only disk work on the key path; the JSON is parsed once per change, never once per press.
     * A missing file reads as 0 and [Config.load] falls back to [KeyMap.DEFAULT].
     */
    fun entries(): List<KeyEntry> {
        val modified = dir?.let { File(it, Config.FILE).lastModified() } ?: 0L
        if (modified != stamp) {
            cached = Config.load(dir).keys
            stamp = modified
        }
        return cached
    }

    fun installed(pkg: String): Boolean = launchIntent(context, pkg) != null

    fun act(entry: KeyEntry) {
        val codes = "keycode=${entry.keycode} scancode=${entry.scancode}"
        when {
            entry.pkg != null -> {
                val intent = launchIntent(context, entry.pkg)
                Log.i(KevBox.TAG, "key $codes -> ${entry.pkg}${if (intent == null) " (not installed)" else ""}")
                intent?.let { context.startActivity(it) }
            }
            // Through the HOME intent, never an explicit component: that would make a second Home.
            entry.action == KeyMap.HOME -> {
                Log.i(KevBox.TAG, "key $codes -> home")
                KevBox.goHome(context)
            }
            entry.action == KeyMap.DRAWER -> {
                Log.i(KevBox.TAG, "key $codes -> drawer")
                KevBox.goHome(context, openDrawer = true)
            }
        }
    }

    companion object {
        /**
         * [pkg]'s leanback launch intent, or failing that its plain one, with the flags AppSource
         * uses so an app already open comes back where it was; null when it has neither.
         */
        fun launchIntent(context: Context, pkg: String): Intent? {
            val pm = context.packageManager
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return null
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }
}
