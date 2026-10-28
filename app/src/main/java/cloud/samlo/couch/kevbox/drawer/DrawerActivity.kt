package cloud.samlo.couch.kevbox.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.kevbox.menu.AppMenu
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.Tokens

/**
 * The drawer: every TV app that is not in Home's column, in its own task above Home's. It
 * keeps the lifecycle, the package receiver and the key routing; [DrawerGrid] draws it and
 * [DrawerApps] loads it. Only Home starts it, so finishing it always lands back on Home.
 */
class DrawerActivity : Activity() {
    private lateinit var grid: DrawerGrid
    private lateinit var apps: DrawerApps
    /** The config of the last load, for the hold-OK menu's view of the column. */
    private var config = Config()
    /** The key pressed during Move: only its release ends Move, never a release left over from the menu. */
    private var pressed = 0

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // An uninstalled app must leave the grid at once (AC-37): a held app is put down first.
            grid.endMove()?.let(apps::save)
            apps.load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        grid = DrawerGrid(this, Images(this, Fetcher(this))) { card -> menu(card) }
        setContentView(grid.root)
        apps = DrawerApps(this, UserFiles(this)) { loaded, tiles ->
            config = loaded
            // Before any card exists, so a drawer opened with no Home process still shows the member's accent.
            Tokens.applyAccent(loaded.accent)
            grid.show(tiles)
        }
        apps.load()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packages, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        apps.shutdown()
        super.onDestroy()
    }

    private fun menu(card: Card) {
        val pkg = card.tile.key
        AppMenu.show(this, pkg, AppMenu.Place.DRAWER, config.apps) { grid.startMove(pkg) }
    }

    /** Keys reach Move before focus search, the way MainActivity routes them to Home. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = consumesKey(event) || super.dispatchKeyEvent(event)

    // The manifest opts the drawer out of predictive back, so reading Back here is the only hook.
    @SuppressLint("GestureBackNavigation")
    private fun consumesKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        if (!grid.isMoving) {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
            // The flag tells Home to focus More apps; no other way out of the drawer sets it.
            if (up && !event.isCanceled) {
                closedByBack = true
                finish()
            }
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down) pressed = event.keyCode
                if (up && pressed == event.keyCode) {
                    pressed = 0
                    grid.endMove()?.let(apps::save)
                }
            }
            // Repeats included, so holding an arrow keeps the app travelling. Up and Down never leave the grid.
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) grid.step(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) grid.step(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> if (down) grid.step(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) grid.step(0, 1)
            else -> return false
        }
        return true
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
