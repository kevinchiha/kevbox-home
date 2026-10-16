package cloud.samlo.couch.settings

import cloud.samlo.couch.R
import cloud.samlo.couch.data.Programs

/** The preview channels apps publish, each switchable as a home row. */
class ChannelsPage(private val ctx: SettingsContext) : SettingsPage {
    override fun build() {
        ctx.layout.title.text = ctx.str(R.string.settings_channels)
        val channels = Programs.allChannels(ctx.activity, ctx.config)
        if (channels.isEmpty()) {
            ctx.layout.hint.text = ctx.str(R.string.channels_empty)
            return
        }
        ctx.layout.hint.text = ctx.str(R.string.channels_hint)
        for (info in channels) {
            ctx.options.toggle(Programs.title(ctx.activity, info.packageName, info.name, " · "), "", { info.shown(ctx.config) }, {
                ctx.config.channels = ctx.config.channels + (info.id.toString() to it)
            })
        }
    }
}
