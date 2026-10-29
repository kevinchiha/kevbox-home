package cloud.samlo.couch.index

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.samlo.couch.kevbox.home.MoreAppsRow
import cloud.samlo.couch.ui.FlatView
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.arrange
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf

/**
 * The app list on the left. When every row, the divider and More apps fit, the track sits still
 * with them centred on the screen. Otherwise, like Couch's rows, it never scrolls: the track glides
 * by translation so the selected row keeps [IndexTokens.ROWS_ABOVE] rows above it, and the last
 * rows stop above the fade at the bottom. The selected row's background and accent marker are
 * one view behind the rows that glides from row to row, also by translation.
 *
 * Under the last app, a divider and KevBox's [moreApps] row glide with the track. They sit
 * outside [column], which holds app rows only: [bind] would remove anything else from it.
 */
@SuppressLint("ViewConstructor")
class AppList(context: Context, private val makeRow: (IndexEntry) -> AppRow) : FrameLayout(context) {
    /** The rows and the selection behind them, moving as one. */
    private val track = FrameLayout(context)
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val divider = FlatView(context).apply { setBackgroundColor(IndexTokens.markerIdle) }
    val moreApps = MoreAppsRow(context)
    private val fill = GradientDrawable().apply { cornerRadius = context.dpf(6f) }
    private val marker = GradientDrawable().apply { cornerRadius = context.dpf(1.5f) }
    private val selection = FrameLayout(context).apply {
        background = fill
        visibility = INVISIBLE
        addView(FlatView(context).apply { background = marker }, LayoutParams(context.dp(3), context.dp(IndexTokens.ROW_H - 22), Gravity.CENTER_VERTICAL))
    }
    private val fade = FlatView(context).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Tokens.background and 0x00FFFFFF, Tokens.background),
        )
    }
    private var selectionY = 0f
    /** Whether the last [reveal] centred the column rather than gliding it. */
    private var centred = false
    /** In-row state and accent as last painted; repainting only on a change keeps a key press from redrawing it. */
    private var painted: Pair<Boolean, Int>? = null

    init {
        clipChildren = true
        track.addView(selection, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(IndexTokens.ROW_H)))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(column, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(divider, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(DIVIDER_H)).apply {
                topMargin = context.dp(DIVIDER_GAP)
                bottomMargin = context.dp(DIVIDER_GAP)
            })
            addView(moreApps, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(IndexTokens.ROW_H)))
        }
        track.addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val fadeTop = context.dp(IndexTokens.LIST_FADE_Y)
        addView(fade, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(IndexTokens.LIST_H) - fadeTop).apply { topMargin = fadeTop })
        placeFooter(0)
    }

    /** The track keeps its full height, like a scroll view's content; this view clips it. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        track.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
    }

    fun rows(): List<AppRow> = (0 until column.childCount).map { column.getChildAt(it) as AppRow }

    /** Shows [entries] in order, reusing rows by app; only rows whose data changed are rebound. */
    fun bind(entries: List<IndexEntry>, rebuildAll: Boolean, moveFocus: (AppRow) -> Unit) {
        val existing = rows().associateBy { it.entry.key }
        val wanted = entries.map { e ->
            val kept = existing[e.key]?.takeIf { !rebuildAll }
            kept?.also { if (it.entry.signature != e.signature) it.bind(e) } ?: makeRow(e).apply {
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(IndexTokens.ROW_H)).apply {
                    bottomMargin = context.dp(IndexTokens.ROW_PITCH - IndexTokens.ROW_H)
                }
            }
        }
        placeFooter(wanted.size)
        if (wanted.isEmpty()) {
            // The last app went away under focus: More apps is all that is left.
            if (column.hasFocus()) moreApps.requestFocus()
            column.removeAllViews()
            if (!moreApps.chosen) selection.visibility = INVISIBLE
        } else {
            // The focused app went away (hidden, uninstalled): focus the one now in its place.
            column.arrange(wanted) { _, index -> moveFocus(wanted[index.coerceAtMost(wanted.lastIndex)]) }
        }
        // A new count moves the centred block, or starts or ends centring. Follow it even when
        // focus is in the cards or the top bar and nothing else calls reveal.
        if (centred || centredY() != null) reveal(chosenIndex())
    }

    /** Makes [row] the selected one; [inRow] while focus is in its channel row, which quietens it. */
    fun select(row: AppRow, inRow: Boolean) {
        rows().forEach { it.chosen = it === row }
        moreApps.chosen = false
        glide((column.indexOfChild(row) * context.dp(IndexTokens.ROW_PITCH)).toFloat(), inRow)
    }

    /** Puts the selection on More apps, with no app row chosen, and glides the list to show it. */
    fun selectMore() {
        rows().forEach { it.chosen = false }
        moreApps.chosen = true
        glide(rowY(column.childCount).toFloat(), inRow = false)
        reveal(column.childCount)
    }

    private fun glide(target: Float, inRow: Boolean) {
        if (painted != inRow to Tokens.accent) {
            painted = inRow to Tokens.accent
            fill.setColor(if (inRow) Tokens.surface else Tokens.surfaceFocused)
            marker.setColor(if (inRow) IndexTokens.markerIdle else Tokens.accent)
        }
        if (selection.visibility != VISIBLE) {
            selectionY = target
            selection.translationY = target
            selection.visibility = VISIBLE
            return
        }
        if (target == selectionY) return
        selectionY = target
        selection.animate().translationY(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /**
     * Glides so row [index] sits where the list wants it; More apps counts as row `rows().size`.
     * When the whole column fits, it rests centred whatever [index] is. Without [animate], the
     * selection lands at once too.
     */
    fun reveal(index: Int, animate: Boolean = true) {
        val pitch = context.dp(IndexTokens.ROW_PITCH)
        val end = (rowY(column.childCount) + pitch - IndexTokens.ROWS_SHOWN * pitch).coerceAtLeast(0)
        val middle = centredY()
        centred = middle != null
        val target = middle?.toFloat() ?: -(rowY(index) - IndexTokens.ROWS_ABOVE * pitch).coerceIn(0, end).toFloat()
        track.animate().cancel()
        if (!animate) {
            track.translationY = target
            selection.animate().cancel()
            selection.translationY = selectionY
            return
        }
        if (track.translationY == target) return
        track.animate().translationY(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /**
     * Top of row [index] in the track, in pixels, with [count] app rows; past the last app, More
     * apps' place under the divider.
     */
    private fun rowY(index: Int, count: Int = column.childCount): Int {
        if (index < count) return index * context.dp(IndexTokens.ROW_PITCH)
        if (count == 0) return 0
        return count * context.dp(IndexTokens.ROW_PITCH) + context.dp(DIVIDER_GAP) * 2 + context.dp(DIVIDER_H)
    }

    /**
     * The track's offset, in pixels, that centres [count] app rows, the divider and More apps on
     * the screen; null when they do not fit inside this view that way, and the list glides instead.
     */
    private fun centredY(count: Int = column.childCount): Int? {
        val block = rowY(count, count) + context.dp(IndexTokens.ROW_H)
        val y = context.dp(IndexTokens.SCREEN_H) / 2 - context.dp(IndexTokens.LIST_Y) - block / 2
        return y.takeIf { it >= 0 && it + block <= context.dp(IndexTokens.LIST_H) }
    }

    /** The chosen row's index, More apps' when it is chosen, else the first row's. */
    private fun chosenIndex(): Int {
        val chosen = rows().indexOfFirst { it.chosen }
        return when {
            chosen >= 0 -> chosen
            moreApps.chosen -> column.childCount
            else -> 0
        }
    }

    /**
     * With no apps the divider goes and More apps moves to the top. The fade paints the flat
     * background, a dark band over the backdrop, so it only shows while the rows reach it, and
     * never over a centred column, which ends above this view's bottom.
     */
    private fun placeFooter(count: Int) {
        divider.visibility = if (count == 0) GONE else VISIBLE
        val bottom = if (count == 0) 0 else count * IndexTokens.ROW_PITCH + DIVIDER_GAP * 2 + DIVIDER_H
        val reaches = bottom + IndexTokens.ROW_H > IndexTokens.LIST_FADE_Y
        fade.visibility = if (reaches && centredY(count) == null) VISIBLE else INVISIBLE
    }

    private companion object {
        const val DIVIDER_H = 1
        /** Space above and below the divider. */
        const val DIVIDER_GAP = 8
    }
}
