package cloud.samlo.couch.kevbox.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.samlo.couch.R
import cloud.samlo.couch.index.IndexEntry
import cloud.samlo.couch.index.IndexTokens
import cloud.samlo.couch.ui.FlatView
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.arrange
import cloud.samlo.couch.ui.cardRing
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf
import cloud.samlo.couch.ui.fill

/**
 * The app tiles along the bottom of Home: one [AppTile] per app, in order, then [moreApps], all
 * in one centred line. A tile Home is showing, or the focused one, grows a little and shows its
 * ring; nothing changes size, so a focus move never triggers a layout pass.
 *
 * [moreApps] sits outside the line that holds the app tiles, because [bind] would remove anything
 * else from it. This view pads its tiles by [SCALE_ROOM] on every side, so a grown tile's ring
 * stays inside its bounds even under a parent that clips.
 */
@SuppressLint("ViewConstructor")
class AppTiles(context: Context, private val makeTile: (IndexEntry) -> AppTile) : FrameLayout(context) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
    }
    private val more = MoreAppsTile(context)
    /** The last tile, always there: opens the drawer of every other app. */
    val moreApps: View get() = more

    init {
        clipChildren = false
        clipToPadding = false
        val room = context.dp(SCALE_ROOM)
        setPadding(room, room, room, room)
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            addView(column, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(more, tileParams(context))
        }
        addView(line, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
    }

    fun tiles(): List<AppTile> = (0 until column.childCount).map { column.getChildAt(it) as AppTile }

    /** Shows [entries] in order, reusing tiles by app; only tiles whose data changed are rebound. */
    fun bind(entries: List<IndexEntry>, rebuildAll: Boolean, moveFocus: (AppTile) -> Unit) {
        val existing = tiles().associateBy { it.entry.key }
        val wanted = entries.map { e ->
            val kept = existing[e.key]?.takeIf { !rebuildAll }
            kept?.also { if (it.entry.signature != e.signature) it.bind(e) } ?: makeTile(e).apply {
                layoutParams = tileParams(context)
            }
        }
        if (wanted.isEmpty()) {
            // The last app went away under focus: More apps is all that is left.
            if (column.hasFocus()) more.requestFocus()
            column.removeAllViews()
        } else {
            // The focused app went away (hidden, uninstalled): focus the one now in its place.
            column.arrange(wanted) { _, index -> moveFocus(wanted[index.coerceAtMost(wanted.lastIndex)]) }
        }
    }

    /** Makes [tile] the one Home is showing; null chooses More apps. */
    fun select(tile: AppTile?) {
        tiles().forEach { it.chosen = it === tile }
        more.chosen = tile == null
    }

    private companion object {
        /** Room around the tiles for a grown one: FOCUS_SCALE adds 4% of a tile on each side, under 6dp. */
        const val SCALE_ROOM = 6

        /** Tiles stand RING_INSET larger than their plates on each side; the margins make up TILE_GAP. */
        fun tileParams(context: Context): LinearLayout.LayoutParams {
            val inset = context.dp(Tokens.RING_INSET)
            val half = (context.dp(IndexTokens.TILE_GAP) - inset * 2) / 2
            return LinearLayout.LayoutParams(context.dp(IndexTokens.TILE_W) + inset * 2, context.dp(IndexTokens.TILE_H) + inset * 2).apply {
                leftMargin = half
                rightMargin = half
            }
        }
    }
}

/** One app: its launcher icon alone, with the app's name as the content description. */
@SuppressLint("ViewConstructor")
class AppTile(context: Context, entry: IndexEntry) : TileFrame(context) {
    var entry: IndexEntry = entry
        private set

    private val icon = ImageView(context)

    init {
        isLongClickable = true
        body.addView(icon, LayoutParams(context.dp(IndexTokens.TILE_ICON), context.dp(IndexTokens.TILE_ICON), Gravity.CENTER))
        bind(entry)
    }

    fun bind(entry: IndexEntry) {
        this.entry = entry
        contentDescription = entry.app.label
        icon.setImageBitmap(entry.icon)
    }
}

/** More apps: a grid mark on the same plate. Clickable, never long-clickable: it has no menu. */
@SuppressLint("ViewConstructor")
private class MoreAppsTile(context: Context) : TileFrame(context) {
    private val mark = GridMark(context)

    init {
        isLongClickable = false
        contentDescription = context.getString(R.string.kevbox_more_apps)
        body.addView(mark, LayoutParams(context.dp(IndexTokens.TILE_ICON), context.dp(IndexTokens.TILE_ICON), Gravity.CENTER))
    }

    /** Lit, the mark brightens like the old More apps row's label. */
    override fun onLit(lit: Boolean) = mark.setLit(lit)

    /** Four rounded squares, 2 by 2, centred in the icon slot. Drawn in code: no asset to ship. */
    private class GridMark(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.textMuted }
        private val square = context.dpf(SQUARE)
        private val gap = context.dpf(GAP)
        private val radius = context.dpf(RADIUS)
        private val rect = RectF()

        fun setLit(lit: Boolean) {
            val colour = if (lit) Tokens.textPrimary else Tokens.textMuted
            if (paint.color == colour) return
            paint.color = colour
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val size = square * 2 + gap
            val left = (width - size) / 2f
            val top = (height - size) / 2f
            for (row in 0..1) for (col in 0..1) {
                val x = left + col * (square + gap)
                val y = top + row * (square + gap)
                rect.set(x, y, x + square, y + square)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        private companion object {
            const val SQUARE = 11f
            const val GAP = 4f
            const val RADIUS = 3f
        }
    }
}

/**
 * A tile's frame: a rounded plate [Tokens.RING_INSET] inside the view, with the focus ring around
 * it. Focused, it grows to [Tokens.FOCUS_SCALE] under the full ring; [chosen] but not focused (focus
 * is up in the cards or the top bar), it stays grown under a fainter ring. Only render properties
 * change (scale, the ring's alpha, the plate's colour), so no layout pass.
 *
 * Focus is followed through [onFocusChanged], which leaves the focus change listener to Home.
 */
@SuppressLint("ViewConstructor")
abstract class TileFrame(context: Context) : FrameLayout(context) {
    private val plate = GradientDrawable().apply { cornerRadius = context.dpf(IndexTokens.TILE_RADIUS) }
    /** Holds the icon or mark, centred on the plate. */
    protected val body = FrameLayout(context).apply { background = plate }
    private val ring = FlatView(context).apply {
        background = cardRing(context, IndexTokens.TILE_RADIUS, thin = true)
        alpha = 0f
    }
    /** Lit state as last painted, so a repeated restyle never repaints the plate. */
    private var painted: Boolean? = null

    /** The app Home is showing (or More apps, while it is focused). */
    var chosen = false
        set(value) {
            if (field == value) return
            field = value
            restyle()
        }

    init {
        isFocusable = true
        isClickable = true
        val inset = context.dp(Tokens.RING_INSET)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(inset, inset, inset, inset) })
        addView(ring, fill())
        // Not restyle: a subclass's onLit would run before its own fields exist.
        plate.setColor(IndexTokens.pill)
        painted = false
    }

    /** The plate just lit up or went quiet; subclasses brighten what they draw. */
    protected open fun onLit(lit: Boolean) {}

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        restyle()
    }

    /** A long press opens the app menu, which takes OK's key-up: back from it, drop the press scale. */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) restyle()
    }

    /** Back to the lit or resting size, out of any press scale; Move calls it on the tile it carries. */
    fun restyle() {
        val focused = isFocused
        val lit = focused || chosen
        if (painted != lit) {
            painted = lit
            plate.setColor(if (lit) Tokens.surfaceFocused else IndexTokens.pill)
            onLit(lit)
        }
        val duration = if (lit) Tokens.FOCUS_IN_MS else Tokens.FOCUS_OUT_MS
        scaleTo(if (lit) Tokens.FOCUS_SCALE else 1f, duration)
        val ringAlpha = when {
            focused -> 1f
            chosen -> CHOSEN_RING
            else -> 0f
        }
        ring.animate().alpha(ringAlpha).setDuration(duration).setInterpolator(Tokens.ease).start()
        // A grown tile draws above its neighbours.
        translationZ = if (lit) 1f else 0f
        (parent as? View)?.invalidate()
    }

    private fun scaleTo(scale: Float, durationMs: Long) {
        animate().scaleX(scale).scaleY(scale).setDuration(durationMs).setInterpolator(Tokens.ease).start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && event.repeatCount == 0) scaleTo(Tokens.PRESS_SCALE, Tokens.PRESS_MS)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isConfirmKey(keyCode) && hasFocus()) scaleTo(Tokens.FOCUS_SCALE, Tokens.FOCUS_IN_MS)
        return super.onKeyUp(keyCode, event)
    }

    private fun isConfirmKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private companion object {
        /** The ring's alpha on a chosen tile while focus is elsewhere: it marks the app, not focus. */
        const val CHOSEN_RING = 0.4f
    }
}
