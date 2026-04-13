package com.kineticai.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ski resort information service.
 * Uses SkiAPI (via RapidAPI) for lift status, snow conditions, and resort details.
 *
 * Free tier available on RapidAPI.
 */
class ResortService(private val apiKey: String = "") {

    companion object {
        private const val TAG = "ResortService"
        private const val BASE_URL = "https://ski-resort-forecast.p.rapidapi.com"
        private const val HOST = "ski-resort-forecast.p.rapidapi.com"
    }

    data class ResortInfo(
        val name: String,
        val liftsOpen: Int,
        val liftsTotal: Int,
        val trailsOpen: Int,
        val trailsTotal: Int,
        val snowDepthBase: Int,        // cm
        val snowDepthSummit: Int,      // cm
        val freshSnow24h: Int,         // cm
        val freshSnow7d: Int,          // cm
        val conditions: String,        // "Packed Powder", "Groomed", etc.
        val lastUpdated: String,
    )

    /**
     * Search for the nearest ski resort to given coordinates.
     * Returns resort info if found.
     */
    suspend fun getNearestResort(lat: Double, lon: Double): ResortInfo? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            try {
                val url = URL("$BASE_URL/v1/resort/search?lat=$lat&lon=$lon&radius=50")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("x-rapidapi-key", apiKey)
                conn.setRequestProperty("x-rapidapi-host", HOST)
                conn.connectTimeout = 10_000

                if (conn.responseCode != 200) return@withContext null

                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                parseResort(json)
            } catch (e: Exception) {
                Log.e(TAG, "Resort fetch failed", e)
                null
            }
        }

    /**
     * Generate a resort status string for display.
     */
    fun formatResortStatus(info: ResortInfo): String {
        return buildString {
            appendLine("${info.name}")
            appendLine("Lifts: ${info.liftsOpen}/${info.liftsTotal} open")
            appendLine("Trails: ${info.trailsOpen}/${info.trailsTotal} open")
            appendLine("Snow: ${info.snowDepthBase}cm base / ${info.snowDepthSummit}cm summit")
            if (info.freshSnow24h > 0) appendLine("Fresh: ${info.freshSnow24h}cm in 24h")
            appendLine("Conditions: ${info.conditions}")
        }
    }

    private fun parseResort(json: JSONObject): ResortInfo? {
        return try {
            val data = json.optJSONArray("data")?.optJSONObject(0) ?: json
            val lifts = data.optJSONObject("lifts")
            val runs = data.optJSONObject("runs")
            val snow = data.optJSONObject("snow")

            ResortInfo(
                name = data.optString("name", "Unknown Resort"),
                liftsOpen = lifts?.optInt("open", 0) ?: 0,
                liftsTotal = lifts?.optInt("total", 0) ?: 0,
                trailsOpen = runs?.optInt("open", 0) ?: 0,
                trailsTotal = runs?.optInt("total", 0) ?: 0,
                snowDepthBase = snow?.optInt("base", 0) ?: 0,
                snowDepthSummit = snow?.optInt("summit", 0) ?: 0,
                freshSnow24h = snow?.optInt("fresh24h", 0) ?: 0,
                freshSnow7d = snow?.optInt("fresh7d", 0) ?: 0,
                conditions = data.optString("conditions", "Unknown"),
                lastUpdated = data.optString("updated", ""),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse resort", e)
            null
        }
    }
}
