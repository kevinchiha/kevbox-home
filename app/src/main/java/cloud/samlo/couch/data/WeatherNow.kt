package cloud.samlo.couch.data

import org.json.JSONObject
import kotlin.math.roundToInt

/** Current conditions at [place], plus today's range. */
class WeatherNow(val place: String, val temperature: Int, val high: Int, val low: Int, val code: Int, val isDay: Boolean) {
    val condition: Condition get() = Condition.of(code)
    val icon: Int get() = condition.icon(isDay)

    /** The preferences cache form. */
    fun serialize(): String = JSONObject()
        .put("place", place).put("t", temperature).put("hi", high).put("lo", low)
        .put("code", code).put("day", isDay).toString()

    companion object {
        fun parse(text: String): WeatherNow? = try {
            val o = JSONObject(text)
            WeatherNow(o.getString("place"), o.getInt("t"), o.getInt("hi"), o.getInt("lo"), o.getInt("code"), o.getBoolean("day"))
        } catch (e: Exception) {
            null
        }

        /** An Open-Meteo forecast answer; null when it isn't shaped as expected. */
        fun fromForecast(place: String, json: String): WeatherNow? = try {
            val o = JSONObject(json)
            val current = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            WeatherNow(
                place = place,
                temperature = current.getDouble("temperature_2m").roundToInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
            )
        } catch (e: Exception) {
            null
        }
    }
}
