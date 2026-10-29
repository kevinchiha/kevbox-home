package cloud.samlo.couch.kevbox.drawer

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.FlatView
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.cardRing
import cloud.samlo.couch.ui.dp

/**
 * How the drawer shows Move: the held card grows past its focus size under an accent ring, and
 * the cards it passes glide to their new cells. Couch 0.9.0 dropped `Card.moving` and
 * `glideToNewPlaces` with its Rows layout, so the drawer keeps its own copies here.
 */
internal object MoveLook {
    /** How large the held card grows; the grid leaves room above and below for it. */
    const val SCALE = 1.14f

    private const val RING_TAG = "kevbox-held-ring"

    /** Puts the accent ring over [card]'s own ring, [ringHeight] px tall, and grows the card. */
    fun hold(card: Card, ringHeight: Int) {
        release(card, animate = false)
        val ring = FlatView(card.context).apply {
            tag = RING_TAG
            background = cardRing(card.context).apply {
                getDrawable(0).alpha = 0
                (getDrawable(1) as GradientDrawable).setStroke(card.context.dp(3), Tokens.accent)
            }
        }
        card.addView(ring, FrameLayout.LayoutParams(MATCH_PARENT, ringHeight))
        scale(card, SCALE)
    }

    /** Takes the ring off and returns [card] to its focus size. */
    fun release(card: Card, animate: Boolean = true) {
        card.findViewWithTag<View>(RING_TAG)?.let(card::removeView)
        if (animate) scale(card, if (card.hasFocus()) Tokens.FOCUS_SCALE else 1f)
    }

    private fun scale(card: Card, to: Float) {
        card.animate().scaleX(to).scaleY(to).setDuration(Tokens.FOCUS_IN_MS).setInterpolator(Tokens.ease).start()
    }

    /**
     * Call just before reordering [views] in their parent. Once the next layout has placed them,
     * each glides from where it was drawn to its new place: translation only, so the move costs
     * one layout pass, not one per frame. The first view must stay attached (the focused one).
     */
    fun glideToNewPlaces(vararg views: View) {
        val anchor = views.first()
        val before = views.map { floatArrayOf(it.left + it.translationX, it.top + it.translationY) }
        anchor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                anchor.viewTreeObserver.removeOnPreDrawListener(this)
                views.forEachIndexed { i, view ->
                    view.translationX = before[i][0] - view.left
                    view.translationY = before[i][1] - view.top
                    view.animate().translationX(0f).translationY(0f).setDuration(Tokens.GLIDE_MS).setInterpolator(Tokens.ease).start()
                }
                return true
            }
        })
    }
}
