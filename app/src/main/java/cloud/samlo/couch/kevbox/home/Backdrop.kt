package cloud.samlo.couch.kevbox.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.ui.FlatView
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp
import cloud.samlo.couch.ui.fill
import cloud.samlo.couch.ui.scrimDrawable

/**
 * The full-screen picture behind Home: the focused program's art, else the wallpaper, darkened
 * by the user's Dim setting. Two stacked images crossfade once focus has rested, the way Couch's
 * Hero does it; the code is a copy so Hero stays as upstream has it.
 */
@SuppressLint("ViewConstructor")
class Backdrop(context: Context, private val images: Images) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val below = picture()
    private val above = picture().apply { alpha = 0f }
    private val scrim = FlatView(context)
    private var dim: String? = null

    private var wallpaper: String? = null
    /** The focused program's art; null when focus has none, so the wallpaper shows. */
    private var art: String? = null
    @Volatile
    private var pendingUrl: String? = null
    private var shownUrl: String? = null
    private val settle = Runnable { request(art ?: wallpaper) }

    init {
        addView(below, fill())
        addView(above, fill())
        addView(scrim, fill())
        setDim(DEFAULT_DIM)
    }

    /** Shows [url] (a program's backdrop) once focus rests; null falls back to the wallpaper. */
    fun show(url: String?) {
        art = url
        main.removeCallbacks(settle)
        main.postDelayed(settle, Tokens.HERO_SETTLE_MS)
    }

    /** A new wallpaper. Takes over at once when the focus has no art of its own. */
    fun setWallpaper(url: String?) {
        wallpaper = url
        if (art == null) request(url)
    }

    /** "light", "medium" or "strong"; the scrim is only rebuilt when the level changes. */
    fun setDim(level: String) {
        if (level == dim) return
        dim = level
        scrim.background = scrimDrawable(level)
    }

    private fun request(url: String?) {
        // Already on its way: a second load would decode the same picture again on a weak TV.
        // A load that failed is retried once something else has been asked for in between.
        if (url != null && url == pendingUrl && url != shownUrl) return
        // Set first, so a load still on its way for something else is dropped.
        pendingUrl = url
        if (url == shownUrl) return
        if (url == null) {
            crossfade(null)
            return
        }
        // 720p in RGB_565, like Hero: the scrim hides the softness, at less than half the bytes of 1080p.
        images.load(url, context.dp(ART_W), context.dp(ART_H), 0f, opaque = true, isCurrent = { pendingUrl == url }) {
            shownUrl = url
            crossfade(it)
        }
    }

    private fun crossfade(bitmap: Bitmap?) {
        if (bitmap == null) return fadeOut()
        above.animate().cancel()
        if (above.alpha > 0.5f) {
            // The top image is showing: put the new art underneath and fade the top away.
            below.setImageBitmap(bitmap)
            above.animate().alpha(0f).setDuration(Tokens.HERO_FADE_MS).setInterpolator(Tokens.ease).start()
        } else {
            above.setImageBitmap(bitmap)
            above.animate().alpha(1f).setDuration(Tokens.HERO_FADE_MS).setInterpolator(Tokens.ease).start()
        }
    }

    /**
     * Fades the art away to the flat window background. Swapping layers cannot do it: when the
     * art sits on [below], fading an empty [above] in leaves the art showing, since an ImageView
     * with no bitmap is see-through. So the showing picture moves to [above] (same frame, no
     * visible change), [below] empties, and [above] fades out and lets its bitmap go.
     */
    private fun fadeOut() {
        shownUrl = null
        above.animate().cancel()
        if (above.alpha <= 0.5f) {
            above.setImageDrawable(below.drawable)
            above.alpha = 1f
        }
        below.setImageDrawable(null)
        above.animate().alpha(0f).setDuration(Tokens.HERO_FADE_MS).setInterpolator(Tokens.ease)
            .withEndAction { above.setImageDrawable(null) }.start()
    }

    private fun picture() = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

    private companion object {
        const val DEFAULT_DIM = "medium"
        const val ART_W = 640
        const val ART_H = 360
    }
}
