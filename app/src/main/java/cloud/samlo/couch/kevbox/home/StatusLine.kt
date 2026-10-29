package cloud.samlo.couch.kevbox.home

import android.content.Context
import cloud.samlo.couch.R
import cloud.samlo.couch.index.Formats
import cloud.samlo.couch.index.IndexEntry
import java.util.Locale

/** Where a program stands, from its TV provider row; null fields are unknown. */
data class Watch(val title: String, val season: String?, val episode: String?, val positionMs: Long, val durationMs: Long)

/**
 * The one line under an app's name in the left column: where its show stands when the app
 * publishes a playback position, else when the app was last opened.
 */
object StatusLine {
    private const val SEPARATOR = " · "

    /** Resource lookups passed in, so [text] stays a pure function. */
    class Words(
        val seasonEpisode: (season: String, episode: String) -> String,
        val episode: (episode: String) -> String,
        val minutesLeft: (minutes: Long) -> String,
        val hoursLeft: (hours: Long, minutes: String) -> String,
        val opened: (lastOpened: String) -> String,
    )

    /** The row's one line, or null for no line. [lastOpened] is Formats.lastOpened's text, null when unused this week. */
    fun text(watch: Watch?, lastOpened: String?, words: Words): String? {
        if (watch != null && watch.positionMs > 0 && watch.durationMs > 0) {
            val season = watch.season?.takeIf { it.isNotBlank() }
            val episode = watch.episode?.takeIf { it.isNotBlank() }
            val number = when {
                season != null && episode != null -> words.seasonEpisode(season, episode)
                episode != null -> words.episode(episode)
                else -> null
            }
            return listOfNotNull(watch.title, number, left(watch, words)).joinToString(SEPARATOR)
        }
        return lastOpened?.let(words.opened)
    }

    /** Whole minutes left, at least one, as ProgramTile's card meta counts them. */
    private fun left(watch: Watch, words: Words): String {
        val minutes = ((watch.durationMs - watch.positionMs) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) words.minutesLeft(minutes) else words.hoursLeft(minutes / 60, String.format(Locale.ENGLISH, "%02d", minutes % 60))
    }

    /**
     * [Words] from the strings' raw templates, filled in with English figures: getString with
     * arguments uses the TV's language, and an Arabic box would show Arabic-Indic digits (AC-38).
     */
    fun words(seasonEpisode: String, episode: String, minutesLeft: String, hoursLeft: String, opened: String) = Words(
        seasonEpisode = { s, e -> String.format(Locale.ENGLISH, seasonEpisode, s, e) },
        episode = { e -> String.format(Locale.ENGLISH, episode, e) },
        minutesLeft = { m -> String.format(Locale.ENGLISH, minutesLeft, m) },
        hoursLeft = { h, m -> String.format(Locale.ENGLISH, hoursLeft, h, m) },
        opened = { at -> String.format(Locale.ENGLISH, opened, at) },
    )

    /** [text] for an entry, resolving words and the last-opened text from [context]. Main thread. */
    fun of(context: Context, entry: IndexEntry, now: Long, clock24h: Boolean): String? {
        val words = words(
            context.getString(R.string.program_season_episode),
            context.getString(R.string.program_episode),
            context.getString(R.string.program_remaining_minutes),
            context.getString(R.string.program_remaining_hours),
            context.getString(R.string.kevbox_opened),
        )
        val lastOpened = Formats.lastOpened(context, entry.usage?.lastUsed ?: 0L, now, clock24h)
        return text(entry.programs.firstOrNull()?.watch, lastOpened, words)
    }
}
