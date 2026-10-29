package cloud.samlo.couch.kevbox.home

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import cloud.samlo.couch.ui.Tokens

/**
 * The darkening laid over the backdrop so text stays readable. [level] is "light", "medium" or
 * "strong": how much the left edge and the middle are darkened. Couch's own copy went with its
 * Rows hero in Couch 0.9.0; this one keeps its colours.
 */
internal fun scrimDrawable(level: String): LayerDrawable {
    val bg = Tokens.background and 0x00FFFFFF
    val (edge, mid) = when (level) {
        "light" -> 0xCC000000.toInt() to 0x55000000
        "strong" -> 0xFF000000.toInt() to 0xD9000000.toInt()
        else -> 0xF2000000.toInt() to 0x99000000.toInt()
    }
    val left = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(bg or edge, bg or mid, bg),
    )
    val bottom = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(bg, bg or 0xB3000000.toInt(), bg or 0xFF000000.toInt()),
    )
    // Keeps the clock readable over bright art.
    val top = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(bg or 0x8C000000.toInt(), bg, bg, bg),
    )
    return LayerDrawable(arrayOf(left, bottom, top))
}
