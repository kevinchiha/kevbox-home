package cloud.samlo.couch.data

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class Place(val name: String, val latitude: Double, val longitude: Double)

/**
 * Resolves where to report weather for: a typed city through Open-Meteo's geocoder, or the
 * connection's IP address when no city is set.
 */
class WeatherPlaces(private val fetcher: Fetcher, private val prefs: SharedPreferences) {
    fun find(city: String): Place? {
        val key = "place:$city"
        prefs.getString(key, null)?.let { cached ->
            val parts = cached.split('|')
            if (parts.size == 3) return Place(parts[0], parts[1].toDouble(), parts[2].toDouble())
        }
        val place = try {
            if (city.isBlank()) byIp() else byName(city)
        } catch (e: Exception) {
            null
        } ?: return null
        // An IP lookup can move (VPN, new ISP), so only a typed city is cached for good.
        if (city.isNotBlank()) prefs.edit().putString(key, "${place.name}|${place.latitude}|${place.longitude}").apply()
        return place
    }

    private fun byIp(): Place? {
        val o = JSONObject(fetcher.text("https://ipwho.is/?fields=city,latitude,longitude,success") ?: return null)
        if (!o.optBoolean("success", true)) return null
        return Place(o.getString("city"), o.getDouble("latitude"), o.getDouble("longitude"))
    }

    private fun byName(city: String): Place? {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val o = JSONObject(fetcher.text("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=${Locale.getDefault().language}") ?: return null)
        val r = o.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }
}
