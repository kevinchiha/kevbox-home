package cloud.samlo.couch.index

import android.graphics.Color

/**
 * The Index layout's measurements, in dp on Couch's 960x540dp canvas, and the few colours it
 * adds to [cloud.samlo.couch.ui.Tokens]. Everything is placed at fixed positions, so focus moves
 * never trigger a layout pass.
 */
object IndexTokens {
    /** The canvas height: a column that fits sits centred on it. */
    const val SCREEN_H = 540

    /** The widest the top bar's weather place text gets, so a long place name never crowds the buttons. */
    const val PLACE_MAX_W = 120

    // The app list, left, level with the right column's header. The top bar above it starts at
    // IndexScreen.TOP_BAR_Y (23dp) and is 52dp tall while now playing shows (40dp art plus 6dp
    // padding above and below), so its bottom then sits at 75dp, 11dp below the list frame's top.
    // A short centred column starts lower than that; a full or gliding one starts at the top.
    const val LIST_X = 24
    const val LIST_Y = 64
    const val LIST_W = 330
    const val LIST_H = 423
    /** Where the list starts fading out, from its top. */
    const val LIST_FADE_Y = 340
    const val ROW_H = 46
    const val ROW_PITCH = 49
    /** Rows kept above the selected one before the list glides; the last rows never pass the fade. */
    const val ROWS_ABOVE = 3
    const val ROWS_SHOWN = 7
    const val ICON = 29

    // KevBox's app tiles along the bottom: the launcher icon on a rounded plate. The band runs from
    // TILES_Y to TILES_Y + TILE_H; TILE_GAP is the visible space between two plates.
    const val TILES_Y = 380
    const val TILE_W = 120
    const val TILE_H = 80
    const val TILE_GAP = 16
    const val TILE_RADIUS = 12f
    const val TILE_ICON = 56
    /** The icon's own corners; [IndexData]'s icon is rendered at [TILE_ICON] with these. */
    const val TILE_ICON_RADIUS = 12f

    // The right column: the program's text, its channel's cards, then the week.
    const val COLUMN_X = 380
    const val COLUMN_W = 556
    const val HEADER_Y = 64
    const val STRIP_Y = 204
    const val CARD_H = 99
    const val CARD_GAP = 12
    const val CARD_RADIUS = 8f
    const val STATS_Y = 378
    const val FOOTER_BOTTOM = 17

    val hours = Color.parseColor("#5E6865")
    val barOff = Color.parseColor("#2C3532")
    val markerIdle = Color.parseColor("#3D4A45")
    val description = Color.parseColor("#B9C2BF")
    val footerValue = Color.parseColor("#BFC7C4")
    val pillText = Color.parseColor("#C4CCC9")
    /** Tokens.surface at 70%. */
    val pill = Color.parseColor("#B31A201E")

    /** Card widths by TvContract poster aspect ratio, at [CARD_H] tall. */
    fun cardWidth(ratio: Int): Int = when (ratio) {
        1 -> 149 // 3:2
        2 -> 132 // 4:3
        3 -> 99 // 1:1
        4, 5 -> 66 // 2:3 posters
        else -> 176 // 16:9
    }
}
