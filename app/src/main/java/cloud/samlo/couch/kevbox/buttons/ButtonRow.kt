package cloud.samlo.couch.kevbox.buttons

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import cloud.samlo.couch.R
import cloud.samlo.couch.kevbox.KevBox
import cloud.samlo.couch.ui.dp

/**
 * The top-right buttons, in the order the config gives. Each button is built once and kept, so
 * a reload with the same list only updates icons and text and a focused button keeps focus.
 * [onCustomize] runs on a held OK on Settings.
 */
@SuppressLint("ViewConstructor")
class ButtonRow(context: Context, onCustomize: () -> Unit) : LinearLayout(context) {
    private val wifi = TopButton(context, alwaysText = false, focusable = true).apply {
        setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }
    }
    private val vpn = TopButton(context, alwaysText = true, focusable = false).apply {
        setIcon(R.drawable.ic_kb_vpn)
        setText(context.getString(R.string.kevbox_vpn))
        visibility = GONE
    }
    private val bluetooth = TopButton(context, alwaysText = false, focusable = true).apply {
        setOnClickListener { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
    }
    private val speedtest = TopButton(context, alwaysText = false, focusable = true).apply {
        setIcon(R.drawable.ic_kb_speedtest)
        setText(context.getString(R.string.kevbox_speedtest))
        setOnClickListener { openSpeedtest() }
    }
    private val settings = TopButton(context, alwaysText = true, focusable = true).apply {
        setIcon(R.drawable.ic_pill_settings)
        setText(context.getString(R.string.kevbox_settings))
        setOnClickListener { openSettings(Settings.ACTION_SETTINGS) }
        setOnLongClickListener {
            onCustomize()
            true
        }
    }
    private val byId = mapOf("wifi" to wifi, "vpn" to vpn, "bluetooth" to bluetooth, "speedtest" to speedtest, "settings" to settings)
    private var shownIds: List<String>? = null
    private var speedtestPkg = ""
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var btReceiver: BroadcastReceiver? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showNet(NetState(Link.NONE, 0, false, false))
        showBt(BtState(false, 0))
    }

    /** Shows [ids] in order; Speed test only when [speedtestApp] can be launched. */
    fun show(ids: List<String>, speedtestApp: String) {
        speedtestPkg = speedtestApp
        val wanted = ids.filter { it in byId && (it != "speedtest" || launchIntent() != null) }
        if (wanted == shownIds) return
        shownIds = wanted
        removeAllViews()
        wanted.forEachIndexed { i, id ->
            addView(byId.getValue(id), LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(26)).apply {
                if (i > 0) marginStart = context.dp(GAP)
            })
        }
    }

    /** The first visible button OK can press, in row order; never the VPN item. */
    fun firstButton(): View? = (0 until childCount).map { getChildAt(it) }
        .firstOrNull { it is TopButton && it.visibility == VISIBLE && it.isFocusable }

    /** Reads the network and Bluetooth once, then follows their changes until [stopWatching]. */
    fun startWatching() {
        showNet(NetworkState.read(context))
        showBt(BluetoothState.read(context))
        if (netCallback == null) netCallback = NetworkState.watch(context) { showNet(it) }
        if (btReceiver == null && context.getSystemService(BluetoothManager::class.java)?.adapter != null) {
            btReceiver = BluetoothState.watch(context) { showBt(it) }
        }
    }

    fun stopWatching() {
        netCallback?.let { NetworkState.unwatch(context, it) }
        btReceiver?.let { BluetoothState.unwatch(context, it) }
        netCallback = null
        btReceiver = null
    }

    private fun showNet(state: NetState) {
        wifi.setIcon(
            when (state.link) {
                Link.WIFI -> WIFI_ICONS[state.level.coerceIn(0, 4)]
                Link.ETHERNET -> R.drawable.ic_kb_ethernet
                Link.NONE -> R.drawable.ic_kb_no_network
            },
        )
        wifi.setDot(state.validated && state.link != Link.NONE)
        wifi.setText(context.getString(ButtonText.network(state)))
        vpn.appear(state.vpn)
    }

    private fun showBt(state: BtState) {
        bluetooth.setIcon(
            when {
                !state.on -> R.drawable.ic_kb_bluetooth_off
                state.connected > 0 -> R.drawable.ic_kb_bluetooth_connected
                else -> R.drawable.ic_kb_bluetooth_on
            },
        )
        bluetooth.setText(ButtonText.bluetoothText(state) { context.getString(it) })
    }

    private fun launchIntent(): Intent? {
        if (speedtestPkg.isBlank()) return null
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(speedtestPkg) ?: pm.getLaunchIntentForPackage(speedtestPkg)
    }

    private fun openSpeedtest() {
        val intent = launchIntent() ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(KevBox.TAG, "Speed test app $speedtestPkg did not open", e)
        }
    }

    /** Opens [action], or the main Settings page when this box lacks it; logs if both fail. */
    private fun openSettings(action: String) {
        for (each in listOf(action, Settings.ACTION_SETTINGS).distinct()) {
            try {
                context.startActivity(Intent(each).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next page.
            } catch (e: SecurityException) {
                // Try the next page.
            }
        }
        Log.w(KevBox.TAG, "No settings page opened for $action")
    }

    private companion object {
        const val GAP = 8
        val WIFI_ICONS = intArrayOf(
            R.drawable.ic_kb_wifi_0, R.drawable.ic_kb_wifi_1, R.drawable.ic_kb_wifi_2,
            R.drawable.ic_kb_wifi_3, R.drawable.ic_kb_wifi_4,
        )
    }
}
