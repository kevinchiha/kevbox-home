package cloud.samlo.couch.kevbox.restart

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import cloud.samlo.couch.HomeAccessibilityService
import cloud.samlo.couch.R

/**
 * Restart app: opens [pkg]'s details page, then hands the presses to the accessibility service
 * ([ForceStop]). Without the service the page stays open and a toast says what to press, since
 * Android gives a normal app no way to force-stop another.
 */
object AppRestart {
    fun start(context: Context, pkg: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        if (!HomeAccessibilityService.restart(pkg)) {
            Toast.makeText(context, R.string.kevbox_restart_by_hand, Toast.LENGTH_LONG).show()
        }
    }
}
