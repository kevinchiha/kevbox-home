package cloud.samlo.couch.kevbox.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import cloud.samlo.couch.R
import cloud.samlo.couch.index.IndexText
import cloud.samlo.couch.index.IndexTokens
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf

/**
 * The last row of the left column, always there: a grid mark and "More apps", which opens the
 * drawer of every other app. It looks like an app row with no status line, so the list's shared
 * selection glides onto it the same way. Clickable, never long-clickable: it has no menu.
 */
@SuppressLint("ViewConstructor")
class MoreAppsRow(context: Context) : FrameLayout(context) {
    private val label = IndexText(context, 19f, 600, Tokens.textMuted).apply {
        letterSpacing = -0.015f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        text = context.getString(R.string.kevbox_more_apps)
    }

    /** Focused: the label brightens like a chosen app row's name. */
    var chosen = false
        set(value) {
            if (field == value) return
            field = value
            label.setTextColor(if (value) Tokens.textPrimary else Tokens.textMuted)
        }

    init {
        isFocusable = true
        isClickable = true
        isLongClickable = false
        contentDescription = label.text
        // The same padding and icon slot as AppRow, so the label lines up with the app names.
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(PAD_START), 0, context.dp(PAD_END), 0)
            addView(GridMark(context), LinearLayout.LayoutParams(context.dp(IndexTokens.ICON), context.dp(IndexTokens.ICON)))
            addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(LABEL_GAP) })
        }
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Four small rounded squares, 2 by 2, centred in the icon slot. Drawn in code: no asset to ship. */
    private class GridMark(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tokens.textMuted }
        private val square = context.dpf(SQUARE)
        private val gap = context.dpf(GAP)
        private val radius = context.dpf(RADIUS)
        private val rect = RectF()

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
            const val SQUARE = 5f
            const val GAP = 2f
            const val RADIUS = 1.5f
        }
    }

    private companion object {
        // AppRow's padding and icon-to-name gap.
        const val PAD_START = 15
        const val PAD_END = 13
        const val LABEL_GAP = 12
    }
}
