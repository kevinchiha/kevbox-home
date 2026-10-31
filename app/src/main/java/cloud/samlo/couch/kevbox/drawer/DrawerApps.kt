package cloud.samlo.couch.kevbox.drawer

import android.app.Activity
import android.os.Handler
import android.os.Looper
import cloud.samlo.couch.data.AppSource
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.kevbox.home.AppLists
import cloud.samlo.couch.ui.dp
import java.util.concurrent.Executors

/**
 * Loads the drawer's tiles and saves its order, both on one background thread: the package
 * manager queries and banner renders behind [AppSource.tiles] are too slow for the main thread,
 * and one thread keeps a save and the reload after it in order.
 */
class DrawerApps(
    private val activity: Activity,
    private val files: UserFiles,
    private val onLoaded: (Config, List<Tile>) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Reads the config and every launchable app, then hands both to [onLoaded] unless the drawer is gone. */
    fun load() = executor.execute {
        val config = Config.load(files.dir)
        val tiles = AppSource.tiles(
            activity,
            config,
            files.icons,
            activity.dp(DrawerGrid.CARD_W),
            activity.dp(DrawerGrid.CARD_H),
        )
        val byPackage = tiles.associateBy { it.key }
        // AppSource already put a name from config.names on the label, so the order sorts by it.
        val labels = tiles.associate { it.key to it.label }
        val order = AppLists.drawer(labels, config.apps, config.drawer, config.hidden, activity.packageName)
        val shown = order.mapNotNull { byPackage[it] }
        main.post { if (!activity.isDestroyed) onLoaded(config, shown) }
    }

    /** Saves [order], the whole grid as shown, as the drawer order, into a freshly read config. */
    fun save(order: List<String>) = executor.execute {
        val config = Config.load(files.dir)
        config.drawer = order
        config.save(files.dir)
    }

    /**
     * Puts [pkg] at the end of Home's column in a freshly read config, then reloads the grid,
     * which drops it, since the column and the drawer never share an app. [onAdded] runs on the
     * main thread once the save is done.
     */
    fun addToHome(pkg: String, onAdded: () -> Unit) {
        executor.execute {
            val config = Config.load(files.dir)
            config.apps = AppLists.addToColumn(config.apps, pkg)
            config.save(files.dir)
            main.post { if (!activity.isDestroyed) onAdded() }
        }
        load()
    }

    fun shutdown() = executor.shutdown()
}
