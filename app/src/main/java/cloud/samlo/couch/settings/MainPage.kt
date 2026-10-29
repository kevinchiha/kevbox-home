package cloud.samlo.couch.settings

import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Accents
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.update.Installer
import cloud.samlo.couch.update.UpdateCheck
import cloud.samlo.couch.update.Versions

/**
 * "Customize": the groups down the left, the focused group's options on the right. Up and down
 * the rail switch groups, Right or OK steps into the options, and Back or Left (on a row that
 * doesn't use it) steps out again. Left/right changes a value in place, OK cycles or opens it, and
 * the focused row explains itself. Rows that only matter for another setting sit indented under
 * it and show only when they apply. KevBox has one layout and no ambient mode, Inputs, Other TVs
 * or remote relay, so its groups hold fewer rows than Couch's.
 */
class MainPage(private val ctx: SettingsContext) : SettingsPage {
    private val o = ctx.options
    private val config get() = ctx.config
    private val rail = Rail(ctx, focusable = true)
    private var current = Group.LOOK

    /** How many accesses were off at the last build, so a grant on return updates the warning. */
    private var off = 0

    override fun build() {
        checkNow()
        off = offCount()
        rail.build(current, if (off > 0) missingWarning() else null)
        rail.listen(onFocus = { if (it != current) show(it) }, onEnter = ::enter)
        // A saved change shows at once in its group's line; the other lines can't have changed.
        o.onSave = { rail.refresh(current) }
        show(current)
    }

    override fun focusStart() = rail.item(current).requestFocus()

    /** Back from another screen: show what changed, and redo the warning once a grant changed it. */
    override fun onResume() {
        // The page just closed saved its own copy; saving this one as it was would undo that.
        ctx.config = Config.load(ctx.files.dir)
        if (off == offCount()) {
            o.rows().forEach { it.refresh() }
            rail.refresh()
            return
        }
        val inPage = inPage()
        val focus = (ctx.activity.currentFocus as? OptionRow)?.label?.text?.toString()
        build()
        if (inPage) refocus(focus) else focusStart()
    }

    /** A Back press that started in the options; its repeats and release are swallowed too. */
    private var backToRail = false

    /**
     * Back from the options returns to their group in the rail, instead of leaving Customize.
     * The whole press is kept, so holding Back doesn't go on to close the page from the rail.
     */
    override fun consumesKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        val first = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (first) backToRail = inPage()
        if (!backToRail) return false
        if (first) rail.item(current).requestFocus()
        if (event.action == KeyEvent.ACTION_UP) backToRail = false
        return true
    }

    private fun inPage() = ctx.activity.currentFocus?.parent == ctx.layout.list

    private fun enter() {
        o.rows().firstOrNull()?.requestFocus()
    }

    /** Fills the page on the right with [group]'s options. */
    private fun show(group: Group) {
        current = group
        rail.select(group)
        ctx.layout.list.removeAllViews()
        ctx.layout.title.text = ctx.str(group.title)
        // No hint here: the options start level with the first group in the rail.
        ctx.layout.hint.visibility = View.GONE
        ctx.layout.title.setPadding(ctx.activity.dp(20), 0, 0, ctx.activity.dp(20))
        when (group) {
            Group.LOOK -> look()
            Group.HOME -> home()
            Group.CLOCK -> clock()
            Group.COUCH -> system()
        }
        // Left from a row that doesn't use it goes back to this group, not to whichever is nearest.
        val back = rail.item(group).id
        val all = o.rows()
        all.forEach { it.nextFocusLeftId = back }
        all.firstOrNull()?.let { it.nextFocusUpId = it.id }
        all.lastOrNull()?.let { it.nextFocusDownId = it.id }
    }

    private fun offCount() = Setup.items.count { !it.isOn(ctx.activity) }

    /** A line above the groups while an access is missing; it opens the permissions page. */
    private fun missingWarning(): RailItem {
        val missing = Setup.items.filterNot { it.isOn(ctx.activity) }
        return RailItem(ctx.activity, R.drawable.ic_group_warning, ctx.activity.resources.getQuantityString(R.plurals.setup_missing, missing.size, missing.size)).apply {
            summary.text = missing.joinToString(", ") { ctx.str(it.title) }
            isFocusable = true
            warn()
            setOnClickListener { open(SettingsActivity.PAGE_ACCESS) }
        }
    }

    /** KevBox: the accent, then the wallpaper behind Index and how dark it gets (no ambient mode). */
    private fun look() {
        o.swatches(
            ctx.str(R.string.accent), ctx.str(R.string.accent_help),
            Accents.all.map { it.hex },
            { config.accent },
            {
                config.accent = it
                Tokens.applyAccent(it)
                // The focus rings here follow the new colour right away.
                o.restyle()
                rail.restyle()
            },
        )
        wallpaper()
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

    private fun wallpaper() {
        o.choice(
            ctx.str(R.string.wallpaper_source), ctx.str(R.string.wallpaper_source_help),
            listOf(
                Option(ctx.str(R.string.wallpaper_daily), "daily"),
                Option(ctx.str(R.string.wallpaper_reddit), REDDIT),
                Option(ctx.str(R.string.wallpaper_gradient), "gradient"),
                Option(ctx.str(R.string.wallpaper_custom), "custom"),
            ),
            { config.wallpaper },
            {
                val was = config.wallpaper == REDDIT
                config.wallpaper = it
                // The subreddit rows come and go with Reddit; other steps keep the page as it is.
                if (was != (it == REDDIT)) rebuild(ctx.str(R.string.wallpaper_source))
            },
        )
        if (config.wallpaper != REDDIT) return
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
    }

    private fun home() {
        o.page(ctx.str(R.string.settings_channels), ctx.str(R.string.settings_channels_help)) { open(SettingsActivity.PAGE_CHANNELS) }
    }

    /** KevBox: no City row; the weather finds the TV by its IP address (an empty `weatherCity`). */
    private fun clock() {
        o.toggle(ctx.str(R.string.clock24h), ctx.str(R.string.clock24h_help), { config.clock24h }, { config.clock24h = it })
        o.toggle(ctx.str(R.string.weather), ctx.str(R.string.weather_help), { config.weather }) {
            config.weather = it
            rebuild(ctx.str(R.string.weather))
        }
        if (config.weather) o.nested {
            o.choice(
                ctx.str(R.string.units), ctx.str(R.string.units_help), listOf(Option("°C", "c"), Option("°F", "f")),
                { if (config.fahrenheit) "f" else "c" }, { config.fahrenheit = it == "f" },
            )
        }
    }

    private fun system() {
        o.page(ctx.str(R.string.group_setup), ctx.str(R.string.setup_page_help), { accessValue() }) { open(SettingsActivity.PAGE_ACCESS) }
        o.toggle(ctx.str(R.string.check_updates), ctx.str(R.string.check_updates_help), { config.checkUpdates }, { config.checkUpdates = it })
        about()
    }

    private fun accessValue(): String {
        val off = Setup.items.count { !it.isOn(ctx.activity) }
        return if (off == 0) ctx.str(R.string.setup_all_on) else ctx.activity.resources.getQuantityString(R.plurals.setup_off, off, off)
    }

    /**
     * The installed version; OK installs a newer one. KevBox: the help says it is checking until
     * [checkNow] answers, and OK installs even while Home's Update chip waits for the silent update.
     * The GPL v3 notice and the source address follow the update line.
     */
    private fun about() {
        val fetcher = Fetcher(ctx.activity)
        val update = UpdateCheck(ctx.activity, fetcher).pending(config)
        val status = update?.let { ctx.activity.getString(R.string.about_update, it.version) }
            ?: ctx.str(if (checking) R.string.kevbox_about_checking else R.string.about_help)
        val help = status + "\n\n" + ctx.str(R.string.kevbox_licence)
        o.link(ctx.str(R.string.about), help, { Versions.installed(ctx.activity) }) {
            if (update == null || Installer.running) return@link
            Toast.makeText(ctx.activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            Installer(ctx.activity, fetcher).install(update) { message ->
                message?.let { Toast.makeText(ctx.activity, it, Toast.LENGTH_LONG).show() }
            }
        }
    }

    /** KevBox: true while [checkNow]'s request runs. */
    private var checking = false

    /** KevBox: true once [checkNow] has started, so a rebuild never asks again. */
    private var checkStarted = false

    /**
     * KevBox: opening Customize asks tv.kevbox.dev at once rather than trusting the daily check
     * (silent update AC-11). The answer is stored, so About and the rail read it through pending().
     */
    private fun checkNow() {
        if (checkStarted || !config.checkUpdates) return
        checkStarted = true
        checking = true
        val loaded = config
        val check = UpdateCheck(ctx.activity, Fetcher(ctx.activity))
        Thread({
            // A failed request leaves the stored answer, so pending() still reads the last one.
            runCatching { check.refresh(loaded, force = true) }
            ctx.activity.runOnUiThread {
                if (ctx.activity.isDestroyed) return@runOnUiThread
                checking = false
                rail.refresh(Group.COUCH)
                if (current == Group.COUCH) reshow()
            }
        }, "kevbox-about-check").start()
    }

    /** KevBox: the group's options again with the focus kept: on its row by title, or in the rail. */
    private fun reshow() {
        val inPage = inPage()
        val focus = (ctx.activity.currentFocus as? OptionRow)?.label?.text?.toString()
        show(current)
        if (inPage) refocus(focus)
    }

    private fun open(page: String) = ctx.activity.startActivity(SettingsActivity.intent(ctx.activity, page))

    /** The group's options again, after a change that adds or removes rows; focus back on [focus]. */
    private fun rebuild(focus: String) = ctx.layout.list.post {
        show(current)
        refocus(focus)
    }

    private fun refocus(title: String?) {
        (o.rows().firstOrNull { it.label.text == title } ?: o.rows().firstOrNull())?.requestFocus()
    }

    private companion object {
        const val REDDIT = "reddit"
    }
}
