package cloud.samlo.couch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import cloud.samlo.couch.home.HomeScreen
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.drawer.DrawerActivity

/**
 * The launcher entry point. Named in the TV's settings, so it keeps its name and package; the
 * home screen itself lives in [HomeScreen].
 */
class MainActivity : Activity() {
    private lateinit var home: HomeScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = HomeScreen(this)
        home.onCreate()
        // A drawer key with no Home running yet still lands on the drawer.
        openDrawerIfAsked(intent)
    }

    override fun onResume() {
        super.onResume()
        home.onResume()
    }

    override fun onPause() {
        home.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        home.onDestroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.onHomePressed()
        openDrawerIfAsked(intent)
    }

    /**
     * Home starts the drawer itself, while in front, so the drawer's task sits straight above
     * Home's and Back lands here. See [KevBox.goHome].
     */
    private fun openDrawerIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(KevBox.EXTRA_OPEN_DRAWER, false) == true) {
            startActivity(DrawerActivity.intent(this))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        home.load()
    }

    @Deprecated("Settings only reports back one action.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        home.onSettingsResult(resultCode)
    }

    // Opted out of predictive back in the manifest, so this legacy hook still swallows Back.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Home has nowhere to go back to.")
    override fun onBackPressed() = Unit

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = home.consumesKey(event) || super.dispatchKeyEvent(event)
}
