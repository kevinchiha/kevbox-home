package cloud.samlo.couch.kevbox.buttons

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import cloud.samlo.couch.index.IndexText
import cloud.samlo.couch.index.IndexTokens
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.dpf

/** Couch's godmode green, borrowed for the "internet works" dot so `Tokens.kt` stays unedited. */
val VALIDATED_DOT = Tokens.GODMODE_COLOR

/**
 * One top-bar pill, the shape of the Index layout's old Settings pill: 26dp tall, an icon, and a
 * label. With [alwaysText] false it is a 26dp circle until focused, then the label widens in, so
 * the row stays short while still saying what each icon means.
 */
@SuppressLint("ViewConstructor")
class TopButton(context: Context, private val alwaysText: Boolean, focusable: Boolean) : LinearLayout(context) {
    private val icon = ImageView(context).apply { setColorFilter(IndexTokens.pillText) }
    private val dot = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(VALIDATED_DOT)
        }
        visibility = GONE
    }
    private val label = IndexText(context, 11f, 500, IndexTokens.pillText).apply { maxLines = 1 }
    private var shown = if (alwaysText) 1f else 0f
    private var animator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = focusable
        isClickable = focusable
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(0x33FFFFFF, Tokens.ring))
            addState(intArrayOf(), shape(IndexTokens.pill, null))
        }
        val box = FrameLayout(context).apply {
            clipChildren = false
            addView(icon, FrameLayout.LayoutParams(context.dp(ICON), context.dp(ICON)))
            addView(dot, FrameLayout.LayoutParams(context.dp(DOT), context.dp(DOT), Gravity.TOP or Gravity.END).apply {
                topMargin = -context.dp(1)
                marginEnd = -context.dp(1)
            })
        }
        clipChildren = false
        addView(box, LayoutParams(context.dp(ICON), context.dp(ICON)))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        place(shown)
    }

    fun setIcon(res: Int) = icon.setImageResource(res)

    fun setDot(on: Boolean) {
        dot.visibility = if (on) VISIBLE else GONE
    }

    /** The label, shown or not; it is the spoken name either way. */
    fun setText(text: String) {
        label.text = text
        contentDescription = text
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!alwaysText) widen(if (gainFocus) 1f else 0f)
    }

    /** Fades the whole pill in or out of the row; for the VPN item. */
    fun appear(visible: Boolean) {
        if (visible == (visibility == VISIBLE && alpha == 1f)) return
        animate().cancel()
        if (visible) {
            if (visibility != VISIBLE) alpha = 0f
            visibility = VISIBLE
            animate().alpha(1f).setDuration(Tokens.APPEAR_MS).setInterpolator(Tokens.ease).start()
        } else if (visibility == VISIBLE) {
            animate().alpha(0f).setDuration(Tokens.APPEAR_MS).setInterpolator(Tokens.ease).withEndAction {
                visibility = GONE
                alpha = 1f
            }.start()
        }
    }

    private fun widen(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = Tokens.APPEAR_MS
            interpolator = Tokens.ease
            addUpdateListener { place(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Lays the pill out at [f] of the way from circle (0) to full pill (1). At 1 the label goes
     * back to wrap_content, so a state change while focused reflows the text in place.
     */
    private fun place(f: Float) {
        shown = f
        val pad = context.dp(CIRCLE_PAD) + (context.dp(TEXT_PAD - CIRCLE_PAD) * f).toInt()
        setPadding(pad, 0, pad, 0)
        val params = label.layoutParams as LayoutParams
        params.marginStart = (context.dp(GAP) * f).toInt()
        params.width = if (f >= 1f) LayoutParams.WRAP_CONTENT else (naturalWidth() * f).toInt()
        label.layoutParams = params
        label.alpha = f
        label.visibility = if (f <= 0f) GONE else VISIBLE
    }

    private fun naturalWidth(): Int {
        label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        return label.measuredWidth
    }

    private fun shape(color: Int, stroke: Int?) = GradientDrawable().apply {
        cornerRadius = context.dpf(13f)
        setColor(color)
        stroke?.let { setStroke(context.dp(2), it) }
    }

    private companion object {
        const val ICON = 12
        const val DOT = 4
        const val GAP = 5
        /** (26 - 12) / 2: an icon-only pill is a 26dp circle. */
        const val CIRCLE_PAD = 7
        /** The old Settings pill's side padding. */
        const val TEXT_PAD = 11
    }
}
