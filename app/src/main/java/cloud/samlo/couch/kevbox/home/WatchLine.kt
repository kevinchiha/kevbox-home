package cloud.samlo.couch.kevbox.home

import android.content.Context
import cloud.samlo.couch.R
import java.util.Locale

/** Where a program stands, from its TV provider row; null fields are unknown. */
data class Watch(
    val season: String?,
    val episode: String?,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * The line under a program's title in Home's text block: "S1 E2 · The Rescue · 24 min left", or
 * as much of it as the app publishes. Null when there is nothing to say.
 */
object WatchLine {
    private const val SEPARATOR = " · "

    /**
     * Below this a duration is not a real running time: KevBox TV writes 100 s with a matching
     * position when it only knows a percentage, so the launcher can draw the progress bar.
     */
    private const val MIN_REAL_DURATION_MS = 5 * 60_000L

    /** Resource lookups passed in, so [text] stays a pure function. */
    class Words(
        val seasonEpisode: (season: String, episode: String) -> String,
        val episode: (episode: String) -> String,
        val minutesLeft: (minutes: Long) -> String,
        val hoursLeft: (hours: Long, minutes: String) -> String,
    )

    fun text(watch: Watch?, words: Words): String? {
        watch ?: return null
        val season = watch.season?.takeIf { it.isNotBlank() }
        val episode = watch.episode?.takeIf { it.isNotBlank() }
        val number = when {
            season != null && episode != null -> words.seasonEpisode(season, episode)
            episode != null -> words.episode(episode)
            else -> null
        }
        val left = if (watch.positionMs > 0 && watch.durationMs >= MIN_REAL_DURATION_MS) left(watch, words) else null
        return listOfNotNull(number, watch.episodeTitle?.takeIf { it.isNotBlank() }, left)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(SEPARATOR)
    }

    /** Whole minutes left, at least one. */
    private fun left(watch: Watch, words: Words): String {
        val minutes = ((watch.durationMs - watch.positionMs) / 60_000).coerceAtLeast(1)
        return if (minutes < 60) words.minutesLeft(minutes) else words.hoursLeft(minutes / 60, String.format(Locale.ENGLISH, "%02d", minutes % 60))
    }

    /**
     * [Words] from the strings' raw templates, filled in with English figures: getString with
     * arguments uses the TV's language, and an Arabic box would show Arabic-Indic digits (AC-38).
     */
    fun words(seasonEpisode: String, episode: String, minutesLeft: String, hoursLeft: String) = Words(
        seasonEpisode = { s, e -> String.format(Locale.ENGLISH, seasonEpisode, s, e) },
        episode = { e -> String.format(Locale.ENGLISH, episode, e) },
        minutesLeft = { m -> String.format(Locale.ENGLISH, minutesLeft, m) },
        hoursLeft = { h, m -> String.format(Locale.ENGLISH, hoursLeft, h, m) },
    )

    /** [text] with words from [context]'s strings. Main thread. */
    fun of(context: Context, watch: Watch?): String? = text(
        watch,
        words(
            context.getString(R.string.program_season_episode),
            context.getString(R.string.program_episode),
            context.getString(R.string.program_remaining_minutes),
            context.getString(R.string.program_remaining_hours),
        ),
    )
}
