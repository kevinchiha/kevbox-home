package cloud.samlo.couch.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri

/**
 * Reads the preview channels apps publish to the system TV provider. Other apps' rows are only
 * visible with READ_TV_LISTINGS, and the provider rejects selections from third-party callers, so
 * all filtering happens here.
 */
object Programs {
    const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    private const val MAX_ROW = 15
    private const val MAX_CHANNELS = 12

    class Channel(val id: Long, val packageName: String, val name: String, val tiles: List<Tile>)

    /** A preview channel as listed in settings, whether or not it is shown. */
    class ChannelInfo(val id: Long, val packageName: String, val name: String, val browsable: Boolean) {
        fun shown(config: Config) = config.channels[id.toString()] ?: browsable
    }

    fun canRead(context: Context) =
        context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    /** Every preview channel any app has published, for the settings list. */
    fun allChannels(context: Context, config: Config): List<ChannelInfo> {
        if (!canRead(context)) return emptyList()
        val order = { pkg: String -> config.pinnedApps.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it } }
        return query(context, TvContract.Channels.CONTENT_URI) { c ->
            if (c.string("type") != TvContract.Channels.TYPE_PREVIEW) return@query null
            val pkg = c.string("package_name") ?: return@query null
            if (pkg in config.hiddenApps) return@query null
            // Browsable = the user kept this channel on the stock home screen; that is the default.
            ChannelInfo(c.long("_id"), pkg, c.string("display_name").orEmpty(), c.int("browsable", 0) != 0)
        }.filterNotNull().sortedWith(compareBy({ order(it.packageName) }, { it.packageName }, { it.name }))
    }

    /** The channels to show as rows, each with its best programs first. */
    fun channels(context: Context, config: Config): List<Channel> =
        allChannels(context, config)
            .asSequence()
            .filter { it.shown(config) }
            .map { info -> Channel(info.id, info.packageName, info.name, programs(context, info)) }
            .filter { it.tiles.isNotEmpty() }
            .take(MAX_CHANNELS)
            .toList()

    private fun programs(context: Context, info: ChannelInfo): List<Tile> =
        query(context, TvContract.buildPreviewProgramsUriForChannel(info.id)) { c ->
            if (c.int("browsable", 1) == 0) return@query null
            c.int("weight", 0) to ProgramTile.from(context, c, info.packageName)
        }.filterNotNull().sortedByDescending { it.first }.mapNotNull { it.second }.take(MAX_ROW)

    /** "App  ·  Channel", or just the channel when its name already says which app it is. */
    fun title(context: Context, packageName: String, channelName: String, separator: String): String {
        val app = AppLabels.of(context, packageName)
        return if (app == null || channelName.contains(app, ignoreCase = true)) channelName else "$app$separator$channelName"
    }

    private fun <T> query(context: Context, uri: Uri, map: (Cursor) -> T?): List<T?> = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val out = ArrayList<T?>(c.count)
            while (c.moveToNext()) out.add(map(c))
            out
        } ?: emptyList()
    } catch (e: Exception) {
        // No TV provider (some sticks) or a provider that refuses us: just no rows.
        emptyList()
    }
}
