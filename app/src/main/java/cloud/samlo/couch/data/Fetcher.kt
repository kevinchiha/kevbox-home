package cloud.samlo.couch.data

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Opens http(s) URLs and content URIs alike. Blocking: call from a worker thread. */
class Fetcher(private val context: Context) {
    fun open(url: String): InputStream = when (Uri.parse(url).scheme) {
        "http", "https" -> connect(url).inputStream
        else -> context.contentResolver.openInputStream(Uri.parse(url))
            ?: throw IllegalStateException("No stream for $url")
    }

    /** The body as text, or null on any failure: callers fall back to their cache. */
    fun text(url: String): String? = try {
        open(url).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    /**
     * The body with the response's HTTP `Date` header in epoch ms (null when missing, unreadable or
     * not over http), or null on any failure. The silent update counts time by the server's clock.
     */
    fun fetch(url: String): Fetched? = try {
        when (Uri.parse(url).scheme) {
            "http", "https" -> {
                val connection = connect(url)
                val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val date = try {
                    connection.getHeaderFieldDate("Date", 0L)
                } catch (e: Exception) {
                    0L
                }
                Fetched(text, date.takeIf { it > 0 })
            }
            else -> text(url)?.let { Fetched(it, null) }
        }
    } catch (e: Exception) {
        null
    }

    private fun connect(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        return connection
    }

    private companion object {
        const val TIMEOUT_MS = 8000
    }
}

/** A fetched body and the server's `Date` header in epoch ms, null when it had none. */
class Fetched(val text: String, val dateMs: Long?)
