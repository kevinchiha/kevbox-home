package cloud.samlo.couch.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Listens for installs, removals and updates, and calls [onChanged] so the app list follows.
 * KevBox shows no HDMI inputs, so it no longer watches TV inputs turning on and off.
 */
class SystemWatchers(
    private val activity: Activity,
    private val onChanged: () -> Unit,
) {
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChanged()
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(packageReceiver, filter)
        }
    }

    fun unregister() {
        activity.unregisterReceiver(packageReceiver)
    }
}
