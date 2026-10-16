package cloud.samlo.couch.data

import android.database.Cursor

// Providers differ in which columns they publish, so a missing or null column is never an error.

internal fun Cursor.string(column: String): String? {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) null else getString(i)
}

internal fun Cursor.long(column: String): Long {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) 0L else getLong(i)
}

internal fun Cursor.int(column: String, fallback: Int): Int {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) fallback else getInt(i)
}
