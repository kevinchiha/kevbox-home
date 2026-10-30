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
     * Up to two of [channels] (one app's, in `Programs.channels` order), in slot order. An app in
     * [slots] shows only the rows its keywords find: a missing one leaves its slot empty rather
     * than taking another channel, which could be a second copy of a row already shown. Any other
     * app shows its first two channels.
     */
    fun pick(packageName: String, channels: List<Programs.Channel>): List<Programs.Channel> {
        val keywords = slots[packageName] ?: return channels.take(MAX)
        val used = HashSet<Long>()
        return keywords.mapNotNull { words ->
            // Two channels can answer one slot: an app that lost track of its row makes a new one and
            // leaves the old one behind, frozen. The provider numbers channels upward, so the
            // newest, the highest id, is the one the app still fills.
            channels.filter { c ->
                c.id !in used && words.any { c.name.contains(it, ignoreCase = true) }
            }.maxByOrNull { it.id }?.also { used.add(it.id) }
        }
    }
}
