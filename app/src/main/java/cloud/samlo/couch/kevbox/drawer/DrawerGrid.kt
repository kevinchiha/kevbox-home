package cloud.samlo.couch.kevbox.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.kevbox.home.AppLists
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.Fonts
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.arrange
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.glideToNewPlaces
import kotlin.math.ceil

/**
 * The drawer's screen: a heading over a [GridMove.COLUMNS]-wide grid of Couch cards. The grid
 * never scrolls: like Couch's rows it glides by translation, so a focus move costs no redraw.
 * During Move the held card keeps focus and its neighbours glide to their new cells.
 */
class DrawerGrid(private val activity: Activity, private val images: Images, private val onLongPress: (Card) -> Unit) {
    private val heading = TextView(activity).apply {
        typeface = Fonts.hanken(activity, 600)
        textSize = 20f
        setTextColor(Tokens.textMuted)
        setText(R.string.kevbox_more_apps)
        setPadding(activity.dp(Tokens.RING_INSET), 0, 0, activity.dp(Tokens.CARD_GAP))
    }
    private val boxW = activity.dp(CARD_W + 2 * Tokens.RING_INSET)
    private val boxH = activity.dp(CARD_H + 2 * Tokens.RING_INSET + LABEL_DP)
    private val gap = activity.dp(Tokens.CARD_GAP)

    /** Room above and below the cards for the held card at [Tokens.MOVE_SCALE], so the viewport never clips it. */
    private val room = ceil(boxH * (Tokens.MOVE_SCALE - 1f) / 2f).toInt()
    private val cells = Cells()
    private val viewport = FrameLayout(activity).apply { addView(cells) }
    private var targetY = 0f
    private var moving: Card? = null
    private var orderAtStart: List<String> = emptyList()

    val root: View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Tokens.background)
        setPadding(activity.dp(Tokens.OVERSCAN_H), activity.dp(Tokens.OVERSCAN_V), activity.dp(Tokens.OVERSCAN_H), 0)
        addView(heading)
        addView(viewport, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    val isMoving: Boolean get() = moving != null

    fun cards(): List<Card> = (0 until cells.childCount).map { cells.getChildAt(it) as Card }

    /**
     * Shows [tiles] in order, reusing each card whose tile is unchanged. Focus stays on the same
     * app, else the tile now at its old place, else the last; the first tile on open.
     */
    fun show(tiles: List<Tile>) {
        val existing = cards().associateBy { it.tile.key }
        val wanted = tiles.map { t -> existing[t.key]?.takeIf { it.tile.signature == t.signature } ?: card(t) }
        cells.arrange(wanted) { old, index ->
            val key = (old as Card).tile.key
            (wanted.firstOrNull { it.tile.key == key } ?: wanted.getOrNull(index) ?: wanted.lastOrNull())?.requestFocus()
        }
        if (!cells.hasFocus()) wanted.firstOrNull()?.requestFocus()
    }

    fun startMove(pkg: String) {
        val card = cards().firstOrNull { it.tile.key == pkg } ?: return
        orderAtStart = cards().map { it.tile.key }
        moving = card
        card.requestFocus()
        card.moving = true
        heading.setText(R.string.kevbox_move_hint)
    }

    /** One arrow step of the held card; a step off the grid does nothing. */
    fun step(dx: Int, dy: Int) {
        val card = moving ?: return
        val now = cards()
        val from = now.indexOf(card)
        val to = GridMove.target(from, dx, dy, now.size)
        if (to == from) return
        val order = AppLists.move(now.map { it.tile.key }, from, to - from)
        val byKey = now.associateBy { it.tile.key }
        glideToNewPlaces(card, *now.filter { it !== card }.toTypedArray())
        cells.arrange(order.map { byKey.getValue(it) }) { _, _ -> }
    }

    /** Ends Move; returns the whole order as shown when it changed, for the caller to save, else null. */
    fun endMove(): List<String>? {
        val card = moving ?: return null
        moving = null
        card.moving = false
        heading.setText(R.string.kevbox_more_apps)
        return cards().map { it.tile.key }.takeIf { it != orderAtStart }
    }

    private fun card(tile: Tile) =
        Card(activity, tile, CARD_W, CARD_H, images, showLabel = true, look = LOOK, onFocused = ::reveal).apply {
            isLongClickable = true
            setOnLongClickListener {
                onLongPress(this)
                true
            }
        }

    /** Glides the grid so [card]'s whole row, ring and label included, sits inside the bottom overscan margin. */
    private fun reveal(card: View) {
        if (viewport.height == 0) return
        val limit = viewport.height - activity.dp(Tokens.OVERSCAN_V)
        var y = targetY
        if (card.bottom + room + y > limit) y = (limit - card.bottom - room).toFloat()
        if (card.top - room + y < 0) y = (room - card.top).toFloat()
        y = y.coerceAtMost(0f)
        if (y == targetY) return
        targetY = y
        cells.animate().translationY(y).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /** Lays the cards out by child order, [GridMove.COLUMNS] to a row, [Tokens.CARD_GAP] apart. */
    @SuppressLint("ViewConstructor")
    private inner class Cells : ViewGroup(activity) {
        init {
            clipChildren = false
            clipToPadding = false
            setPadding(0, room, 0, room)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val exactW = MeasureSpec.makeMeasureSpec(boxW, MeasureSpec.EXACTLY)
            val exactH = MeasureSpec.makeMeasureSpec(boxH, MeasureSpec.EXACTLY)
            for (i in 0 until childCount) getChildAt(i).measure(exactW, exactH)
            val rows = (childCount + GridMove.COLUMNS - 1) / GridMove.COLUMNS
            val height = paddingTop + paddingBottom + rows * boxH + (rows - 1).coerceAtLeast(0) * gap
            setMeasuredDimension(GridMove.COLUMNS * boxW + (GridMove.COLUMNS - 1) * gap, height)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val x = i % GridMove.COLUMNS * (boxW + gap)
                val y = paddingTop + i / GridMove.COLUMNS * (boxH + gap)
                getChildAt(i).layout(x, y, x + boxW, y + boxH)
            }
            // Cards only know their place once laid out: keep the focused one in view after any reorder.
            focusedChild?.let { reveal(it) }
        }
    }

    companion object {
        /** Couch's canvas width in dp (see [Tokens]). */
        private const val CANVAS_W = 960
        /** Content width: five boxes and four gaps fill the canvas's title-safe width exactly. */
        const val CARD_W = (CANVAS_W - 2 * Tokens.OVERSCAN_H - (GridMove.COLUMNS - 1) * Tokens.CARD_GAP) /
            GridMove.COLUMNS - 2 * Tokens.RING_INSET
        /** At the Apps row's [Tokens.APP_W]:[Tokens.APP_H] ratio, so banners keep their shape. */
        const val CARD_H = CARD_W * Tokens.APP_H / Tokens.APP_W
        /** The label strip [Card] adds under its content when it shows a label. */
        private const val LABEL_DP = 24
        private const val LOOK = "drawer"
    }
}
