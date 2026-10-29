package cloud.samlo.couch.settings

import cloud.samlo.couch.R
import cloud.samlo.couch.data.Accents
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.update.UpdateCheck
import cloud.samlo.couch.update.Versions

/**
 * The groups of Customize, listed in the rail. Each says in one line how it is set, so the rail
 * reads as a summary of the whole page.
 */
enum class Group(val title: Int, val icon: Int) {
    LOOK(R.string.group_look, R.drawable.ic_group_look),
    HOME(R.string.group_home, R.drawable.ic_group_home),
    CLOCK(R.string.group_clock, R.drawable.ic_group_clock),
    COUCH(R.string.group_system, R.drawable.ic_group_couch),
    ;

    fun summary(ctx: SettingsContext): String {
        val c = ctx.activity
        val config = ctx.config
        return when (this) {
            // KevBox: no ambient mode, so the wallpaper the Index backdrop shows instead.
            LOOK -> listOf(
                ctx.str((Accents.all.firstOrNull { it.hex.equals(config.accent, ignoreCase = true) } ?: Accents.all.first()).label),
                ctx.str(wallpaperLabel(config.wallpaper)),
            )
            // KevBox: no Show or hide apps page, so the group names its one page.
            HOME -> listOf(ctx.str(R.string.settings_channels))
            CLOCK -> listOf(
                ctx.str(if (config.clock24h) R.string.summary_clock_24 else R.string.summary_clock_12),
                ctx.str(if (config.weather) R.string.summary_weather_on else R.string.summary_weather_off),
            )
            COUCH -> {
                val update = UpdateCheck(c, Fetcher(c)).pending(config)
                listOf(update?.let { c.getString(R.string.summary_update, it.version) } ?: c.getString(R.string.summary_version, Versions.installed(c)))
            }
        }.joinToString(", ")
    }

    /** KevBox: the wallpaper source's name, as Customize lists it. */
    private fun wallpaperLabel(value: String) = when (value) {
        "reddit" -> R.string.wallpaper_reddit
        "gradient" -> R.string.wallpaper_gradient
        "custom" -> R.string.wallpaper_custom
        else -> R.string.wallpaper_daily
    }

    companion object {
        /** The group a page of settings belongs to; null for the pages that stand on their own. */
        fun of(page: String?): Group? = when (page) {
            SettingsActivity.PAGE_CHANNELS -> HOME
            SettingsActivity.PAGE_ACCESS -> COUCH
            else -> null
        }
    }
}
