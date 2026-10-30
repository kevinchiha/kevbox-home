package cloud.samlo.couch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import cloud.samlo.couch.home.HomeScreen
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.kevbox.drawer.DrawerActivity
import cloud.samlo.couch.kevbox.update.SilentJob
import cloud.samlo.couch.kevbox.update.Visibility

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
        // A drawer key with no Home running yet still lands on the drawer. Only on a fresh start:
        // a rebuild after process death replays the same launch intent, and no key was pressed.
        if (savedInstanceState == null) openDrawerIfAsked(intent)
        SilentJob.schedule(this)
    }

    override fun onStart() {
        super.onStart()
        Visibility.started(this)
    }

    override fun onStop() {
        Visibility.stopped(this)
        super.onStop()
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
        // The drawer key opens the drawer over Home instead: Back from it lands on More apps.
        if (!openDrawerIfAsked(intent)) home.onHomePressed()
    }

    /**
     * Home starts the drawer itself, while in front, so the drawer's task sits straight above
     * Home's and Back lands here. See [KevBox.goHome]. True when it opened the drawer.
     */
    private fun openDrawerIfAsked(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(KevBox.EXTRA_OPEN_DRAWER, false) != true) return false
        startActivity(DrawerActivity.intent(this))
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        home.load()
    }

    // Opted out of predictive back in the manifest, so this legacy hook still swallows Back.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Home has nowhere to go back to.")
    override fun onBackPressed() = Unit

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) Visibility.keyPressed()
        return home.consumesKey(event) || super.dispatchKeyEvent(event)
    }
}
