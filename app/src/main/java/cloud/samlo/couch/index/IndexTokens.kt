package cloud.samlo.couch.index

import android.graphics.Color
import cloud.samlo.couch.ui.Tokens

/**
 * The Index layout's measurements, in dp on Couch's 960x540dp canvas, and the few colours it
 * adds to [cloud.samlo.couch.ui.Tokens]. Everything is placed at fixed positions, so focus moves
 * never trigger a layout pass.
 */
object IndexTokens {
    /** The widest the top bar's weather place text gets, so a long place name never crowds the buttons. */
    const val PLACE_MAX_W = 120

    /** The left edge of the clock, the text block and the system line; the top bar ends as far from the right. */
    const val EDGE_X = 24

    // The text block, left: the focused program's text, or the chosen app's name. The top bar
    // above it starts at IndexScreen.TOP_BAR_Y (23dp) and is 52dp tall while now playing shows,
    // so its bottom then sits at 75dp. The block starts level with the upper row's cards.
    const val TEXT_Y = 92
    const val TEXT_W = 330

    // KevBox's app tiles along the bottom: the app's TV banner at the drawer's card size, its
    // corners rounded like the drawer's. The band runs from TILES_Y to TILES_Y + TILE_H; TILE_GAP
    // is the visible space between two plates.
    const val TILES_Y = 380
    const val TILE_W = Tokens.APP_W
    const val TILE_H = Tokens.APP_H
    const val TILE_GAP = 16
    const val TILE_RADIUS = Tokens.CARD_RADIUS
    /** An app with no banner shows its launcher icon this size on the plate instead. */
    const val TILE_ICON = 56
    /** The icon's own corners; [IndexData]'s icon is rendered at [TILE_ICON] with these. */
    const val TILE_ICON_RADIUS = 12f

    // The two rows of cards, right. Each row's label sits at its LABEL_Y and its cards' plates
    // start ROW_CARDS_DY under it. The space between the rows' cards (236 - 191) equals the space
    // from the lower row's cards to the tiles (380 - 335): Kevin's 2026-10-30 ask. The rows start
    // TEXT_GAP past the text block's text, never left of ROWS_MIN_X nor right of ROWS_X.
    const val ROWS_X = 380
    const val TEXT_GAP = ROWS_X - EDGE_X - TEXT_W
    const val ROWS_MIN_X = EDGE_X + TEXT_GAP
    const val ROW_1_LABEL_Y = 62
    const val ROW_2_LABEL_Y = 206
    const val ROW_CARDS_DY = 30
    const val CARD_H = 99
    const val CARD_GAP = 12
    const val CARD_RADIUS = 8f
    const val FOOTER_BOTTOM = 17

    val hours = Color.parseColor("#5E6865")
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
