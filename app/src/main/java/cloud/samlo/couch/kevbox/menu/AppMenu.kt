package cloud.samlo.couch.kevbox.menu

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import cloud.samlo.couch.R
import cloud.samlo.couch.data.AppLabels
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.restart.AppRestart

/**
 * The menu a long OK press opens on an app, in the left column or the drawer: App info, Move,
 * Restart app and, for removable drawer apps, Uninstall. Nothing else, by design. Program cards
 * keep Couch's own CardMenus; this copies its dialog pattern rather than calling into it.
 */
object AppMenu {
    enum class Place { COLUMN, DRAWER }

    /**
     * Opens the menu for [pkg]. A package that is already gone opens nothing: the column or the
     * drawer is about to drop it. The chosen action runs once the dialog has closed, so Move
     * starts with the dialog out of the way; Back closes it with no action and focus stays put.
     */
    fun show(activity: Activity, pkg: String, place: Place, apps: List<String>, onMove: () -> Unit) {
        val info = try {
            activity.packageManager.getApplicationInfo(pkg, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        val actions = listOfNotNull(
            activity.getString(R.string.kevbox_menu_info) to { appInfo(activity, pkg) },
            activity.getString(R.string.kevbox_menu_move) to onMove,
            activity.getString(R.string.kevbox_menu_restart) to { AppRestart.start(activity, pkg) },
            if (Uninstall.allowed(info.flags, pkg, place, apps)) {
                activity.getString(R.string.kevbox_menu_uninstall) to { uninstall(activity, pkg) }
            } else {
                null
            },
        )
        var chosen: (() -> Unit)? = null
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(AppLabels.of(activity, pkg) ?: pkg)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> chosen = actions[which].second }
            .setOnDismissListener { chosen?.invoke() }
            .show()
    }

    /** A TV without the details page logs and stays put rather than crashing Home or the drawer. */
    private fun appInfo(activity: Activity, pkg: String) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(KevBox.TAG, "No app details page for $pkg: ${e.message}")
        }
    }

    /** Hands off to Android's confirmation screen; nothing is removed unless the member confirms there. */
    private fun uninstall(activity: Activity, pkg: String) {
        try {
            activity.startActivity(Uninstall.intent(pkg))
        } catch (e: ActivityNotFoundException) {
            Log.w(KevBox.TAG, "No uninstall screen for $pkg: ${e.message}")
        } catch (e: SecurityException) {
            Log.w(KevBox.TAG, "Uninstall refused for $pkg: ${e.message}")
        }
    }
}
