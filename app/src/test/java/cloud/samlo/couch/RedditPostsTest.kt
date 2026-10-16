package cloud.samlo.couch

import cloud.samlo.couch.data.RedditPosts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditPostsTest {
    private fun post(
        url: String,
        width: Int = 1920,
        height: Int = 1080,
        score: Int = 1,
        nsfw: Boolean = false,
        hint: String = "image",
    ) = JSONObject()
        .put("url", url)
        .put("score", score)
        .put("over_18", nsfw)
        .put("post_hint", hint)
        .put(
            "preview",
            JSONObject().put(
                "images",
                JSONArray().put(JSONObject().put("source", JSONObject().put("width", width).put("height", height))),
            ),
        )

    private fun response(vararg posts: JSONObject) = JSONObject().put("data", JSONArray(posts.toList())).toString()

    @Test
    fun aspectRatioWindowIsSixteenNineToTwentyOneNine() {
        assertTrue(RedditPosts.isTvShaped(1920, 1080))
        assertTrue(RedditPosts.isTvShaped(2560, 1080))
        assertTrue(RedditPosts.isTvShaped(1920, 1200)) // 16:10 is the lower bound
        assertFalse(RedditPosts.isTvShaped(1920, 1280)) // 3:2 is below it
        assertFalse(RedditPosts.isTvShaped(3840, 1080)) // panorama
        assertFalse(RedditPosts.isTvShaped(1080, 1920)) // portrait
    }

    @Test
    fun smallOrUnsizedPicturesAreRejected() {
        assertFalse(RedditPosts.isTvShaped(1280, 720))
        assertFalse(RedditPosts.isTvShaped(1920, 0))
        assertFalse(RedditPosts.isTvShaped(0, 0))
    }

    @Test
    fun keepsOnlyTvShapedSafeImagesOnKnownHostsBestFirst() {
        val json = response(
            post("https://i.redd.it/low.jpg", score = 5),
            post("https://i.redd.it/high.jpg", score = 90),
            post("https://i.redd.it/nsfw.jpg", score = 99, nsfw = true),
            post("https://i.redd.it/tall.jpg", width = 1080, height = 1920, score = 80),
            post("https://example.com/other.jpg", score = 70),
            post("https://i.imgur.com/mid.jpg", score = 40),
            post("https://i.redd.it/video", score = 60, hint = "hosted:video"),
        )
        assertEquals(
            listOf("https://i.redd.it/high.jpg", "https://i.imgur.com/mid.jpg", "https://i.redd.it/low.jpg"),
            RedditPosts.wallpaperUrls(json),
        )
    }

    @Test
    fun postsWithoutAPreviewAreSkipped() {
        val bare = JSONObject().put("url", "https://i.redd.it/x.jpg").put("post_hint", "image")
        assertTrue(RedditPosts.wallpaperUrls(response(bare)).isEmpty())
    }

    @Test
    fun capsTheListAtForty() {
        val posts = (1..60).map { post("https://i.redd.it/$it.jpg", score = it) }.toTypedArray()
        assertEquals(40, RedditPosts.wallpaperUrls(response(*posts)).size)
    }

    @Test(expected = Exception::class)
    fun malformedAnswersThrowSoTheCallerKeepsItsCache() {
        RedditPosts.wallpaperUrls("{}")
    }
}
