package cloud.samlo.couch.kevbox.menu

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

/**
 * When the hold-OK menu may offer Uninstall, and the intent that asks Android to do it. The
 * removal always goes through Android's own confirmation screen, so one stray press on the
 * remote cannot delete an app.
 */
object Uninstall {
    /**
     * True only in the drawer, for a package outside [apps], when FLAG_SYSTEM is unset or
     * FLAG_UPDATED_SYSTEM_APP is set in [flags]. A system app with no updates cannot be removed,
     * and Home's tiles hold the household's chosen apps, so neither ever offers it.
     */
    fun allowed(flags: Int, pkg: String, place: AppMenu.Place, apps: List<String>): Boolean {
        if (place != AppMenu.Place.DRAWER || pkg in apps) return false
        val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updated = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return !system || updated
    }

    /** Android's own uninstall confirmation for [pkg]. ACTION_DELETE, since ACTION_UNINSTALL_PACKAGE is deprecated. */
    fun intent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
