package cloud.samlo.couch.kevbox.home

/**
 * The pure part of Move along Home's app tiles (the left column before them, hence the name):
 * where the moving app sits, and how its new place is written back to `config.apps`. Kept apart
 * from the views so the JVM tests can check it.
 */
class ColumnMove(order: List<String>, pkg: String) {
    private val moving = pkg
    private var current: List<String> = order.toList()
    private var at: Int = current.indexOf(pkg)

    init {
        require(at >= 0) { "$pkg is not in the column" }
    }

    /** The tiles' order as it stands, the moving app included. */
    val order: List<String> get() = current

    /** Where the moving app sits in [order]. */
    val index: Int get() = at

    /** Shifts the moving app by [by] places, clamped; returns true when the order changed. */
    fun shift(by: Int): Boolean {
        val next = AppLists.move(current, at, by)
        if (next == current) return false
        current = next
        at = next.indexOf(moving)
        return true
    }

    /**
     * [apps] with the tile packages' slots refilled in the new order; every other package keeps
     * its slot. Hidden and uninstalled apps keep theirs too, so one that comes back returns to
     * where it was rather than to the end.
     */
    fun saved(apps: List<String>): List<String> {
        val inColumn = current.toHashSet()
        val next = current.iterator()
        val placed = HashSet<String>()
        val out = ArrayList<String>(apps.size)
        for (pkg in apps) {
            when {
                pkg !in inColumn -> out.add(pkg)
                // A duplicate slot would put the app back in two places; the tiles only show the first.
                placed.add(pkg) && next.hasNext() -> out.add(next.next())
            }
        }
        // A tile app missing from apps (an outdated list) still keeps its new place, at the end.
        next.forEachRemaining { out.add(it) }
        return out
    }
}
