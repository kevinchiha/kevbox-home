package cloud.samlo.couch.kevbox.home

import android.view.View

/**
 * Up into the top buttons and Down back out. Remembers the view Up left from (an app row, a
 * channel card, More apps) so Down returns there rather than to wherever Android's geometry
 * search would land.
 */
class TopFocus {
    private var origin: View? = null

    /** Up from [from] to [button], the leftmost top button. Null when there is none: focus stays. */
    fun up(from: View?, button: View?): View? {
        button ?: return null
        origin = from
        return button
    }

    /**
     * Down from a top button: the view Up left from while it is still attached and focusable,
     * else the first of [fallbacks] there is (the selected app row, then More apps).
     */
    fun down(vararg fallbacks: View?): View? {
        val back = origin?.takeIf { it.isAttachedToWindow && it.isFocusable && it.isShown }
        origin = null
        return back ?: fallbacks.firstOrNull { it != null }
    }
}
