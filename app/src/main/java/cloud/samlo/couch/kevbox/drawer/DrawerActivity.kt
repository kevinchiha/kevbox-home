package cloud.samlo.couch.kevbox.drawer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Stub: task 8 fills this in. */
class DrawerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }

    companion object {
        /** Main thread only: task 8 sets it, Home reads it, so no locking. */
        private var closedByBack = false

        fun intent(context: Context): Intent =
            Intent(context, DrawerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** True once after Back closed the drawer; reading it clears it. Home reads it to put focus on More apps. */
        fun takeClosedByBack(): Boolean {
            val was = closedByBack
            closedByBack = false
            return was
        }
    }
}
