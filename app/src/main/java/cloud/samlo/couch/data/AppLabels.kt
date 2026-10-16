package cloud.samlo.couch.data

import android.content.Context
import android.content.pm.PackageManager

/** Display names for installed packages; a label never changes while the process lives. */
object AppLabels {
    private val labels = HashMap<String, String?>()

    fun of(context: Context, pkg: String): String? = labels.getOrPut(pkg) {
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
