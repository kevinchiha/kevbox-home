package cloud.samlo.couch.kevbox.home

/**
 * What the left column and the drawer hold, worked out from plain package lists so the JVM tests
 * can check the rules without a TV. The column and the drawer never share an app.
 */
object AppLists {
    /** The left column: [apps] in order, only those in [launchable], minus [hidden], no duplicates. */
    fun column(apps: List<String>, hidden: Set<String>, launchable: Set<String>): List<String> =
        apps.filter { it in launchable && it !in hidden }.distinct()

    /** The drawer: [launchable] (package to label) minus [self], [apps] and [hidden]; [drawer] order first, then by label. */
    fun drawer(
        launchable: Map<String, String>,
        apps: List<String>,
        drawer: List<String>,
        hidden: Set<String>,
        self: String,
    ): List<String> {
        val inColumn = apps.toSet()
        fun shown(pkg: String) = pkg in launchable && pkg != self && pkg !in inColumn && pkg !in hidden
        val first = drawer.filter(::shown).distinct()
        val pinned = first.toSet()
        val rest = launchable.keys
            .filter { shown(it) && it !in pinned }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { pkg: String -> launchable.getValue(pkg) }.thenBy { it })
        return first + rest
    }

    /** The column with [pkg] added at its end, or [apps] unchanged when it is already there. */
    fun addToColumn(apps: List<String>, pkg: String): List<String> = if (pkg in apps) apps else apps + pkg

    /**
     * The apps that always stay on Home: KevBox Live, KevBox TV and SmartTube, under each package
     * name SmartTube ships as. Remove from Home is never offered for them.
     */
    val LOCKED: Set<String> = setOf(
        "tv.kevbox.live",
        "tv.kevbox",
        "org.smarttube.stable",
        "org.smarttube.beta",
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv.beta",
    )

    /** True when Remove from Home may be offered for [pkg]. */
    fun removable(pkg: String): Boolean = pkg !in LOCKED

    /**
     * The column without [pkg], every copy of it, so it lands back in the drawer. A locked app
     * leaves [apps] unchanged.
     */
    fun removeFromColumn(apps: List<String>, pkg: String): List<String> =
        if (removable(pkg)) apps.filter { it != pkg } else apps

    /** [list] with the item at [from] moved [by] places (negative is earlier), clamped to the ends. */
    fun move(list: List<String>, from: Int, by: Int): List<String> {
        if (from !in list.indices) return list
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
