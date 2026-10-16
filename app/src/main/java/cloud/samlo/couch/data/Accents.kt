package cloud.samlo.couch.data

import cloud.samlo.couch.R

/** An accent choice: [label] is a string resource, [hex] the colour. */
class Accent(val label: Int, val hex: String)

object Accents {
    val all = listOf(
        Accent(R.string.accent_ember, "#FF8A4C"),
        Accent(R.string.accent_amber, "#FBBF24"),
        Accent(R.string.accent_mint, "#34D399"),
        Accent(R.string.accent_sky, "#5AB0FF"),
        Accent(R.string.accent_coral, "#FF6B81"),
        Accent(R.string.accent_white, "#F5F5F7"),
    )

    val default: String get() = all.first().hex

    /** Choices an earlier palette saved, and the one that replaced each. */
    private val retired = mapOf(
        "#8B93FF" to "#FF8A4C",
        "#4ADE80" to "#34D399",
        "#F472B6" to "#FF6B81",
    )

    /** A saved accent from an older palette becomes its successor; anything else is kept. */
    fun migrate(hex: String): String = retired[hex.uppercase()] ?: hex
}
