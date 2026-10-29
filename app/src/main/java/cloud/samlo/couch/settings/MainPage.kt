package cloud.samlo.couch.settings

import android.widget.Toast
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Accents
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.update.Installer
import cloud.samlo.couch.update.UpdateCheck
import cloud.samlo.couch.update.Versions

/**
 * "Customize": every option on one TV-friendly list. Left/right changes a value in place,
 * OK cycles or opens it, and the panel on the right explains the focused option. KevBox has one
 * layout, so the list is fixed: Setup, look, wallpaper, channels, clock and weather, updates.
 */
class MainPage(private val ctx: SettingsContext) : SettingsPage {
    private val o = ctx.options
    private val config get() = ctx.config

    override fun build() {
        setup()
        look()
        wallpaper()
        home()
        clock()
        system()
    }

    /** Back from Android's settings: show what was just granted. */
    override fun onResume() = o.rows().forEach { it.refresh() }

    private fun setup() {
        o.header(ctx.str(R.string.group_setup))
        for (item in Setup.items) {
            o.link(ctx.str(item.title), ctx.str(item.help), { ctx.str(if (item.isOn(ctx.activity)) R.string.yes else R.string.setup_turn_on) }) {
                item.open(ctx.activity)
            }
        }
    }

    private fun look() {
        o.header(ctx.str(R.string.group_look))
        o.choice(
            ctx.str(R.string.accent), ctx.str(R.string.accent_help),
            Accents.all.map { Option(ctx.str(it.label), it.hex) },
            { config.accent },
            {
                config.accent = it
                Tokens.applyAccent(it)
                // The focus ring here follows the new colour right away.
                o.restyle()
            },
        )
    }

    private fun wallpaper() {
        o.header(ctx.str(R.string.group_wallpaper))
        o.choice(
            ctx.str(R.string.wallpaper_source), ctx.str(R.string.wallpaper_source_help),
            listOf(
                Option(ctx.str(R.string.wallpaper_daily), "daily"),
                Option(ctx.str(R.string.wallpaper_reddit), "reddit"),
                Option(ctx.str(R.string.wallpaper_gradient), "gradient"),
                Option(ctx.str(R.string.wallpaper_custom), "custom"),
            ),
            { config.wallpaper }, { config.wallpaper = it },
        )
        o.text(
            ctx.str(R.string.subreddit), ctx.str(R.string.subreddit_help), Config.DEFAULT_SUBREDDIT,
            { "r/" + config.subreddit }, { config.subreddit = it.removePrefix("r/").ifEmpty { Config.DEFAULT_SUBREDDIT } },
        )
        o.choice(
            ctx.str(R.string.wallpaper_hours), ctx.str(R.string.wallpaper_hours_help),
            listOf(
                Option(ctx.str(R.string.hours_1), "1"),
                Option(ctx.str(R.string.hours_3), "3"),
                Option(ctx.str(R.string.hours_6), "6"),
                Option(ctx.str(R.string.hours_24), "24"),
            ),
            { config.wallpaperHours.toString() }, { config.wallpaperHours = it.toInt() },
        )
        o.choice(
            ctx.str(R.string.dim), ctx.str(R.string.dim_help),
            listOf(
                Option(ctx.str(R.string.dim_light), "light"),
                Option(ctx.str(R.string.dim_medium), "medium"),
                Option(ctx.str(R.string.dim_strong), "strong"),
            ),
            { config.dim }, { config.dim = it },
        )
    }

    private fun home() {
        o.header(ctx.str(R.string.group_home))
        o.link(ctx.str(R.string.settings_channels), ctx.str(R.string.settings_channels_help)) {
            ctx.activity.startActivity(SettingsActivity.intent(ctx.activity, SettingsActivity.PAGE_CHANNELS))
        }
    }

    /** No City row: the weather finds the TV by its IP address (an empty `weatherCity`). */
    private fun clock() {
        o.header(ctx.str(R.string.group_clock))
        o.toggle(ctx.str(R.string.clock24h), ctx.str(R.string.clock24h_help), { config.clock24h }, { config.clock24h = it })
        o.toggle(ctx.str(R.string.weather), ctx.str(R.string.weather_help), { config.weather }, { config.weather = it })
        o.choice(
            ctx.str(R.string.units), "", listOf(Option("°C", "c"), Option("°F", "f")),
            { if (config.fahrenheit) "f" else "c" }, { config.fahrenheit = it == "f" },
        )
    }

    private fun system() {
        o.header(ctx.str(R.string.group_system))
        o.toggle(ctx.str(R.string.check_updates), ctx.str(R.string.check_updates_help), { config.checkUpdates }, { config.checkUpdates = it })
        about()
    }

    /**
     * The installed version. Opening the page asks tv.kevbox.dev at once rather than trusting the
     * daily check (silent update AC-11), and OK installs a newer version even while Home's Update
     * chip waits for the silent update.
     */
    private fun about() {
        val fetcher = Fetcher(ctx.activity)
        val check = UpdateCheck(ctx.activity, fetcher)
        var update = check.pending(config)
        var checking = config.checkUpdates
        val help = {
            update?.let { ctx.activity.getString(R.string.about_update, it.version) }
                ?: ctx.str(if (checking) R.string.kevbox_about_checking else R.string.about_help)
        }
        val row = o.link(ctx.str(R.string.about), help(), { Versions.installed(ctx.activity) }) {
            val release = update ?: return@link
            if (Installer.running) return@link
            Toast.makeText(ctx.activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            Installer(ctx.activity, fetcher).install(release) { message ->
                message?.let { Toast.makeText(ctx.activity, it, Toast.LENGTH_LONG).show() }
            }
        }
        // The row keeps the help it was built with, so the panel follows the check from here.
        val couch = row.onFocusChangeListener
        row.setOnFocusChangeListener { view, focused ->
            couch?.onFocusChange(view, focused)
            if (focused) ctx.layout.help.text = help()
        }
        if (!checking) return
        val loaded = config
        Thread({
            // A failed request leaves the stored answer, so pending() still reads the last one.
            val found = try {
                check.refresh(loaded, force = true)
                Result.success(check.pending(loaded))
            } catch (e: Exception) {
                Result.failure(e)
            }
            ctx.activity.runOnUiThread {
                if (ctx.activity.isDestroyed) return@runOnUiThread
                found.onSuccess { update = it }
                checking = false
                if (row.isFocused) ctx.layout.help.text = help()
            }
        }, "kevbox-about-check").start()
    }
}
