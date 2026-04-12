package com.mycarv.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Avalanche danger and safety alerts from Avalanche.org + OpenWeather.
 *
 * Avalanche.org: free public API, updated daily during season (Dec-Apr).
 * Provides danger ratings (1-5) for US backcountry zones.
 */
class SafetyAlertService {

    companion object {
        private const val TAG = "SafetyAlertService"
        private const val AVALANCHE_API = "https://api.avalanche.org/v2/public/products"
    }

    data class AvalancheInfo(
        val dangerLevel: Int,          // 1=Low, 2=Moderate, 3=Considerable, 4=High, 5=Extreme
        val dangerLabel: String,
        val summary: String,
        val forecastUrl: String,
        val issuedAt: String,
    )

    data class SafetySummary(
        val avalanche: AvalancheInfo?,
        val weatherAlerts: List<WeatherService.WeatherAlert>,
        val overallRisk: String,       // "LOW", "MODERATE", "HIGH", "EXTREME"
        val recommendations: List<String>,
    )

    /**
     * Fetch avalanche danger rating for coordinates.
     * The Avalanche.org API finds the nearest forecast zone.
     */
    suspend fun getAvalancheInfo(lat: Double, lon: Double): AvalancheInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$AVALANCHE_API?lat=$lat&lng=$lon&type=forecast")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/json")

                if (conn.responseCode != 200) {
                    Log.e(TAG, "Avalanche API error: ${conn.responseCode}")
                    return@withContext null
                }

                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                parseAvalancheResponse(text)
            } catch (e: Exception) {
                Log.e(TAG, "Avalanche fetch failed", e)
                null
            }
        }

    /**
     * Combine avalanche + weather into a unified safety summary.
     */
    fun buildSafetySummary(
        avalanche: AvalancheInfo?,
        weatherAlerts: List<WeatherService.WeatherAlert>,
    ): SafetySummary {
        val recommendations = mutableListOf<String>()
        var overallRisk = "LOW"

        // Avalanche risk
        avalanche?.let {
            when (it.dangerLevel) {
                5 -> {
                    overallRisk = "EXTREME"
                    recommendations.add("EXTREME avalanche danger — avoid all backcountry and steep terrain")
                }
                4 -> {
                    overallRisk = "HIGH"
                    recommendations.add("HIGH avalanche danger — stick to groomed runs only")
                }
                3 -> {
                    if (overallRisk != "HIGH" && overallRisk != "EXTREME") overallRisk = "MODERATE"
                    recommendations.add("CONSIDERABLE avalanche danger — avoid steep ungroomed slopes")
                }
                2 -> {
                    recommendations.add("Moderate avalanche danger — be cautious on steep terrain")
                }
                1 -> {
                    recommendations.add("Low avalanche danger — generally safe conditions")
                }
            }
        }

        // Weather alerts
        weatherAlerts.forEach { alert ->
            when (alert.severity) {
                "extreme" -> {
                    overallRisk = "EXTREME"
                    recommendations.add("${alert.event}: ${alert.description.take(100)}")
                }
                "severe" -> {
                    if (overallRisk != "EXTREME") overallRisk = "HIGH"
                    recommendations.add("${alert.event}: ${alert.description.take(100)}")
                }
                "moderate" -> {
                    if (overallRisk == "LOW") overallRisk = "MODERATE"
                    recommendations.add("${alert.event}: ${alert.description.take(100)}")
                }
                else -> {
                    recommendations.add("${alert.event}: ${alert.description.take(100)}")
                }
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("No active safety alerts — enjoy your skiing!")
        }

        return SafetySummary(
            avalanche = avalanche,
            weatherAlerts = weatherAlerts,
            overallRisk = overallRisk,
            recommendations = recommendations,
        )
    }

    private fun parseAvalancheResponse(text: String): AvalancheInfo? {
        return try {
            val arr = org.json.JSONArray(text)
            if (arr.length() == 0) return null

            val product = arr.getJSONObject(0)
            val dangerArr = product.optJSONArray("danger")
            val dangerLevel = if (dangerArr != null && dangerArr.length() > 0) {
                dangerArr.getJSONObject(0).optInt("lower", 1)
            } else 0

            val dangerLabel = when (dangerLevel) {
                1 -> "Low"; 2 -> "Moderate"; 3 -> "Considerable"
                4 -> "High"; 5 -> "Extreme"; else -> "Unknown"
            }

            AvalancheInfo(
                dangerLevel = dangerLevel,
                dangerLabel = dangerLabel,
                summary = product.optString("bottom_line", "Check local forecast").take(300),
                forecastUrl = product.optString("url", ""),
                issuedAt = product.optString("published_time", ""),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse avalanche data", e)
            null
        }
    }
}
