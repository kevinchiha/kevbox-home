package cloud.samlo.couch.kevbox

import android.content.Context
import android.content.Intent

/** Names every KevBox piece shares: the log tag, and the way back to Home. */
object KevBox {
    const val TAG = "KevBoxHome"

    /** Boolean intent extra: Home, once in front, opens the drawer over itself. */
    const val EXTRA_OPEN_DRAWER = "cloud.samlo.couch.kevbox.OPEN_DRAWER"

    /**
     * Brings the running Home to the front the way Android's own Home key does: ACTION_MAIN +
     * CATEGORY_HOME, setPackage(context.packageName), FLAG_ACTIVITY_NEW_TASK, plus
     * EXTRA_OPEN_DRAWER = true when [openDrawer]. The singleTask MainActivity is reused and
     * onNewIntent runs; no second Home is created.
     *
     * Never an explicit MainActivity component: once KevBox Home is the real Home, an explicit
     * start is a standard launch that Android keeps out of the home task, so it would build a
     * second Home instead of reaching onNewIntent.
     */
    fun goHome(context: Context, openDrawer: Boolean = false) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (openDrawer) intent.putExtra(EXTRA_OPEN_DRAWER, true)
        context.startActivity(intent)
    }
}
