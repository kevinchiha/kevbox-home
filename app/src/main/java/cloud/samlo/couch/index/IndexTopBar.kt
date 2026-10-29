package cloud.samlo.couch.index

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Animatable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.WeatherNow
import cloud.samlo.couch.kevbox.buttons.ButtonRow
import cloud.samlo.couch.kevbox.config.KevBoxConfig
import cloud.samlo.couch.ui.Fonts
import cloud.samlo.couch.ui.NowPlaying
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.UpdateChip
import cloud.samlo.couch.ui.dp

/**
 * The bar across the top. On the left, the clock, then the weather; on the right, what's playing,
 * an update when there is one, then the KevBox buttons. One row, so the clock stays level with
 * the buttons.
 */
@SuppressLint("ViewConstructor")
class IndexTopBar(context: Context, onSettings: () -> Unit, onUpdate: () -> Unit) : LinearLayout(context) {
    val nowPlaying = NowPlaying(context)
    val update = UpdateChip(context).apply { setOnClickListener { onUpdate() } }
    private val buttons = ButtonRow(context, onSettings)
    private val clock = TextClock(context).apply {
        format12Hour = "HH:mm"
        format24Hour = "HH:mm"
        textSize = 20f
        typeface = Fonts.hanken(context, 600)
        letterSpacing = -0.02f
        setTextColor(Tokens.textPrimary)
        includeFontPadding = false
    }
    private val weather = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }
    private val weatherIcon = ImageView(context)
    private val temperature = text(20f, 600, Tokens.textPrimary).apply { letterSpacing = -0.02f }
    private val place = text(9.5f, 500, Tokens.textMuted, 1.25f).apply { maxLines = 2 }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(15) })
        // Meteocons leave a margin around the drawing; 30dp draws the sky at about 20dp.
        // The icon may overhang the pills' height without making the bar taller.
        weather.addView(weatherIcon, LayoutParams(context.dp(30), context.dp(30)).apply {
            marginEnd = context.dp(2)
            topMargin = -context.dp(2)
            bottomMargin = -context.dp(2)
        })
        weather.addView(temperature)
        weather.addView(place, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(7) })
        addView(weather, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        // Takes the width left over, pushing the rest to the right edge.
        addView(Space(context), LayoutParams(0, 0, 1f))
        addView(nowPlaying, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(15) })
        addView(update, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(26)).apply { marginEnd = context.dp(15) })
        addView(buttons, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(26)))
    }

    fun showWeather(now: WeatherNow?) {
        if (now == null) {
            weather.visibility = GONE
            return
        }
        weatherIcon.setImageResource(now.icon)
        if (animating) (weatherIcon.drawable as? Animatable)?.start()
        temperature.text = context.getString(R.string.weather_temperature, now.temperature)
        place.text = context.getString(R.string.index_place_range, now.place, now.high, now.low)
        weather.visibility = VISIBLE
    }

    /** The leftmost button OK can press, where Up from the first app lands; null when none shows. */
    fun firstButton(): View? = buttons.firstButton()

    /** Runs on every load: the clock's format, and the buttons [config] asks for. */
    fun applyLook(config: Config) {
        val time = if (config.clock24h) "HH:mm" else "h:mm"
        clock.format12Hour = time
        clock.format24Hour = time
        buttons.show(KevBoxConfig.buttonsOf(config), config.speedtestApp)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        buttons.startWatching()
    }

    override fun onDetachedFromWindow() {
        buttons.stopWatching()
        super.onDetachedFromWindow()
    }

    private fun text(sizeSp: Float, weight: Int, color: Int, lineHeight: Float = 1f) =
        IndexText(context, sizeSp, weight, color, lineHeight).apply { maxLines = 1 }

    private var animating = false

    /** The weather icon moves, like Labby's, while the home screen is showing. */
    fun animateWeather(on: Boolean) {
        animating = on
        (weatherIcon.drawable as? Animatable)?.let { if (on) it.start() else it.stop() }
    }
}
