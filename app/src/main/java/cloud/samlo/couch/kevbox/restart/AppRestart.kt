package cloud.samlo.couch.kevbox.restart

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Stub: task 10 fills this in. */
object AppRestart {
    fun start(context: Context, pkg: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
