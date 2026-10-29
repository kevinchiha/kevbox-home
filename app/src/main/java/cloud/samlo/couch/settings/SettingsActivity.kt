package cloud.samlo.couch.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.kevbox.update.Visibility
import cloud.samlo.couch.ui.Tokens

/**
 * Hosts one settings page (main, channels or permissions, chosen by an extra) in the shared
 * layout. Each change is saved at once; the home screen picks it up when it comes back.
 */
class SettingsActivity : Activity() {
    private lateinit var page: SettingsPage
    private lateinit var layout: SettingsLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = UserFiles(this)
        val config = Config.load(files.dir)
        Tokens.applyAccent(config.accent)
        val name = intent.getStringExtra(EXTRA_PAGE)
        // The main page and the pages inside its groups show the groups down the left.
        val group = Group.of(name)
        layout = SettingsLayout(this, withRail = name == null || group != null)
        val ctx = SettingsContext(this, files, layout, config)
        page = when (name) {
            PAGE_CHANNELS -> ChannelsPage(ctx)
            PAGE_ACCESS -> AccessPage(ctx)
            else -> MainPage(ctx)
        }
        setContentView(layout.root)
        page.build()
        group?.let { Rail(ctx, focusable = false).build(it) }
        layout.list.post { if (!page.focusStart()) layout.items().firstOrNull { it.isFocusable }?.requestFocus() }
    }

    override fun onDestroy() {
        if (this::page.isInitialized) page.onDestroy()
        super.onDestroy()
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
        if (::page.isInitialized) page.onResume()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Setup.onPermissionResult(this, requestCode, grantResults)
        if (::page.isInitialized) page.onResume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) Visibility.keyPressed()
        return page.consumesKey(event) || super.dispatchKeyEvent(event)
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        const val PAGE_CHANNELS = "channels"
        const val PAGE_ACCESS = "access"

        fun intent(context: Context, page: String) =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
