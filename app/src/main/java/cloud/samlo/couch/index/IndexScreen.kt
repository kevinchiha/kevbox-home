package cloud.samlo.couch.index

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.kevbox.drawer.DrawerActivity
import cloud.samlo.couch.kevbox.home.Backdrop
import cloud.samlo.couch.kevbox.home.MoveMode
import cloud.samlo.couch.kevbox.home.TopFocus
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.FocusRow
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.fill

/**
 * The Index layout: the clock and weather top left, apps listed on the left under them, the top
 * buttons top right; on the right, what the selected app offers (the
 * focused program's text over its first channel's cards), its last week of use, and the TV's
 * readings along the bottom.
 *
 * D-pad: up/down walks the list down to More apps, right enters the app's channel row, left (from
 * its first card) or down comes back. Up from the first app or from a card reaches the top
 * buttons, and down from them returns where it came from. KevBox's pieces (backdrop, More apps,
 * Move) live in `kevbox/home`; this class only calls them.
 */
@SuppressLint("ViewConstructor")
class IndexScreen(
    private val activity: Activity,
    private val images: Images,
    onSettings: () -> Unit,
    onUpdate: () -> Unit,
    private val onAppMenu: (Tile) -> Unit,
    private val onProgramMenu: (Card) -> Unit,
) : FrameLayout(activity) {
    val topBar = IndexTopBar(activity, onSettings, onUpdate)
    private val backdrop = Backdrop(activity, images)
    private val list = AppList(activity) { entry -> row(entry) }
    private val topFocus = TopFocus()
    private val move: MoveMode = MoveMode(activity, list, { config }) { held, config -> placed(held, config) }
    private val spotlight = Spotlight(activity)
    private val programs = FocusRow(activity).apply {
        gapDp = IndexTokens.CARD_GAP
        leavesAtStart = true
        setStartPadding(activity.dp(STRIP_START) - activity.dp(Tokens.RING_INSET))
    }
    /** The app's banner, in the channel row's place when the app has no channel. */
    private val banner = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val stats = StatsBar(activity)
    val systemLine = SystemLine(activity)

    private var data: IndexData? = null
    private var config = Config()
    private var look = ""
    private var selectedKey: String? = null
    /** The app whose programs the channel row holds. */
    private var stripKey: String? = null
    /** Placed once, on the first bind; later binds keep focus where it is. */
    private var placedFocus = false
    /** Set while [bind] moves focus itself, so the focus listener doesn't fill the column twice. */
    private var binding = false
    /** The channel row fills once the selection rests, so holding down never builds cards and art. */
    private val fillStrip = Runnable { selectedRow()?.let { fill(it.entry) } }
    /** The figures follow a frame after the press, so the press's own frame stays light. */
    private val statsLater = Runnable { selectedRow()?.let { showStats(it.entry) } }
    /** Cards are added a couple per frame: building them all at once cost a weak TV two frames. */
    private var built = 0
    private val buildMore = Runnable { selectedRow()?.entry?.takeIf { it.key == stripKey }?.let { buildStep(it) } }

    init {
        // The one full-screen layer, under everything: the focused program's art or the wallpaper
        // (the spec's choice, AC-13). The window's flat colour shows until the first image lands.
        addView(backdrop, fill())
        place(list, IndexTokens.LIST_X, IndexTokens.LIST_Y, IndexTokens.LIST_W, IndexTokens.LIST_H)
        place(spotlight, IndexTokens.COLUMN_X, IndexTokens.HEADER_Y, IndexTokens.COLUMN_W, LayoutParams.WRAP_CONTENT)
        // The row starts a little left of the column so a scaled card's ring isn't clipped.
        val cardTop = 10 + Tokens.RING_INSET
        addView(programs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = activity.dp(IndexTokens.COLUMN_X - STRIP_START)
            topMargin = activity.dp(IndexTokens.STRIP_Y - cardTop)
        })
        place(banner, IndexTokens.COLUMN_X, IndexTokens.STRIP_Y, IndexTokens.cardWidth(-1), IndexTokens.CARD_H)
        place(stats, IndexTokens.COLUMN_X, IndexTokens.STATS_Y, IndexTokens.COLUMN_W, LayoutParams.WRAP_CONTENT)
        list.moreApps.setOnFocusChangeListener { _, has -> if (has) onMoreApps() }
        list.moreApps.setOnClickListener { activity.startActivity(DrawerActivity.intent(activity)) }
        addView(systemLine, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = activity.dp(IndexTokens.LIST_X)
            bottomMargin = activity.dp(IndexTokens.FOOTER_BOTTOM) - systemLine.inset
        })
        // Full width: the clock starts at the column's x, the buttons end as far from the right edge.
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = activity.dp(TOP_BAR_Y)
            leftMargin = activity.dp(IndexTokens.LIST_X)
            rightMargin = activity.dp(IndexTokens.LIST_X)
        })
    }

    fun bind(data: IndexData, config: Config) {
        // Binding the loader's order mid-move would snap the moving row back: Move keeps it for later.
        if (move.active) {
            move.hold(data, config)
            return
        }
        this.data = data
        this.config = config
        val rebuild = look != config.look
        look = config.look
        binding = true
        list.bind(data.entries, rebuild) { it.requestFocus() }
        val rows = list.rows()
        val more = list.moreApps
        if (rows.isEmpty()) {
            // A card about to be cleared hands focus to More apps too, not to wherever Android picks.
            if (!placedFocus || !hasFocus() || programs.hasFocus()) more.requestFocus()
            placedFocus = true
            binding = false
            clearColumn()
            if (more.hasFocus()) list.selectMore()
            return
        }
        // The selected app may be gone (hidden, uninstalled): follow focus, which arrange moved.
        val target = rows.firstOrNull { it.entry.key == selectedKey } ?: rows.firstOrNull { it.hasFocus() } ?: rows.first()
        if (target.hasFocus()) list.reveal(rows.indexOf(target))
        if (!placedFocus || !hasFocus()) {
            placedFocus = true
            list.reveal(rows.indexOf(target), animate = false)
            target.requestFocus()
        }
        binding = false
        if (more.hasFocus()) {
            // Focus and the selection stay on More apps (Back from the drawer lands there).
            selectedKey = target.entry.key
            list.selectMore()
            show(target.entry, deferStrip = false)
        } else {
            select(target, refresh = true)
        }
    }

    /** Move's keys, then Back from the channel row or the top pills to the selected app. True when handled. */
    fun consumesKey(event: KeyEvent): Boolean {
        if (move.consumes(event)) return true
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        val focused = activity.currentFocus ?: return false
        if (!programs.isAncestorOf(focused) && !topBar.isAncestorOf(focused)) return false
        if (event.action == KeyEvent.ACTION_UP) (selectedRow() ?: list.moreApps).requestFocus()
        return true
    }

    /** HOME while home: Move places the app, then back to the first app, else More apps. */
    fun reset() {
        move.place()
        val first = list.rows().firstOrNull()
        if (first == null) {
            list.moreApps.requestFocus()
            return
        }
        // Focus first: its listener selects the row, then the jump lands the selection with the list.
        first.requestFocus()
        list.reveal(0, animate = false)
    }

    /** Starts Move for [pkg] in the left column: the row keeps focus and the spotlight says what to do. */
    fun startMove(pkg: String) {
        val row = list.rows().firstOrNull { it.entry.key == pkg } ?: return
        if (!move.start(row)) return
        row.requestFocus()
        select(row, refresh = false)
        spotlight.showApp(row.entry.app, activity.getString(R.string.kevbox_move_hint))
    }

    /** Draws [wallpaper] behind the screen, dimmed by [dim]. Called on every load. */
    fun showBackdrop(wallpaper: String?, dim: String) {
        backdrop.setDim(dim)
        backdrop.setWallpaper(wallpaper)
    }

    /** Leaving (a mapped key opened an app) places a moving app; Back from the drawer lands on More apps. */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            move.place()
            move.forgetKeys()
        } else if (DrawerActivity.takeClosedByBack()) {
            list.moreApps.requestFocus()
        }
    }

    /** Move ended: the held reload, in the placed order, or the app shown normally again. */
    private fun placed(held: IndexData?, config: Config) {
        if (held != null) return bind(held, config)
        selectedRow()?.let { select(it, refresh = true) }
    }

    private fun onMoreApps() {
        list.selectMore()
        backdrop.show(null)
    }

    /** No app left in the column: nothing of a departed app stays on the right. */
    private fun clearColumn() {
        selectedKey = null
        stripKey = null
        removeCallbacks(fillStrip)
        removeCallbacks(buildMore)
        removeCallbacks(statsLater)
        programs.update(emptyList(), "") { error("no cards") }
        banner.visibility = GONE
        spotlight.visibility = INVISIBLE
        stats.visibility = INVISIBLE
        backdrop.show(null)
    }

    private fun row(entry: IndexEntry) = AppRow(activity, entry).apply {
        setOnFocusChangeListener { v, has -> if (has && !binding && !move.active) select(v as AppRow, refresh = false) }
        setOnClickListener { this.entry.app.launch(activity) }
        setOnLongClickListener {
            onAppMenu(this.entry.app)
            true
        }
    }

    /** Makes [row] the selected app and fills the right column with it. */
    private fun select(row: AppRow, refresh: Boolean) {
        val changed = row.entry.key != selectedKey
        selectedKey = row.entry.key
        val inRow = programs.hasFocus()
        list.select(row, inRow)
        if (row.hasFocus()) {
            list.reveal(list.rows().indexOf(row))
            backdrop.show(row.entry.programs.firstOrNull()?.backdropUrl)
        }
        if (changed || refresh) show(row.entry, deferStrip = changed && !refresh)
        else if (!inRow) showProgram(row.entry, null)
    }

    /** Fills the right column with [entry]: its channel row (or banner), the text above it, the week. */
    private fun show(entry: IndexEntry, deferStrip: Boolean) {
        if (data == null) return
        spotlight.visibility = VISIBLE
        stats.visibility = VISIBLE
        removeCallbacks(fillStrip)
        if (deferStrip) {
            // Another app's cards must not linger under this one while the row waits.
            if (stripKey != entry.key) programs.update(emptyList(), "") { error("no cards") }
            postDelayed(fillStrip, STRIP_SETTLE_MS)
        } else {
            fill(entry)
        }
        showProgram(entry, (activity.currentFocus as? Card)?.takeIf { programs.isAncestorOf(it) }?.tile)
        banner.setImageBitmap(entry.app.banner)
        banner.visibility = if (entry.programs.isEmpty() && entry.app.banner != null) VISIBLE else GONE
        removeCallbacks(statsLater)
        if (deferStrip) post(statsLater) else showStats(entry)
    }

    private fun showStats(entry: IndexEntry) {
        val data = data ?: return
        stats.show(entry, data.usageKnown, System.currentTimeMillis(), config.clock24h)
    }

    private fun fill(entry: IndexEntry) {
        removeCallbacks(fillStrip)
        if (stripKey != entry.key) {
            stripKey = entry.key
            programs.forget()
        }
        removeCallbacks(buildMore)
        built = 0
        buildStep(entry)
    }

    private fun buildStep(entry: IndexEntry) {
        // Past the screen's edge nobody sees them arrive, so the rest come in one go.
        val visible = visibleCount(entry.programs)
        built = if (built >= visible) entry.programs.size else minOf(visible, built + CARDS_PER_FRAME)
        update(entry, entry.programs.take(built))
        if (built < entry.programs.size) postOnAnimation(buildMore)
    }

    private fun update(entry: IndexEntry, tiles: List<Tile>) {
        val cardLook = "${entry.key}|$look"
        programs.update(tiles, cardLook) { card(it, cardLook) }
    }

    /** How many cards reach the right edge of the screen, plus one to glide in. */
    private fun visibleCount(tiles: List<Tile>): Int {
        var x = 0
        tiles.forEachIndexed { i, tile ->
            x += IndexTokens.cardWidth(tile.imageRatio) + IndexTokens.CARD_GAP
            if (x > SCREEN_W - IndexTokens.COLUMN_X) return i + 2
        }
        return tiles.size
    }

    /** The text for [program], or the app's first one, or the app itself when it has no channel. */
    private fun showProgram(entry: IndexEntry, program: Tile?) {
        val tile = program ?: entry.programs.firstOrNull()
        if (tile != null) {
            spotlight.showProgram(entry.channelTitle.orEmpty(), tile)
        } else {
            spotlight.showApp(entry.app, activity.getString(R.string.index_no_channel, entry.app.label))
        }
    }

    private fun card(tile: Tile, cardLook: String) = Card(
        activity, tile, IndexTokens.cardWidth(tile.imageRatio), IndexTokens.CARD_H, images,
        showLabel = true, look = cardLook, onFocused = { onProgramFocused(it) },
        radiusDp = IndexTokens.CARD_RADIUS, thinRing = true,
    ).apply {
        isLongClickable = true
        setOnLongClickListener {
            onProgramMenu(this)
            true
        }
    }

    private fun onProgramFocused(card: Card) {
        val row = selectedRow() ?: return
        list.select(row, inRow = true)
        showProgram(row.entry, card.tile)
        backdrop.show(card.tile.backdropUrl)
    }

    private fun selectedRow() = list.rows().firstOrNull { it.entry.key == selectedKey }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val row = selectedRow()
        val inStrip = focused != null && programs.isAncestorOf(focused)
        val inTop = focused != null && topBar.isAncestorOf(focused)
        return when {
            focused is AppRow -> {
                val rows = list.rows()
                val i = rows.indexOf(focused)
                when (direction) {
                    FOCUS_UP -> rows.getOrNull(i - 1) ?: topFocus.up(focused, topBar.firstButton()) ?: focused
                    FOCUS_DOWN -> rows.getOrNull(i + 1) ?: list.moreApps
                    FOCUS_RIGHT -> {
                        // Right before the row has filled: fill it now.
                        if (stripKey != focused.entry.key) fill(focused.entry)
                        // KevBox: an app with no channel has no cards, so Right goes to the first
                        // top button instead of nowhere; Down from there returns to this row.
                        programs.cards().firstOrNull() ?: topFocus.up(focused, topBar.firstButton()) ?: focused
                    }
                    else -> focused
                }
            }
            focused === list.moreApps -> if (direction != FOCUS_UP) focused else {
                list.rows().lastOrNull() ?: topFocus.up(focused, topBar.firstButton()) ?: focused
            }
            inStrip -> when (direction) {
                // The spec's call: Up from a card goes to the buttons, not out of the strip.
                FOCUS_UP -> topFocus.up(focused, topBar.firstButton()) ?: focused
                FOCUS_DOWN -> leaveStrip(row) ?: focused
                FOCUS_LEFT -> if (programs.cards().firstOrNull() === focused) leaveStrip(row) ?: focused else super.focusSearch(focused, direction)
                else -> super.focusSearch(focused, direction)
            }
            inTop -> if (direction == FOCUS_DOWN) topFocus.down(row, list.moreApps) ?: focused else super.focusSearch(focused, direction)?.takeIf { topBar.isAncestorOf(it) } ?: focused
            else -> super.focusSearch(focused, direction)
        }
    }

    /** Back to the selected app; its focus listener puts the panel back on the first program. */
    private fun leaveStrip(row: AppRow?): View? = row

    private fun View.isAncestorOf(view: View): Boolean {
        var p: View? = view
        while (p != null) {
            if (p === this) return true
            p = p.parent as? View
        }
        return false
    }

    private fun place(view: View, x: Int, y: Int, w: Int, h: Int) {
        addView(view, LayoutParams(if (w > 0) activity.dp(w) else w, if (h > 0) activity.dp(h) else h).apply {
            leftMargin = activity.dp(x)
            topMargin = activity.dp(y)
        })
    }

    private companion object {
        /** Where the channel row's clipping starts, left of the column: room for a focused card's ring. */
        const val STRIP_START = 16
        const val TOP_BAR_Y = 23
        const val STRIP_SETTLE_MS = 160L
        const val CARDS_PER_FRAME = 2
        const val SCREEN_W = 960
    }
}
