package cloud.samlo.couch.settings

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cloud.samlo.couch.R
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp

/**
 * The settings screen's frame: title and hint above a scrolling list on the left, and a help
 * panel on the right that explains the focused option.
 */
class SettingsLayout(context: Context) {
    val title = TextView(context).apply {
        setText(R.string.settings_title)
        textSize = 30f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Tokens.textPrimary)
        setPadding(context.dp(20), 0, 0, 0)
    }
    val hint = TextView(context).apply {
        textSize = 15f
        setTextColor(Tokens.textMuted)
        setPadding(context.dp(20), context.dp(4), 0, context.dp(16))
    }
    val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, context.dp(60))
    }
    val help = TextView(context).apply {
        textSize = 17f
        setTextColor(Tokens.textMuted)
        setLineSpacing(0f, 1.25f)
    }

    val root = FrameLayout(context).apply {
        addView(column(context), FrameLayout.LayoutParams(context.dp(560), MATCH_PARENT).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H) - context.dp(20)
            topMargin = context.dp(Tokens.OVERSCAN_V + 10)
        })
        addView(help, FrameLayout.LayoutParams(context.dp(300), WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H + 600)
            topMargin = context.dp(Tokens.OVERSCAN_V + 120)
        })
    }

    /** Everything currently in the list: section headers and option rows. */
    fun items(): List<View> = (0 until list.childCount).map { list.getChildAt(it) }

    private fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(title)
        addView(hint)
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(40))
            addView(list)
        }, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
}
