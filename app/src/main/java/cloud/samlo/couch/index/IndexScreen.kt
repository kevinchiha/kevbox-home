package cloud.samlo.couch.index

import android.annotation.SuppressLint
import android.app.Activity
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.kevbox.drawer.DrawerActivity
import cloud.samlo.couch.kevbox.home.AppTile
import cloud.samlo.couch.kevbox.home.AppTiles
import cloud.samlo.couch.kevbox.home.Backdrop
import cloud.samlo.couch.kevbox.home.MoveMode
import cloud.samlo.couch.kevbox.home.TopFocus
import cloud.samlo.couch.kevbox.home.WatchLine
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.FocusRow
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.fill

/**
 * The Index layout: the clock and weather top left, the top buttons top right; the apps as icon
 * tiles along the bottom, then More apps; above them, what the selected app offers: the focused
 * program's text on the left, and up to two labelled rows of its cards on the right. The TV's
 * readings run along the very bottom.
 *
 * D-pad: left/right walks the tiles, each one swapping the text and both rows to its app. Up from
 * a tile enters its lowest row, then the upper row, then the top buttons; down walks back the
 * same way, and from the buttons returns where Up came from. Back from a card or a button returns
 * to the selected tile. KevBox's pieces (backdrop, tiles, Move) live in `kevbox/home`; this class
 * only calls them.
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
    private val tiles = AppTiles(activity) { entry -> tile(entry) }
    private val topFocus = TopFocus()
    private val move: MoveMode = MoveMode(activity, tiles, { config }) { held, config -> placed(held, config) }
    private val spotlight = Spotlight(activity)
    /** The upper row, then the lower one; an app's first shelf fills the upper. */
    private val lanes = listOf(Lane(IndexTokens.ROW_1_LABEL_Y), Lane(IndexTokens.ROW_2_LABEL_Y))
    val systemLine = SystemLine(activity)

    private var data: IndexData? = null
    private var config = Config()
    private var look = ""
    private var selectedKey: String? = null
    /** More apps is the chosen tile: the text says so and no row shows. */
    private var moreChosen = false
    /** The app whose cards the rows hold; null while they are empty. */
    private var rowsKey: String? = null
    /** Placed once, on the first bind; later binds keep focus where it is. */
    private var placedFocus = false
    /** Set while [bind] moves focus itself, so the focus listeners don't fill the rows twice. */
    private var binding = false
    /** The rows fill once the selection rests, so holding left or right never builds cards and art. */
    private val fillRows = Runnable { selectedTile()?.let { fill(it.entry) } }
    /** Cards are added a couple per frame: building them all at once cost a weak TV two frames. */
    private val built = IntArray(lanes.size)
    private val buildMore = Runnable { selectedTile()?.entry?.takeIf { it.key == rowsKey }?.let { buildStep(it, first = false) } }

    /** One row of cards and the small label above it, the channel's name as published. */
    private inner class Lane(labelY: Int) {
        val label = IndexText(activity, 12f, 600, Tokens.textMuted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = INVISIBLE
        }
        val row = FocusRow(activity).apply {
            gapDp = IndexTokens.CARD_GAP
            hidesPassed = true
            setStartPadding(activity.dp(STRIP_START) - activity.dp(Tokens.RING_INSET))
        }
        val cardsY = labelY + IndexTokens.ROW_CARDS_DY
        val labelParams = LayoutParams(activity.dp(SCREEN_W - IndexTokens.ROWS_MIN_X - IndexTokens.EDGE_X), LayoutParams.WRAP_CONTENT).apply {
            leftMargin = activity.dp(IndexTokens.ROWS_MIN_X)
            topMargin = activity.dp(labelY) - label.inset
        }
    }

    init {
        // The one full-screen layer, under everything: the focused program's art or the wallpaper
        // (the spec's choice, AC-13). The window's flat colour shows until the first image lands.
        addView(backdrop, fill())
        place(spotlight, IndexTokens.EDGE_X, IndexTokens.TEXT_Y, IndexTokens.TEXT_W, LayoutParams.WRAP_CONTENT)
        lanes.forEach { addView(it.label, it.labelParams) }
        // Labels and rows sit at ROWS_MIN_X and slide right past the text block's text ([followText]).
        // Each row starts a little left of its label so a scaled card's ring isn't clipped.
        val cardTop = 10 + Tokens.RING_INSET
        lanes.forEach { lane ->
            addView(lane.row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = activity.dp(IndexTokens.ROWS_MIN_X - STRIP_START)
                topMargin = activity.dp(lane.cardsY - cardTop)
            })
        }
        // The plates' band starts at TILES_Y: a tile stands RING_INSET outside its plate, and the
        // bar pads its tiles for a grown one's ring.
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(IndexTokens.TILES_Y - Tokens.RING_INSET) - tiles.paddingTop
        })
        tiles.moreApps.setOnFocusChangeListener { _, has -> if (has && !binding) onMoreApps() }
        tiles.moreApps.setOnClickListener { activity.startActivity(DrawerActivity.intent(activity)) }
        addView(systemLine, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = activity.dp(IndexTokens.EDGE_X)
            bottomMargin = activity.dp(IndexTokens.FOOTER_BOTTOM) - systemLine.inset
        })
        // Full width: the clock starts at the text block's x, the buttons end as far from the right edge.
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = activity.dp(TOP_BAR_Y)
            leftMargin = activity.dp(IndexTokens.EDGE_X)
            rightMargin = activity.dp(IndexTokens.EDGE_X)
        })
    }

    fun bind(data: IndexData, config: Config) {
        // Binding the loader's order mid-move would snap the moving tile back: Move keeps it for later.
        if (move.active) {
            move.hold(data, config)
            return
        }
        this.data = data
        this.config = config
        // The accent is the one setting that changes how tiles and cards are drawn.
        val rebuild = look != config.accent
        look = config.accent
        binding = true
        tiles.bind(data.entries, rebuild) { it.requestFocus() }
        val all = tiles.tiles()
        val more = tiles.moreApps
        if (all.isEmpty()) {
            // A card about to be cleared hands focus to More apps too, not to wherever Android picks.
            val focused = activity.currentFocus
            if (!placedFocus || !hasFocus() || (focused != null && inRows(focused))) more.requestFocus()
            placedFocus = true
            binding = false
            onMoreApps()
            return
        }
        // The selected app may be gone (hidden, uninstalled): follow focus, which bind moved.
        val target = all.firstOrNull { it.entry.key == selectedKey } ?: all.firstOrNull { it.hasFocus() } ?: all.first()
        if (!placedFocus || !hasFocus()) {
            placedFocus = true
            target.requestFocus()
        }
        binding = false
        // Focus and the choice stay on More apps (Back from the drawer lands there), also while
        // focus is up in the buttons above it.
        if (more.hasFocus() || (moreChosen && all.none { it.hasFocus() })) onMoreApps() else select(target, refresh = true)
    }

    /** Move's keys, then Back from the cards or the top buttons to the selected tile. True when handled. */
    fun consumesKey(event: KeyEvent): Boolean {
        if (move.consumes(event)) return true
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        val focused = activity.currentFocus ?: return false
        if (!inRows(focused) && !topBar.isAncestorOf(focused)) return false
        if (event.action == KeyEvent.ACTION_UP) chosen().requestFocus()
        return true
    }

    /** HOME while home: Move places the app, then focus goes to the first app tile, else More apps. */
    fun reset() {
        move.place()
        (tiles.tiles().firstOrNull() ?: tiles.moreApps).requestFocus()
    }

    /** Starts Move for [pkg]'s tile: the tile keeps focus and the text block says what to do. */
    fun startMove(pkg: String) {
        val tile = tiles.tiles().firstOrNull { it.entry.key == pkg } ?: return
        if (!move.start(tile)) return
        tile.requestFocus()
        // The long press that opened the menu left the tile at press scale.
        tile.restyle()
        select(tile, refresh = false)
        spotlight.showApp(tile.entry.app.label, activity.getString(R.string.kevbox_move_hint_tiles))
        followText()
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
            tiles.moreApps.requestFocus()
        }
    }

    /** Move ended: the held reload, in the placed order, or the app shown normally again. */
    private fun placed(held: IndexData?, config: Config) {
        if (held != null) return bind(held, config)
        selectedTile()?.let { select(it, refresh = true) }
    }

    /** More apps chosen: the wallpaper, "More apps" as the title, and no rows. */
    private fun onMoreApps() {
        moreChosen = true
        selectedKey = null
        tiles.select(null)
        clearRows()
        spotlight.showApp(activity.getString(R.string.kevbox_more_apps), null)
        followText()
        backdrop.show(null)
    }

    /** Empties both rows and their labels, so nothing of the last app lingers. */
    private fun clearRows() {
        removeCallbacks(fillRows)
        removeCallbacks(buildMore)
        rowsKey = null
        lanes.forEach { lane ->
            lane.row.update(emptyList(), "") { error("no cards") }
            lane.label.visibility = INVISIBLE
        }
    }

    private fun tile(entry: IndexEntry) = AppTile(activity, entry).apply {
        setOnFocusChangeListener { v, has -> if (has && !binding && !move.active) select(v as AppTile, refresh = false) }
        setOnClickListener { this.entry.app.launch(activity) }
        setOnLongClickListener {
            onAppMenu(this.entry.app)
            true
        }
    }

    /** Makes [tile] the selected app and fills the text block and the rows with it. */
    private fun select(tile: AppTile, refresh: Boolean) {
        val changed = tile.entry.key != selectedKey
        selectedKey = tile.entry.key
        moreChosen = false
        tiles.select(tile)
        if (tile.hasFocus()) backdrop.show(tile.entry.programs.firstOrNull()?.backdropUrl)
        if (changed || refresh) show(tile.entry, deferRows = changed && !refresh)
        else if (focusedCard() == null) showProgram(tile.entry, null)
    }

    /** Fills the screen with [entry]: the row labels now, its cards now or once the selection rests, the text. */
    private fun show(entry: IndexEntry, deferRows: Boolean) {
        if (data == null) return
        removeCallbacks(fillRows)
        if (deferRows) {
            // Another app's cards must not linger under this one while the rows wait (AC-6).
            if (rowsKey != entry.key) clearRows()
            postDelayed(fillRows, STRIP_SETTLE_MS)
        } else {
            fill(entry)
        }
        lanes.forEachIndexed { i, lane ->
            val shelf = entry.shelves.getOrNull(i)
            val title = shelf?.title.orEmpty()
            if (lane.label.text.toString() != title) lane.label.text = title
            // Invisible, not gone: a focus move never triggers a layout pass.
            lane.label.visibility = if (shelf != null) VISIBLE else INVISIBLE
        }
        showProgram(entry, focusedCard()?.tile)
    }

    private fun fill(entry: IndexEntry) {
        removeCallbacks(fillRows)
        val reload = rowsKey == entry.key
        if (!reload) {
            rowsKey = entry.key
            lanes.forEach { it.row.forget() }
        }
        removeCallbacks(buildMore)
        // A reload of the same app starts from the cards already shown, not from a couple: a row
        // cut short under its focused card would send focus back to the first one.
        lanes.forEachIndexed { i, lane -> built[i] = if (reload) kept(lane, entry.shelves.getOrNull(i)?.tiles.orEmpty()) else 0 }
        buildStep(entry, first = true)
    }

    /** How many of [programs] a reload builds at once in [lane]: the cards it shows, and through its focused card's new place. */
    private fun kept(lane: Lane, programs: List<Tile>): Int {
        val focusedKey = focusedCard()?.takeIf { lane.row.isAncestorOf(it) }?.tile?.key
        val at = programs.indexOfFirst { it.key == focusedKey }
        return maxOf(lane.row.cards().size, at + 1)
    }

    /** Adds the next cards to each row; the [first] step also clears rows the app has no shelf for. */
    private fun buildStep(entry: IndexEntry, first: Boolean) {
        // The couple per frame is shared, so two rows grow together at the old pace.
        val shown = entry.shelves.size.coerceIn(1, lanes.size)
        val step = (CARDS_PER_FRAME / shown).coerceAtLeast(1)
        var pending = false
        lanes.forEachIndexed { i, lane ->
            val programs = entry.shelves.getOrNull(i)?.tiles.orEmpty()
            // Past the screen's edge nobody sees them arrive, so the rest come in one go.
            val visible = visibleCount(programs)
            val next = if (built[i] >= visible) programs.size else minOf(visible, built[i] + step)
            if (first || next != built[i]) update(lane, entry, programs.take(next))
            built[i] = next
            if (next < programs.size) pending = true
        }
        if (pending) postOnAnimation(buildMore)
    }

    private fun update(lane: Lane, entry: IndexEntry, programs: List<Tile>) {
        // A row about to empty (the app dropped that channel) hands focus to the tile first, not
        // to wherever Android picks.
        if (programs.isEmpty() && lane.row.hasFocus()) chosen().requestFocus()
        val cardLook = "${entry.key}|$look"
        lane.row.update(programs, cardLook) { card(it, cardLook) }
    }

    /** How many cards reach the right edge of the screen, plus one to glide in. */
    private fun visibleCount(programs: List<Tile>): Int {
        var x = 0
        programs.forEachIndexed { i, tile ->
            x += IndexTokens.cardWidth(tile.imageRatio) + IndexTokens.CARD_GAP
            if (x > SCREEN_W - IndexTokens.ROWS_MIN_X) return i + 2
        }
        return programs.size
    }

    /** The text for [program], or the app's first one, or the app's name alone when it has no rows. */
    private fun showProgram(entry: IndexEntry, program: Tile?) {
        val tile = program ?: entry.programs.firstOrNull()
        if (tile != null) {
            spotlight.showProgram(entry.app.label, tile, WatchLine.of(activity, tile.watch))
        } else {
            spotlight.showApp(entry.app.label, null)
        }
        followText()
    }

    /**
     * Slides the labels and rows to start [IndexTokens.TEXT_GAP] past the text block's text, so a
     * short title leaves no empty band; a long one keeps them at [IndexTokens.ROWS_X]. Kevin's
     * 2026-10-30 ask, on every card move. Translation only, so no layout pass.
     */
    private fun followText() {
        val min = activity.dp(IndexTokens.ROWS_MIN_X)
        val x = (activity.dp(IndexTokens.EDGE_X) + spotlight.textWidth() + activity.dp(IndexTokens.TEXT_GAP))
            .coerceIn(min, activity.dp(IndexTokens.ROWS_X))
        val shift = (x - min).toFloat()
        lanes.forEach { lane ->
            for (view in listOf(lane.label, lane.row)) {
                if (view.translationX == shift) continue
                view.animate().translationX(shift).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
            }
        }
    }

    // No label under the cards: two rows must fit above the tiles, and the text block names the
    // focused card.
    private fun card(tile: Tile, cardLook: String) = Card(
        activity, tile, IndexTokens.cardWidth(tile.imageRatio), IndexTokens.CARD_H, images,
        showLabel = false, look = cardLook, onFocused = { onProgramFocused(it) },
        radiusDp = IndexTokens.CARD_RADIUS, thinRing = true,
    ).apply {
        isLongClickable = true
        setOnLongClickListener {
            onProgramMenu(this)
            true
        }
    }

    private fun onProgramFocused(card: Card) {
        val tile = selectedTile() ?: return
        showProgram(tile.entry, card.tile)
        backdrop.show(card.tile.backdropUrl)
    }

    private fun selectedTile(): AppTile? = if (moreChosen) null else tiles.tiles().firstOrNull { it.entry.key == selectedKey }

    /** Where Back and Down from the buttons land: the selected tile, else More apps. */
    private fun chosen(): View = selectedTile() ?: tiles.moreApps

    private fun focusedCard(): Card? = (activity.currentFocus as? Card)?.takeIf { inRows(it) }

    private fun inRows(view: View) = lanes.any { it.row.isAncestorOf(view) }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val lane = if (focused == null) -1 else lanes.indexOfFirst { it.row.isAncestorOf(focused) }
        val inTop = focused != null && topBar.isAncestorOf(focused)
        return when {
            focused is AppTile -> {
                val all = tiles.tiles()
                val i = all.indexOf(focused)
                when (direction) {
                    FOCUS_LEFT -> all.getOrNull(i - 1) ?: focused
                    FOCUS_RIGHT -> all.getOrNull(i + 1) ?: tiles.moreApps
                    FOCUS_UP -> {
                        // Up before the rows have filled: fill them now.
                        if (rowsKey != focused.entry.key) fill(focused.entry)
                        // The first card of the lowest row; an app with no rows goes to the buttons.
                        lanes.lastOrNull { it.row.cards().isNotEmpty() }?.row?.cards()?.first()
                            ?: topFocus.up(focused, topBar.firstButton()) ?: focused
                    }
                    else -> focused
                }
            }
            focused === tiles.moreApps -> when (direction) {
                FOCUS_LEFT -> tiles.tiles().lastOrNull() ?: focused
                FOCUS_UP -> topFocus.up(focused, topBar.firstButton()) ?: focused
                else -> focused
            }
            lane >= 0 -> when (direction) {
                // The upper row goes to the buttons, not out of the screen.
                FOCUS_UP -> lanes.getOrNull(lane - 1)?.row?.entryCard() ?: topFocus.up(focused, topBar.firstButton()) ?: focused
                FOCUS_DOWN -> lanes.getOrNull(lane + 1)?.row?.entryCard() ?: chosen()
                else -> super.focusSearch(focused, direction)
            }
            inTop -> if (direction == FOCUS_DOWN) topFocus.down(chosen()) ?: focused else super.focusSearch(focused, direction)?.takeIf { topBar.isAncestorOf(it) } ?: focused
            else -> super.focusSearch(focused, direction)
        }
    }

    /** The card this row hands focus to from the other row: the one focused last here, else the first. */
    private fun FocusRow.entryCard(): View? = ArrayList<View>().also { addFocusables(it, FOCUS_DOWN) }.firstOrNull()

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
        /** Where each row's clipping starts, left of ROWS_X: room for a focused card's ring. */
        const val STRIP_START = 16
        const val TOP_BAR_Y = 23
        const val STRIP_SETTLE_MS = 160L
        const val CARDS_PER_FRAME = 2
        const val SCREEN_W = 960
    }
}
