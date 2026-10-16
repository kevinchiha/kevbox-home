package cloud.samlo.couch.ui

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import java.util.Locale

/**
 * A plain view for backgrounds, scrims and focus rings. Nothing inside it overlaps, so
 * skipping the offscreen layer keeps alpha fades cheap.
 */
class FlatView(context: Context) : View(context) {
    override fun hasOverlappingRendering() = false
}

/** Layout params filling the parent, for the stacked layers of a [FrameLayout]. */
fun fill() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

/** "Thursday, October 15" or "jeudi 15 octobre": weekday, day and month in the TV's language. */
fun TextClock.showLongDate() {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
    format12Hour = pattern
    format24Hour = pattern
}
