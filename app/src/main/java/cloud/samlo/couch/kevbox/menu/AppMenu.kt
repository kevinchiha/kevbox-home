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
import cloud.samlo.couch.kevbox.home.AppLists
import cloud.samlo.couch.kevbox.restart.AppRestart

/**
 * The menu a long OK press opens on an app, on Home's tiles or in the drawer: Add to Home (drawer
 * only), App info, Move, Restart app, Remove from Home (tiles only, never for KevBox Live, KevBox
 * TV or SmartTube) and, for removable drawer apps, Uninstall. Nothing else, by design. Program cards keep Couch's own CardMenus; this copies its dialog pattern rather than
 * calling into it.
 */
object AppMenu {
    /** Where the menu opened: [COLUMN] is Home's app tiles (named for the left column they replaced). */
    enum class Place { COLUMN, DRAWER }

    /**
     * Opens the menu for [pkg]. A package that is already gone opens nothing: the tiles or the
     * drawer is about to drop it. The chosen action runs once the dialog has closed, so Move
     * starts with the dialog out of the way; Back closes it with no action and focus stays put.
     * [onAddToHome] is the drawer's Add to Home, offered first since it is what a member most
     * often wants there; Home's tiles pass none. [onRemoveFromHome] is the tiles' Remove from Home,
     * offered last, and only for an app outside `AppLists.LOCKED`; the drawer passes none.
     */
    fun show(
        activity: Activity,
        pkg: String,
        place: Place,
        apps: List<String>,
        onAddToHome: (() -> Unit)? = null,
        onRemoveFromHome: (() -> Unit)? = null,
        onMove: () -> Unit,
    ) {
        val info = try {
            activity.packageManager.getApplicationInfo(pkg, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        val actions = listOfNotNull(
            onAddToHome?.takeIf { place == Place.DRAWER && pkg !in apps }?.let { activity.getString(R.string.kevbox_menu_add_home) to it },
            activity.getString(R.string.kevbox_menu_info) to { appInfo(activity, pkg) },
            activity.getString(R.string.kevbox_menu_move) to onMove,
            activity.getString(R.string.kevbox_menu_restart) to { AppRestart.start(activity, pkg) },
            onRemoveFromHome?.takeIf { place == Place.COLUMN && AppLists.removable(pkg) }
                ?.let { activity.getString(R.string.kevbox_menu_remove_home) to it },
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
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
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
