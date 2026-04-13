package com.kineticai.app.network

import android.util.Log
import com.kineticai.app.analysis.SkiMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI coaching service — sends run data to a cloud LLM for deep personalized analysis.
 *
 * Supports OpenAI (GPT-4), Anthropic (Claude), or any OpenAI-compatible endpoint.
 * The LLM receives your full metric summary and returns natural language coaching.
 */
class AiCoachService(
    private val apiKey: String = "",
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        private const val TAG = "AiCoachService"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    data class AiInsight(
        val summary: String,
        val strengths: List<String>,
        val improvements: List<String>,
        val nextDrill: String,
        val motivation: String,
    )

    /**
     * Send run metrics to AI and get personalized coaching analysis.
     */
    suspend fun analyzeRun(metrics: SkiMetrics, feedback: List<String>): AiInsight? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            try {
                val prompt = buildPrompt(metrics, feedback)
                val response = callLLM(prompt) ?: return@withContext null
                parseResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed", e)
                null
            }
        }

    /**
     * Ask the AI a natural language question about your skiing.
     */
    suspend fun askQuestion(question: String, metrics: SkiMetrics): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            try {
                val context = "The skier's current run data:\n${metricsToText(metrics)}\n\nQuestion: $question"
                callLLM(context)
            } catch (e: Exception) {
                Log.e(TAG, "AI Q&A failed", e)
                null
            }
        }

    /**
     * Generate a weekly progress summary from multiple runs.
     */
    suspend fun weeklyReport(runSummaries: List<String>): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) return@withContext null
            try {
                val prompt = """You are an expert ski coach analyzing a week of skiing data.
                    |
                    |Here are the run summaries from this week:
                    |${runSummaries.joinToString("\n---\n")}
                    |
                    |Provide a concise weekly progress report covering:
                    |1. Overall trend (improving/plateauing/declining)
                    |2. Biggest improvement this week
                    |3. Persistent weakness to focus on
                    |4. Recommended drill for next session
                    |5. One motivational observation
                    |
                    |Keep it concise and actionable. Talk directly to the skier.""".trimMargin()
                callLLM(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Weekly report failed", e)
                null
            }
        }

    private fun buildPrompt(metrics: SkiMetrics, feedback: List<String>): String {
        return """You are an expert ski coach (PSIA Level 3 certified) analyzing a ski run.
            |
            |RUN DATA:
            |${metricsToText(metrics)}
            |
            |EXISTING ANALYSIS:
            |${feedback.joinToString("\n")}
            |
            |Provide a personalized coaching response in this JSON format:
            |{
            |  "summary": "2-3 sentence overall assessment",
            |  "strengths": ["strength 1", "strength 2"],
            |  "improvements": ["specific improvement 1", "specific improvement 2", "specific improvement 3"],
            |  "nextDrill": "one specific drill name and brief description",
            |  "motivation": "one encouraging sentence"
            |}
            |
            |Be specific to their numbers. Reference their actual scores. Be encouraging but honest.
            |If Kinetic Score < 90, focus on fundamentals. If 90-130, focus on refinement. If >130, push advanced skills.""".trimMargin()
    }

    private fun metricsToText(m: SkiMetrics): String {
        return """Kinetic Score: ${m.skiIQ}/200
            |Turns: ${m.turnCount} (L:${m.leftTurnCount} R:${m.rightTurnCount})
            |Duration: ${m.runDurationFormatted}
            |Max Speed: ${String.format("%.1f", m.maxSpeedKmh)} km/h
            |Altitude Drop: ${String.format("%.0f", m.altitudeDrop)}m
            |
            |BALANCE: ${m.balanceScore.toInt()}/100
            |  Early Forward: ${m.earlyForwardMovement.toInt()}, Centered: ${m.centeredBalance.toInt()}
            |  Boot Flex: ${String.format("%.1f", m.bootFlexAngle)}deg, Knee Flex: ${String.format("%.0f", m.kneeFlexion)}deg
            |
            |EDGING: ${m.edgingScore.toInt()}/100
            |  Edge Angle: ${String.format("%.1f", m.edgeAngle)}deg, Early Edging: ${m.earlyEdging.toInt()}
            |  Edge Similarity: ${m.edgeSimilarity.toInt()}, Progressive: ${m.progressiveEdgeBuild.toInt()}
            |  Angulation: ${String.format("%.1f", m.ankleAngulation)}deg
            |  Carve Quality (audio): ${m.carveQualityScore.toInt()}/100
            |
            |ROTARY: ${m.rotaryScore.toInt()}/100
            |  Turn Shape: ${m.turnShape.toInt()}, Parallel: ${m.parallelSkis.toInt()}
            |  Kinetic Chain Bottom-Up: ${String.format("%.0f", m.kinChainBottomUpPct)}%
            |
            |PRESSURE: ${m.pressureScore.toInt()}/100
            |  G-Force: avg ${String.format("%.1f", m.gForce)}G, max ${String.format("%.1f", m.maxGForce)}G
            |  Weight Release: ${m.transitionWeightRelease.toInt()}, Smoothness: ${m.pressureSmoothness.toInt()}
            |
            |TURN PHASES: Init ${m.phaseInitScore.toInt()} | SteerIn ${m.phaseSteerInScore.toInt()} | Apex ${m.phaseApexScore.toInt()} | SteerOut ${m.phaseSteerOutScore.toInt()} | Transition ${m.phaseTransitionScore.toInt()}
            |Weakest Phase: ${m.weakestPhase}
            |
            |ASYMMETRY: ${m.asymmetryScore.toInt()}% symmetric
            |  Left edge: ${String.format("%.1f", m.leftEdgeAngleAvg)}deg, Right edge: ${String.format("%.1f", m.rightEdgeAngleAvg)}deg
            |  Weaker side: ${m.weakerSide} (${m.weakerSideMetric})
            |
            |FATIGUE: ${String.format("%.0f", m.fatigueIndicator)}% decline, trend ${String.format("%.0f", m.skiIQTrend)}
            |Snow: ${m.snowType}
            |Jumps: ${m.jumpCount}
            |ACL Risk Max: ${String.format("%.0f", m.aclRiskMax)}/100""".trimMargin()
    }

    private fun callLLM(prompt: String): String? {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.doOutput = true

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 500)
            put("temperature", 0.7)
        }

        conn.outputStream.write(body.toString().toByteArray())

        if (conn.responseCode != 200) {
            Log.e(TAG, "LLM API error: ${conn.responseCode}")
            return null
        }

        val response = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()

        return response
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
    }

    private fun parseResponse(text: String): AiInsight {
        return try {
            val json = JSONObject(text.trim().removePrefix("```json").removeSuffix("```").trim())
            AiInsight(
                summary = json.optString("summary", text),
                strengths = jsonArrayToList(json.optJSONArray("strengths")),
                improvements = jsonArrayToList(json.optJSONArray("improvements")),
                nextDrill = json.optString("nextDrill", "Practice your weakest skill"),
                motivation = json.optString("motivation", "Keep pushing — every run makes you better!"),
            )
        } catch (e: Exception) {
            AiInsight(
                summary = text.take(500),
                strengths = emptyList(),
                improvements = emptyList(),
                nextDrill = "",
                motivation = "",
            )
        }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
