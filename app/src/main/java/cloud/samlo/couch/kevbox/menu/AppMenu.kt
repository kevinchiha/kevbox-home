package cloud.samlo.couch.kevbox.menu

import android.app.Activity

/** Stub: task 9 fills this in. */
object AppMenu {
    enum class Place { COLUMN, DRAWER }

    @Suppress("UNUSED_PARAMETER")
    fun show(activity: Activity, pkg: String, place: Place, apps: List<String>, onMove: () -> Unit) = Unit
}
