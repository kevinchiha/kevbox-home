package cloud.samlo.couch.ui

import android.view.View
import android.view.ViewGroup

/**
 * Makes the children exactly [wanted], in order, without ever detaching the child that holds
 * focus: that would make Android hand focus to whatever comes first in the window. If the
 * focused child itself is going away, [moveFocus] gets a chance to move focus first.
 */
fun ViewGroup.arrange(wanted: List<View>, moveFocus: (old: View, index: Int) -> Unit) {
    val keep = wanted.toHashSet()
    val focused = focusedChild
    val focusedIndex = indexOfChild(focused)
    wanted.filter { it.parent !== this }.forEach { addView(it) }
    if (focused != null && focused !in keep) moveFocus(focused, focusedIndex)
    for (i in childCount - 1 downTo 0) if (getChildAt(i) !in keep) removeViewAt(i)
    wanted.forEachIndexed { i, view ->
        while (getChildAt(i) !== view) {
            val other = getChildAt(i)
            if (other.hasFocus()) {
                // Move the unfocused one into place instead.
                removeView(view)
                addView(view, i)
            } else {
                removeViewAt(i)
                addView(other)
            }
        }
    }
}
