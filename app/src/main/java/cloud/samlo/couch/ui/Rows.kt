package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A titled row of cards. [id] stays stable across reloads so unchanged rows are left alone. */
@SuppressLint("ViewConstructor")
class Section(context: Context, val id: String) : LinearLayout(context) {
    val title = TextView(context).apply {
        textSize = 15f
        letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Tokens.textMuted)
        setPadding(context.dp(Tokens.OVERSCAN_H), 0, context.dp(Tokens.OVERSCAN_H), 0)
    }
    val row = FocusRow(context)
    var signature = ""

    init {
        orientation = VERTICAL
        addView(title)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}

/**
 * The vertical stack of sections. Like the rows, it glides by translation and anchors: the
 * focused section always sits at the top of the rows area, just under the hero.
 */
class Rows(context: Context) : ScrollView(context) {
    val column: LinearLayout = object : LinearLayout(context) {
        override fun requestChildFocus(child: View?, focused: View?) {
            super.requestChildFocus(child, focused)
            (child as? Section)?.let { reveal(it) }
        }
    }.apply { orientation = LinearLayout.VERTICAL }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        // Room under the last row so it can still glide up to the anchor.
        column.setPadding(0, 0, 0, context.dp(200))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun sections(): List<Section> = (0 until column.childCount).map { column.getChildAt(it) as Section }

    fun section(id: String): Section? = sections().firstOrNull { it.id == id }

    fun reveal(section: Section) {
        val maxShift = (column.height - height).coerceAtLeast(0)
        val target = -section.top.coerceIn(0, maxShift).toFloat()
        if (column.translationY == target) return
        column.animate().translationY(target).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
    }

    /** Re-anchors on the focused section, e.g. after sections above it came or went. */
    fun revealFocused() {
        (column.focusedChild as? Section)?.let { reveal(it) }
    }

    fun reset() {
        column.animate().cancel()
        column.translationY = 0f
    }

    override fun executeKeyEvent(event: KeyEvent) = false
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}
