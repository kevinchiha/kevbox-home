package cloud.samlo.couch.kevbox.drawer

/**
 * The pure grid geometry behind arrow keys in Move mode. It knows nothing of views, so the JVM
 * tests check every edge without a TV.
 */
object GridMove {
    const val COLUMNS = 5

    /** The index [from] moves to for a step of [dx] columns and [dy] rows in a list of [size], clamped; [from] when the step leaves the grid. */
    fun target(from: Int, dx: Int, dy: Int, size: Int, columns: Int = COLUMNS): Int {
        if (size <= 0 || columns <= 0 || from !in 0 until size) return from
        val column = from % columns + dx
        val row = from / columns + dy
        // No wrapping: a sideways step off the end of a row, or a step above or below the grid, stays put.
        if (column !in 0 until columns || row !in 0..(size - 1) / columns) return from
        val to = row * columns + column
        return when {
            to < size -> to
            // Down into a short last row with no cell under [from]: the last app is the nearest place.
            dy != 0 -> size - 1
            else -> from
        }
    }
}
