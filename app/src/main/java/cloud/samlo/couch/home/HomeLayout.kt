package cloud.samlo.couch.home

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.ui.Ambient
import cloud.samlo.couch.ui.Hero
import cloud.samlo.couch.ui.Rows
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp

/** The home screen's view tree, bottom to top: hero, rows, top bar, ambient overlay. */
class HomeViews(context: Context, images: Images, onSettings: () -> Unit, onUpdate: () -> Unit) {
    val hero = Hero(context, images)
    val rows = Rows(context)
    val topBar = TopBar(context, onSettings, onUpdate)
    val ambient = Ambient(context, images)

    val root = FrameLayout(context).apply {
        addView(hero, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(rows, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            topMargin = context.dp(Tokens.ROWS_TOP)
        })
        addView(topBar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(Tokens.OVERSCAN_V)
            marginEnd = context.dp(Tokens.OVERSCAN_H)
        })
        addView(ambient, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
}
