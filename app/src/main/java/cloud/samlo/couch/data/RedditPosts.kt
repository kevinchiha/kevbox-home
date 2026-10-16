package cloud.samlo.couch.data

import org.json.JSONObject

/** Picks usable wallpapers out of an Arctic Shift post search answer. */
object RedditPosts {
    private const val MIN_WIDTH = 1600
    private const val MAX_URLS = 40
    private val HOSTS = listOf("https://i.redd.it/", "https://i.imgur.com/")

    /** Keep 16:9 up to 21:9; wider panoramas crop badly on a TV. */
    fun isTvShaped(width: Int, height: Int) =
        height > 0 && width >= MIN_WIDTH && width.toFloat() / height in 1.6f..2.45f

    /** Safe-for-work image posts that suit a TV, best score first. Throws on malformed JSON. */
    fun wallpaperUrls(json: String): List<String> {
        val posts = JSONObject(json).getJSONArray("data")
        return (0 until posts.length()).map { posts.getJSONObject(it) }
            .filter { !it.optBoolean("over_18") && it.optString("post_hint") == "image" }
            .filter { isTvShaped(it) }
            .sortedByDescending { it.optInt("score") }
            .map { it.getString("url") }
            .filter { url -> HOSTS.any { url.startsWith(it) } }
            .take(MAX_URLS)
    }

    private fun isTvShaped(post: JSONObject): Boolean {
        val source = post.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("source")
        return isTvShaped(source?.optInt("width") ?: 0, source?.optInt("height") ?: 0)
    }
}
