package cloud.samlo.couch.kevbox.home

import cloud.samlo.couch.data.Programs
import cloud.samlo.couch.data.Tile

/** One row of cards on Home: a preview channel's [tiles] under its [title], the name as published. */
class Shelf(val title: String, val tiles: List<Tile>)

/**
 * Which of an app's preview channels Home shows, at most two. Names differ by language and
 * SmartTube's are not fixed, so each slot looks for a keyword in the name rather than an exact one.
 */
object HomeRows {
    private const val MAX = 2

    private val smartTube = listOf(listOf("recommend"), listOf("history"))

    /** Per app, one keyword list per slot; a name holding any of a slot's keywords fills it. */
    private val slots: Map<String, List<List<String>>> = mapOf(
        "tv.kevbox.live" to listOf(listOf("recent", "récent"), listOf("favori")),
        "tv.kevbox" to listOf(listOf("continue"), listOf("library", "bibliothèque")),
        "org.smarttube.stable" to smartTube,
        "org.smarttube.beta" to smartTube,
        "com.teamsmart.videomanager.tv" to smartTube,
        "com.liskovsoft.smarttubetv.beta" to smartTube,
    )

    /**
     * Up to two of [channels] (one app's, in `Programs.channels` order), in slot order. Keyword
     * matches are placed first, so a slot left without one takes the next channel no other slot
     * claimed instead of the one a later slot asks for by name.
     */
    fun pick(packageName: String, channels: List<Programs.Channel>): List<Programs.Channel> {
        val keywords = slots[packageName].orEmpty()
        val picked = arrayOfNulls<Programs.Channel>(MAX)
        val used = HashSet<Long>()
        keywords.forEachIndexed { slot, words ->
            picked[slot] = channels.firstOrNull { c ->
                c.id !in used && words.any { c.name.contains(it, ignoreCase = true) }
            }?.also { used.add(it.id) }
        }
        val rest = channels.filter { it.id !in used }.iterator()
        for (slot in 0 until MAX) {
            if (picked[slot] == null && rest.hasNext()) picked[slot] = rest.next()
        }
        return picked.filterNotNull()
    }
}
