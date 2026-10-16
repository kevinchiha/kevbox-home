package cloud.samlo.couch.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import cloud.samlo.couch.data.Sections
import cloud.samlo.couch.ui.Rows

/**
 * Listens for installs, removals and TV input changes. Anything that changes what the rows
 * contain calls [onChanged]; an input merely (dis)connecting is a cheap in-place dim instead.
 */
class SystemWatchers(
    private val activity: Activity,
    private val rows: Rows,
    private val onChanged: () -> Unit,
) {
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChanged()
    }

    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            // Cheap in-place update: no rebuild, no layout pass.
            rows.section(Sections.INPUTS)?.row?.cards()?.firstOrNull { it.tile.inputId == inputId }
                ?.setDimmed(state == TvInputManager.INPUT_STATE_DISCONNECTED)
        }

        override fun onInputAdded(inputId: String) = onChanged()
        override fun onInputRemoved(inputId: String) = onChanged()
        override fun onInputUpdated(inputId: String) = onChanged()
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
        activity.getSystemService(TvInputManager::class.java)?.registerCallback(inputCallback, Handler(Looper.getMainLooper()))
    }

    fun unregister() {
        activity.unregisterReceiver(packageReceiver)
        activity.getSystemService(TvInputManager::class.java)?.unregisterCallback(inputCallback)
    }
}
