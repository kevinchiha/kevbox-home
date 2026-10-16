package cloud.samlo.couch.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.data.Wallpapers
import cloud.samlo.couch.data.Weather
import cloud.samlo.couch.data.WeatherNow
import java.util.concurrent.Executors

/** Everything one reload produced, ready to apply on the main thread. */
class HomeData(
    val config: Config,
    val wallpaper: String?,
    val pictures: List<String>,
    val weather: WeatherNow?,
    val sections: List<SectionData>,
)

/**
 * Builds [HomeData] on a single background thread and hands it to [onLoaded] on the main one.
 * A reload requested while one is running is queued and runs once, right after it.
 */
class HomeLoader(
    private val context: Context,
    private val files: UserFiles,
    private val wallpapers: Wallpapers,
    private val weather: Weather,
    private val onLoaded: (HomeData) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var loading = false
    private var reloadQueued = false

    fun load() {
        if (loading) {
            reloadQueued = true
            return
        }
        loading = true
        executor.execute {
            val data = build()
            main.post {
                onLoaded(data)
                loading = false
                if (reloadQueued) {
                    reloadQueued = false
                    load()
                }
            }
        }
    }

    fun shutdown() = executor.shutdown()

    private fun build(): HomeData {
        val config = Config.load(files.dir)
        val sections = SectionBuilder.build(context, config, files.icons)
        val wallpaper = wallpapers.current(config, files.dir)
        val pictures = if (config.ambientMinutes > 0) wallpapers.all(config, files.dir) else emptyList()
        return HomeData(config, wallpaper, pictures, weather.now(config), sections)
    }
}
