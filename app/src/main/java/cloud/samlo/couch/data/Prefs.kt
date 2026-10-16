package cloud.samlo.couch.data

import android.content.Context
import android.content.SharedPreferences

/** The one preferences file: caches (weather, wallpapers) and the HOME-capture flag share it. */
object Prefs {
    private const val NAME = "couch"

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
