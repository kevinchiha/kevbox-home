package cloud.samlo.couch.home

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import cloud.samlo.couch.HomeAccessibilityService
import cloud.samlo.couch.MediaListener
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Prefs
import cloud.samlo.couch.data.Programs
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.data.Wallpapers
import cloud.samlo.couch.data.Weather
import cloud.samlo.couch.data.WeatherNow
import cloud.samlo.couch.kevbox.menu.AppMenu
import cloud.samlo.couch.kevbox.update.SilentJob
import cloud.samlo.couch.settings.SettingsActivity
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.update.Installer
import cloud.samlo.couch.update.Release
import cloud.samlo.couch.update.UpdateCheck

/**
 * The home screen's wiring and lifecycle: it owns the pieces (loader, menus) and forwards the
 * activity's callbacks to them. Create it in `onCreate`.
 */
class HomeScreen(private val activity: Activity) {
    private val files = UserFiles(activity)
    private val prefs = Prefs.of(activity)
    private val fetcher = Fetcher(activity)
    private val images = Images(activity, fetcher)
    private val views: HomeViews = HomeViews(
        activity, images, { openSettings() }, { installUpdate() },
        // An app tile gets KevBox's menu; program cards keep Couch's.
        onAppMenu = { tile ->
            AppMenu.show(activity, tile.key, AppMenu.Place.COLUMN, config.apps) { views.index.startMove(tile.key) }
        },
        onProgramMenu = { menus.show(it) },
    )
    private val installer = Installer(activity, fetcher)
    private var update: Release? = null
    private val main = Handler(Looper.getMainLooper())

    private var config = Config.load(null)
    private var resumed = false

    private val menus: CardMenus = CardMenus(activity, { config }, files) { load() }
    private val loader = HomeLoader(
        activity, files, Wallpapers(fetcher, prefs), Weather(fetcher, prefs), UpdateCheck(activity, fetcher), ::onLoaded,
    )
    private val watchers = SystemWatchers(activity) { load() }
    private val reloadForRecommendations = Runnable { if (resumed) load() }

    fun onCreate() {
        activity.setContentView(views.root)
        watchers.register()
        if (!Programs.canRead(activity)) activity.requestPermissions(arrayOf(Programs.READ_TV_LISTINGS), 1)
    }

    fun onResume() {
        resumed = true
        MediaListener.onRecommendationsChanged = {
            // Apps often post a batch at once: one reload for all of them.
            main.removeCallbacks(reloadForRecommendations)
            main.postDelayed(reloadForRecommendations, RECOMMENDATIONS_SETTLE_MS)
        }
        HomeAccessibilityService.restore(activity)
        // Watch progress moves while you're in an app; unchanged rows are not rebuilt.
        load()
        startLive()
    }

    fun onPause() {
        resumed = false
        MediaListener.onRecommendationsChanged = null
        main.removeCallbacks(reloadForRecommendations)
        stopLive()
    }

    fun onDestroy() {
        watchers.unregister()
        loader.shutdown()
    }

    /** HOME while already home goes back to the start, like the stock launcher. */
    fun onHomePressed() {
        if (resumed) views.index.reset()
    }

    fun load() = loader.load()

    /** True when the key was consumed (the Index screen's own keys). */
    fun consumesKey(event: KeyEvent): Boolean = views.index.consumesKey(event)

    private fun onLoaded(data: HomeData) {
        val accentChanged = config.accent != data.config.accent
        config = data.config
        if (accentChanged) Tokens.applyAccent(config.accent)
        views.index.topBar.applyLook(config)
        views.index.showBackdrop(data.wallpaper, config.dim)
        showWeather(data.weather)
        // On Android 12+ the chip waits while the silent update can still install this version (AC-10).
        update = data.update?.takeUnless { SilentJob.holdsPrompt(activity, it) }
        // A reload mid-download keeps the "Downloading" label.
        if (!Installer.running) {
            views.index.topBar.update.show(update?.version)
        }
        views.index.bind(data.index, config)
    }

    private fun startLive() {
        views.index.topBar.nowPlaying.start()
        views.index.systemLine.start()
        views.index.topBar.animateWeather(true)
    }

    private fun stopLive() {
        views.index.topBar.nowPlaying.stop()
        views.index.systemLine.stop()
        views.index.topBar.animateWeather(false)
    }

    private fun showWeather(now: WeatherNow?) {
        views.index.topBar.showWeather(now)
    }

    private fun installUpdate() {
        val release = update ?: return
        if (Installer.running) return
        views.index.topBar.update.downloading()
        installer.install(release) { message ->
            if (message != null) Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            views.index.topBar.update.show(release.version)
        }
    }

    private fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    private companion object {
        const val RECOMMENDATIONS_SETTLE_MS = 2_000L
    }
}
