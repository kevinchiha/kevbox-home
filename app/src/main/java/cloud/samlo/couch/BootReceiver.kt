package cloud.samlo.couch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores HOME capture at boot, before anyone presses HOME. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        HomeAccessibilityService.restore(context)
    }
}
