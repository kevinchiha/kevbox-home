package cloud.samlo.couch

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import cloud.samlo.couch.data.Prefs

/**
 * Some TVs hardwire HOME to the stock launcher. This service only receives window changes from
 * that launcher (see `res/xml/home_accessibility.xml`) and replaces it with Couch. It never
 * filters key events, so holding volume keeps ramping.
 */
class HomeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            ),
        )
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Remember that HOME capture is wanted, so it can be restored after Android drops it.
        wanted(this, true)
    }

    companion object {
        private const val WANTED = "homeCaptureWanted"
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

        fun wanted(context: Context, value: Boolean) =
            Prefs.of(context).edit().putBoolean(WANTED, value).apply()

        /**
         * Android removes an app's accessibility service from the enabled list whenever the app is
         * force-stopped, which silently hands HOME back to the stock launcher. With
         * WRITE_SECURE_SETTINGS (granted over ADB), put it back. Never enables it on its own:
         * only after the service has run once.
         */
        fun restore(context: Context) {
            if (!Prefs.of(context).getBoolean(WANTED, false)) return
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
            val me = ComponentName(context, HomeAccessibilityService::class.java).flattenToString()
            val resolver = context.contentResolver
            val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val services = enabled.split(':').filter { it.isNotBlank() }
            if (me in services && Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return
            try {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (services + me).distinct().joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (e: SecurityException) {
                // Permission revoked since the check: nothing to do.
            }
        }
    }
}
