package cloud.samlo.couch.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf

/**
 * One focusable option line: title on the left, current value on the right. Focusing it shows
 * [helpText] in the [help] panel.
 */
@SuppressLint("ViewConstructor")
class OptionRow(
    context: Context,
    private val helpText: String,
    private val valueOf: () -> String,
    help: TextView,
) : LinearLayout(context) {
    val label = TextView(context).apply {
        textSize = 17f
        setTextColor(Tokens.textPrimary)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val value = TextView(context).apply {
        textSize = 16f
        setTextColor(Tokens.textMuted)
        maxLines = 1
        gravity = Gravity.END
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(context.dp(20), 0, context.dp(20), 0)
        background = optionBackground(context)
        addView(label, LayoutParams(0, WRAP_CONTENT, 1f))
        addView(value, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = context.dp(16) })
        setOnFocusChangeListener { _, focused ->
            if (focused) help.text = helpText
            value.setTextColor(if (focused) Tokens.textPrimary else Tokens.textMuted)
        }
        refresh()
    }

    fun refresh() {
        value.text = valueOf()
    }
}

/** Rounded highlight in the accent colour while focused; call again after the accent changes. */
fun optionBackground(context: Context) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
        cornerRadius = context.dpf(Tokens.CARD_RADIUS)
        setColor(Tokens.surfaceFocused)
        setStroke(context.dp(2), Tokens.accent)
    })
    addState(intArrayOf(), GradientDrawable().apply { cornerRadius = context.dpf(Tokens.CARD_RADIUS) })
}
