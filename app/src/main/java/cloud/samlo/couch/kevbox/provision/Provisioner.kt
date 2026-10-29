package cloud.samlo.couch.kevbox.provision

import android.util.Log
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.config.KevBoxConfig
import org.json.JSONObject
import java.io.File

/**
 * Applies the file `provision.sh` pushes into the app's external files folder. It runs on every
 * Home load, so the common case (no file) stays silent and cheap. A handled file is renamed:
 * left in place, it would undo every change a member makes from Customize on each load.
 */
object Provisioner {
    const val FILE = "kevbox-home.json"
    const val APPLIED = "kevbox-home.applied.json"
    const val REJECTED = "kevbox-home.rejected.json"

    enum class Outcome { NONE, APPLIED, REJECTED, FAILED }

    /** Applies [FILE] in [dir] to config.json there, then renames it; [log] gets one line per outcome. */
    fun apply(dir: File, log: (String) -> Unit): Outcome {
        // provision.sh pushes to kevbox-home.json.tmp and moves it, so only the final name counts.
        val source = File(dir, FILE)
        if (!source.isFile) return Outcome.NONE
        var text: String? = null
        val json = try {
            text = source.readText()
            JSONObject(text)
        } catch (e: Exception) {
            moveTo(source, File(dir, REJECTED), text.orEmpty())
            log("rejected $FILE: ${reason(e)}")
            return Outcome.REJECTED
        }
        val applied = try {
            val config = Config.load(dir)
            val names = KevBoxConfig.decode(json, config)
            config.save(dir)
            // Config.save ignores a refused rename, so only a reload shows whether the save landed.
            val saved = Config.load(dir)
            val lost = names.filter { field(saved, it) != field(config, it) }.sorted()
            check(lost.isEmpty()) { "config.json kept the old ${lost.joinToString(",")}" }
            names
        } catch (e: Exception) {
            log("save failed $FILE: ${reason(e)}")
            return Outcome.FAILED
        }
        // Saved first: a crash or a refused rename only applies the same file again next load.
        try {
            moveTo(source, File(dir, APPLIED), text.orEmpty())
        } catch (e: Exception) {
            log("rename failed $FILE: ${reason(e)}")
            return Outcome.FAILED
        }
        log("applied $FILE: ${applied.sorted().joinToString(",")}")
        return Outcome.APPLIED
    }

    /** [apply] with android.util.Log under KevBox.TAG; NONE when [dir] is null. */
    fun applyIfPresent(dir: File?): Outcome {
        dir ?: return Outcome.NONE
        return try {
            apply(dir) { Log.i(KevBox.TAG, it) }
        } catch (e: Exception) {
            // Home must load even when the provision folder misbehaves.
            Log.w(KevBox.TAG, "save failed $FILE: ${reason(e)}")
            Outcome.FAILED
        }
    }

    /**
     * Replaces [target] with [source]. FUSE storage on some TVs refuses renameTo, so the fallback
     * writes [text] to [target] and deletes [source].
     */
    private fun moveTo(source: File, target: File, text: String) {
        target.delete()
        if (source.renameTo(target)) return
        target.writeText(text)
        source.delete()
    }

    /** The value [KevBoxConfig.decode] wrote under [name], for comparing two configs. */
    private fun field(c: Config, name: String): Any? = when (name) {
        "apps" -> c.apps
        "drawer" -> c.drawer
        "hidden" -> c.hidden
        "buttons" -> c.buttons
        "speedtestApp" -> c.speedtestApp
        "keys" -> c.keys
        "wallpaper" -> c.wallpaper
        else -> null
    }

    private fun reason(e: Exception): String = e.message ?: e.javaClass.simpleName
}
