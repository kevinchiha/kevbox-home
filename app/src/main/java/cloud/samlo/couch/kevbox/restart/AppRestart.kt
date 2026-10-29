package cloud.samlo.couch.kevbox.restart

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import cloud.samlo.couch.HomeAccessibilityService
import cloud.samlo.couch.R
import cloud.samlo.couch.kevbox.KevBox

/**
 * Restart app: opens [pkg]'s details page, then hands the presses to the accessibility service
 * ([ForceStop]). Without the service the page stays open and a toast says what to press, since
 * Android gives a normal app no way to force-stop another.
 */
object AppRestart {
    fun start(context: Context, pkg: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // Some TVs ship without the details page: say what to do by hand instead of crashing Home.
            Log.w(KevBox.TAG, "No app details page for $pkg: ${e.message}")
            byHand(context)
            return
        }
        if (!HomeAccessibilityService.restart(pkg)) byHand(context)
    }

    private fun byHand(context: Context) {
        Toast.makeText(context, R.string.kevbox_restart_by_hand, Toast.LENGTH_LONG).show()
    }
}
