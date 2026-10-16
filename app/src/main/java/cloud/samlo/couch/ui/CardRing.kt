package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View

/** The focus ring: a soft glow outside a crisp stroke. Layer 1 is the stroke. */
fun cardRing(context: Context): LayerDrawable {
    val radius = context.dpf(Tokens.CARD_RADIUS)
    val glow = GradientDrawable().apply {
        cornerRadius = radius + context.dpf(6f)
        setStroke(context.dp(2), Tokens.glow)
    }
    val ring = GradientDrawable().apply {
        cornerRadius = radius + context.dpf(4f)
        setStroke(context.dp(3), Tokens.ring)
    }
    return LayerDrawable(arrayOf(glow, ring)).apply {
        val i = context.dp(2)
        setLayerInset(1, i, i, i, i)
    }
}

/** Watch progress along the bottom edge; drawn once, never animated. */
@SuppressLint("ViewConstructor")
class CardProgress(context: Context, private val progress: Float) : View(context) {
    private val track = Paint().apply { color = 0x55FFFFFF }
    private val fill = Paint().apply { color = Tokens.accent }
    private val height = context.dpf(4f)
    private val margin = context.dpf(10f)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2
        val bottom = getHeight() - margin
        rect.set(margin, bottom - height, width - margin, bottom)
        canvas.drawRoundRect(rect, r, r, track)
        rect.right = margin + (width - margin * 2) * progress
        canvas.drawRoundRect(rect, r, r, fill)
    }
}
