package com.mycarv.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenWeatherMap API client for mountain weather conditions.
 * Free tier: 1,000 calls/day, current weather + 5-day forecast.
 *
 * Set your API key in the companion object or pass at runtime.
 * Get a free key at: https://openweathermap.org/appid
 */
class WeatherService(private val apiKey: String = DEFAULT_API_KEY) {

    companion object {
        private const val TAG = "WeatherService"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5"
        const val DEFAULT_API_KEY = "" // User sets their own key
    }

    data class MountainWeather(
        val temperature: Float,        // °C
        val feelsLike: Float,          // °C with wind chill
        val humidity: Int,             // %
        val windSpeed: Float,          // m/s
        val windGust: Float,           // m/s
        val windDirection: Int,        // degrees
        val visibility: Int,           // meters
        val description: String,       // "light snow", "clear sky"
        val icon: String,              // weather icon code
        val uvIndex: Float,            // 0-11+
        val snowLastHour: Float,       // mm
        val pressure: Float,           // hPa
        val cloudCover: Int,           // %
        val sunrise: Long,             // unix timestamp
        val sunset: Long,              // unix timestamp
        val frostbiteRiskMinutes: Int, // estimated time to frostbite
        val alerts: List<WeatherAlert>,
    )

    data class WeatherAlert(
        val event: String,             // "Avalanche Warning", "Wind Advisory"
        val description: String,
        val severity: String,          // "minor", "moderate", "severe", "extreme"
    )

    data class HourlyForecast(
        val hour: Int,
        val temperature: Float,
        val windSpeed: Float,
        val snowProbability: Int,      // %
        val description: String,
    )

    /**
     * Fetch current mountain weather for the given GPS coordinates.
     */
    suspend fun getCurrentWeather(lat: Double, lon: Double): MountainWeather? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            try {
                val url = URL("$BASE_URL/onecall?lat=$lat&lon=$lon&units=metric&appid=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                if (conn.responseCode != 200) {
                    Log.e(TAG, "Weather API error: ${conn.responseCode}")
                    return@withContext null
                }

                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                parseWeather(json)
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch failed", e)

                // Fallback: try simpler endpoint
                try {
                    val url = URL("$BASE_URL/weather?lat=$lat&lon=$lon&units=metric&appid=$apiKey")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    if (conn.responseCode != 200) return@withContext null
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    conn.disconnect()
                    parseSimpleWeather(json)
                } catch (e2: Exception) {
                    Log.e(TAG, "Weather fallback failed", e2)
                    null
                }
            }
        }

    /**
     * Fetch 12-hour forecast.
     */
    suspend fun getHourlyForecast(lat: Double, lon: Double): List<HourlyForecast> =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext emptyList()
            try {
                val url = URL("$BASE_URL/forecast?lat=$lat&lon=$lon&units=metric&cnt=12&appid=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                if (conn.responseCode != 200) return@withContext emptyList()
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                parseHourlyForecast(json)
            } catch (e: Exception) {
                Log.e(TAG, "Forecast fetch failed", e)
                emptyList()
            }
        }

    private fun parseWeather(json: JSONObject): MountainWeather {
        val current = json.optJSONObject("current") ?: json
        val weather = current.optJSONArray("weather")?.optJSONObject(0)
        val wind = current.optJSONObject("wind") ?: current
        val alerts = json.optJSONArray("alerts")

        val temp = current.optDouble("temp", 0.0).toFloat()
        val windSpd = wind.optDouble("speed", current.optDouble("wind_speed", 0.0)).toFloat()

        return MountainWeather(
            temperature = temp,
            feelsLike = current.optDouble("feels_like", temp.toDouble()).toFloat(),
            humidity = current.optInt("humidity", 0),
            windSpeed = windSpd,
            windGust = wind.optDouble("gust", current.optDouble("wind_gust", 0.0)).toFloat(),
            windDirection = wind.optInt("deg", current.optInt("wind_deg", 0)),
            visibility = current.optInt("visibility", 10000),
            description = weather?.optString("description", "unknown") ?: "unknown",
            icon = weather?.optString("icon", "01d") ?: "01d",
            uvIndex = current.optDouble("uvi", 0.0).toFloat(),
            snowLastHour = current.optJSONObject("snow")?.optDouble("1h", 0.0)?.toFloat() ?: 0f,
            pressure = current.optDouble("pressure", 1013.0).toFloat(),
            cloudCover = current.optInt("clouds", current.optJSONObject("clouds")?.optInt("all", 0) ?: 0),
            sunrise = current.optLong("sunrise", 0),
            sunset = current.optLong("sunset", 0),
            frostbiteRiskMinutes = estimateFrostbiteTime(temp, windSpd),
            alerts = parseAlerts(alerts),
        )
    }

    private fun parseSimpleWeather(json: JSONObject): MountainWeather {
        val main = json.optJSONObject("main") ?: json
        val weather = json.optJSONArray("weather")?.optJSONObject(0)
        val wind = json.optJSONObject("wind") ?: json
        val sys = json.optJSONObject("sys") ?: json

        val temp = main.optDouble("temp", 0.0).toFloat()
        val windSpd = wind.optDouble("speed", 0.0).toFloat()

        return MountainWeather(
            temperature = temp,
            feelsLike = main.optDouble("feels_like", temp.toDouble()).toFloat(),
            humidity = main.optInt("humidity", 0),
            windSpeed = windSpd,
            windGust = wind.optDouble("gust", 0.0).toFloat(),
            windDirection = wind.optInt("deg", 0),
            visibility = json.optInt("visibility", 10000),
            description = weather?.optString("description", "unknown") ?: "unknown",
            icon = weather?.optString("icon", "01d") ?: "01d",
            uvIndex = 0f,
            snowLastHour = json.optJSONObject("snow")?.optDouble("1h", 0.0)?.toFloat() ?: 0f,
            pressure = main.optDouble("pressure", 1013.0).toFloat(),
            cloudCover = json.optJSONObject("clouds")?.optInt("all", 0) ?: 0,
            sunrise = sys.optLong("sunrise", 0),
            sunset = sys.optLong("sunset", 0),
            frostbiteRiskMinutes = estimateFrostbiteTime(temp, windSpd),
            alerts = emptyList(),
        )
    }

    private fun parseHourlyForecast(json: JSONObject): List<HourlyForecast> {
        val list = json.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            val main = item.optJSONObject("main") ?: item
            val weather = item.optJSONArray("weather")?.optJSONObject(0)
            HourlyForecast(
                hour = i,
                temperature = main.optDouble("temp", 0.0).toFloat(),
                windSpeed = item.optJSONObject("wind")?.optDouble("speed", 0.0)?.toFloat() ?: 0f,
                snowProbability = (item.optDouble("pop", 0.0) * 100).toInt(),
                description = weather?.optString("description", "") ?: "",
            )
        }
    }

    private fun parseAlerts(alerts: org.json.JSONArray?): List<WeatherAlert> {
        if (alerts == null) return emptyList()
        return (0 until alerts.length()).map { i ->
            val alert = alerts.getJSONObject(i)
            WeatherAlert(
                event = alert.optString("event", "Alert"),
                description = alert.optString("description", "").take(200),
                severity = alert.optString("severity", "moderate"),
            )
        }
    }

    /**
     * Estimate minutes to frostbite based on wind chill.
     * Formula from NWS wind chill chart.
     */
    private fun estimateFrostbiteTime(tempC: Float, windMs: Float): Int {
        val windKmh = windMs * 3.6f
        if (tempC > 0 || windKmh < 5) return 999 // no risk
        val windChill = 13.12 + 0.6215 * tempC - 11.37 * Math.pow(windKmh.toDouble(), 0.16) +
            0.3965 * tempC * Math.pow(windKmh.toDouble(), 0.16)
        return when {
            windChill < -45 -> 5
            windChill < -35 -> 10
            windChill < -27 -> 15
            windChill < -20 -> 30
            windChill < -10 -> 60
            else -> 999
        }
    }
}
