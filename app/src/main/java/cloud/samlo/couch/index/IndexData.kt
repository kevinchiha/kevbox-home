package cloud.samlo.couch.index

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import cloud.samlo.couch.data.AppSource
import cloud.samlo.couch.data.Banners
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Programs
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.kevbox.home.AppLists
import cloud.samlo.couch.kevbox.home.HomeRows
import cloud.samlo.couch.kevbox.home.Shelf
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf
import java.io.File

/**
 * One app along the bottom: its launch [app] tile (its banner, rendered at tile size), a rounded
 * [icon] for an app with no banner, and the rows of cards Home shows for it
 * (`kevbox.home.HomeRows` picks them from its channels).
 */
class IndexEntry(
    val app: Tile,
    val icon: Bitmap?,
    val shelves: List<Shelf>,
) {
    val key: String get() = app.key
    /** The first row's cards, or none. */
    val programs: List<Tile> get() = shelves.firstOrNull()?.tiles.orEmpty()
    val signature: String = "${app.signature}|${icon?.generationId}|" +
        shelves.joinToString("||") { shelf -> shelf.title + "|" + shelf.tiles.joinToString(";") { it.signature } }
}

class IndexData(val entries: List<IndexEntry>)

/** Builds [IndexData] on the loader thread. */
object IndexBuilder {
    fun build(context: Context, config: Config, iconsDir: File?): IndexData {
        val apps = AppSource.tiles(
            context, config, iconsDir,
            context.dp(IndexTokens.TILE_W), context.dp(IndexTokens.TILE_H), context.dpf(IndexTokens.TILE_RADIUS),
        )
        val channels = Programs.channels(context, config).groupBy { it.packageName }
        // The tiles are config.apps in its saved order.
        val byPackage = apps.associateBy { it.key }
        val ordered = AppLists.column(config.apps, config.hidden, byPackage.keys).map { byPackage.getValue(it) }
        val entries = ordered.map { tile ->
            val picked = HomeRows.pick(tile.key, channels[tile.key].orEmpty())
            IndexEntry(
                app = tile,
                // The banner is the tile; the icon only stands in for an app without one.
                icon = if (tile.banner == null) icon(context, tile.key) else null,
                shelves = picked.map { Shelf(it.name, it.tiles) },
            )
        }
        return IndexData(entries)
    }

    /** The app's launcher icon as a rounded square at the tile's icon size, rendered once per app version. */
    private fun icon(context: Context, pkg: String): Bitmap? {
        val pm = context.packageManager
        val updated = try {
            pm.getPackageInfo(pkg, 0).lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val size = context.dp(IndexTokens.TILE_ICON)
        return Banners.memoized("icon|$pkg|$size|$updated") {
            try {
                Banners.render(pm.getApplicationIcon(pkg), size, size, context.dpf(IndexTokens.TILE_ICON_RADIUS))
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
