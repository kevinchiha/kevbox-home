package cloud.samlo.couch.kevbox.home

import android.app.Activity
import android.view.KeyEvent
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.index.AppList
import cloud.samlo.couch.index.AppRow
import cloud.samlo.couch.index.IndexData

/**
 * The view side of Move in the left column: Up and Down carry the app through the list, OK or
 * Back places it and saves the new order to `config.json`. The order itself is [ColumnMove].
 *
 * A reload that arrives mid-move is held, not shown: binding the loader's order would snap the
 * row back. [onPlaced] gets it afterwards, its entries put in the placed order.
 */
class MoveMode(
    private val activity: Activity,
    private val list: AppList,
    /** The Config instance Home passed in; the one to change and save. */
    private val config: () -> Config,
    /** Move ended: [held] is the reload that waited (null when none), [config] the one saved into. */
    private val onPlaced: (held: IndexData?, config: Config) -> Unit,
) {
    private var move: ColumnMove? = null
    private var start: List<String> = emptyList()
    private var heldData: IndexData? = null
    private var heldConfig: Config? = null
    /** Keys whose key-down Move took, so their key-up never reaches the row (OK's would launch it). */
    private val downs = HashSet<Int>()

    val active: Boolean get() = move != null

    /** Starts moving [row]'s app; false when Move is already on or the app is not in the column. */
    fun start(row: AppRow): Boolean {
        if (active) return false
        val order = list.rows().map { it.entry.key }
        if (row.entry.key !in order) return false
        move = ColumnMove(order, row.entry.key)
        start = order
        return true
    }

    /** True when Move took [event]: every D-pad, OK and Back key while it is on, and their key-ups. */
    fun consumes(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return downs.remove(code) || (active && code in KEYS)
        if (event.action != KeyEvent.ACTION_DOWN || !active || code !in KEYS) return false
        downs.add(code)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> shift(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> shift(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> place()
        }
        return true
    }

    /** Keeps a reload for after the move. */
    fun hold(data: IndexData, config: Config) {
        heldData = data
        heldConfig = config
    }

    /** Ends Move where the app is now. Saves only when the order changed. */
    fun place() {
        val move = move ?: return
        this.move = null
        val target = heldConfig ?: config()
        if (move.order != start) {
            target.apps = move.saved(target.apps)
            target.save(UserFiles(activity).dir)
        }
        val held = heldData?.let { data ->
            val byKey = data.entries.associateBy { it.key }
            IndexData(move.saved(data.entries.map { it.key }).mapNotNull { byKey[it] }, data.usageKnown)
        }
        heldData = null
        heldConfig = null
        onPlaced(held, target)
    }

    /** The window lost focus: key-ups seen from here on belong to another window's presses. */
    fun forgetKeys() = downs.clear()

    private fun shift(by: Int) {
        val move = move ?: return
        if (!move.shift(by)) return
        // The same entries in the new order: rows are reused, so the moving one keeps focus.
        val byKey = list.rows().associate { it.entry.key to it.entry }
        list.bind(move.order.mapNotNull { byKey[it] }, rebuildAll = false) { }
        val row = list.rows().getOrNull(move.index) ?: return
        list.select(row, inRow = false)
        list.reveal(move.index)
    }

    private companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        )
    }
}
