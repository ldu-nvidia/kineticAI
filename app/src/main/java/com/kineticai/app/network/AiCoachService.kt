package com.kineticai.app.network

import android.util.Log
import com.kineticai.app.analysis.SkiMetrics
import com.kineticai.app.xcski.XCSkiMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI coaching service — deep personalized analysis via cloud LLM.
 *
 * Two primary features:
 *   1. Post-session analysis: rich coaching insights from full session data
 *   2. Conversational Q&A: ask questions about your performance with full history context
 *
 * Supports OpenAI, Anthropic, or any OpenAI-compatible endpoint.
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

        private const val SYSTEM_PROMPT_COACH = """You are KineticAI Coach, an expert sports technique analyst with deep knowledge of skiing biomechanics (PSIA Level 3), snowboarding (AASI Level 3), and cross-country skiing (CXC/USSA certified).

You have access to detailed sensor data from the athlete's session including IMU-derived metrics, GPS trajectories, microphone-based surface classification, and multi-segment kinematic chain data.

Your coaching style:
- Speak directly to the athlete in a warm, encouraging but honest tone
- Reference their SPECIFIC numbers — don't be vague
- Connect metrics to physical sensations ("that 22-degree edge angle means you're not feeling the ski bite into the snow yet")
- Identify patterns across sessions when history is available
- Prioritize the ONE thing that would improve their skiing most
- Adapt complexity to their level (don't tell a beginner about angulation, don't oversimplify for an expert)
- Use analogies that resonate with the sport level
- Be concise — athletes want actionable advice, not essays"""

        private const val SYSTEM_PROMPT_CHAT = """You are KineticAI Coach, a conversational AI assistant for athletes. You have access to the athlete's session data and history.

Rules:
- Answer questions about their technique, metrics, progress, and training
- Explain technical concepts when asked (what is angulation? what does G-force mean?)
- Compare sessions when asked
- Suggest drills and exercises
- Be honest about limitations of sensor-based analysis
- If asked about something outside your data, say so clearly
- Keep responses concise (2-4 paragraphs max) unless the user asks for detail
- Use their actual numbers, not generic advice"""
    }

    data class AiInsight(
        val summary: String,
        val strengths: List<String>,
        val improvements: List<String>,
        val nextDrill: String,
        val motivation: String,
    )

    data class ChatMessage(val role: String, val content: String)

    private val conversationHistory = mutableListOf<ChatMessage>()

    fun clearConversation() { conversationHistory.clear() }

    /**
     * Post-session AI analysis for alpine skiing / snowboarding.
     */
    suspend fun analyzeRun(
        metrics: SkiMetrics,
        feedback: List<String>,
        sessionHistory: List<String> = emptyList(),
    ): AiInsight? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null
        try {
            val messages = mutableListOf(
                ChatMessage("system", SYSTEM_PROMPT_COACH),
                ChatMessage("user", buildAlpinePrompt(metrics, feedback, sessionHistory)),
            )
            val response = callLLM(messages, maxTokens = 600) ?: return@withContext null
            parseInsightResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed", e)
            null
        }
    }

    /**
     * Post-session AI analysis for cross-country skiing.
     */
    suspend fun analyzeXCSession(
        metrics: XCSkiMetrics,
        feedback: List<String>,
        sessionHistory: List<String> = emptyList(),
    ): AiInsight? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null
        try {
            val messages = mutableListOf(
                ChatMessage("system", SYSTEM_PROMPT_COACH),
                ChatMessage("user", buildXCPrompt(metrics, feedback, sessionHistory)),
            )
            val response = callLLM(messages, maxTokens = 600) ?: return@withContext null
            parseInsightResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "XC AI analysis failed", e)
            null
        }
    }

    /**
     * Conversational Q&A — maintains conversation history for multi-turn context.
     */
    suspend fun chat(
        question: String,
        sessionContext: String,
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null
        try {
            if (conversationHistory.isEmpty()) {
                conversationHistory.add(ChatMessage("system", SYSTEM_PROMPT_CHAT))
                conversationHistory.add(ChatMessage("system",
                    "Here is the athlete's current/latest session data:\n$sessionContext"))
            }

            conversationHistory.add(ChatMessage("user", question))

            val response = callLLM(conversationHistory, maxTokens = 400)

            if (response != null) {
                conversationHistory.add(ChatMessage("assistant", response))
                // Keep history manageable — trim to last 20 messages + system
                if (conversationHistory.size > 22) {
                    val system = conversationHistory.take(2)
                    val recent = conversationHistory.takeLast(18)
                    conversationHistory.clear()
                    conversationHistory.addAll(system)
                    conversationHistory.addAll(recent)
                }
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "AI chat failed", e)
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
                val messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT_COACH),
                    ChatMessage("user", """Here are the session summaries from this week:
                        |
                        |${runSummaries.joinToString("\n---\n")}
                        |
                        |Provide a concise weekly progress report covering:
                        |1. Overall trend (improving/plateauing/declining)
                        |2. Biggest improvement this week
                        |3. Persistent weakness to focus on
                        |4. Recommended drill for next session
                        |5. One motivational observation
                        |
                        |Talk directly to the athlete. Be specific with numbers.""".trimMargin()),
                )
                callLLM(messages, maxTokens = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Weekly report failed", e)
                null
            }
        }

    private fun buildAlpinePrompt(
        metrics: SkiMetrics,
        feedback: List<String>,
        history: List<String>,
    ): String {
        val historyBlock = if (history.isNotEmpty()) {
            "\n\nPREVIOUS SESSIONS (most recent first):\n${history.take(5).joinToString("\n---\n")}"
        } else ""

        return """Analyze this ski/snowboard session and provide personalized coaching.

SESSION DATA:
${alpineMetricsToText(metrics)}

ON-DEVICE ANALYSIS NOTES:
${feedback.joinToString("\n")}
$historyBlock

Respond in this exact JSON format:
{
  "summary": "2-3 sentence personalized assessment referencing their specific numbers and what they mean physically",
  "strengths": ["specific strength with number", "another strength"],
  "improvements": ["most impactful improvement with drill suggestion", "second improvement", "third improvement"],
  "nextDrill": "specific drill name: brief description of why it targets their #1 weakness",
  "motivation": "one genuine, specific encouraging observation about their progress or effort"
}

Be specific to THEIR numbers. A 22-degree edge angle means something different from a 35-degree one. Reference turn phases if relevant. If asymmetry exists between left and right, address it directly."""
    }

    private fun buildXCPrompt(
        metrics: XCSkiMetrics,
        feedback: List<String>,
        history: List<String>,
    ): String {
        val historyBlock = if (history.isNotEmpty()) {
            "\n\nPREVIOUS SESSIONS:\n${history.take(5).joinToString("\n---\n")}"
        } else ""

        val techDist = metrics.techniqueDistribution.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key.shortName}: ${String.format("%.0f", it.value)}%" }

        return """Analyze this cross-country skiing session and provide personalized coaching.

SESSION DATA:
Stride Score: ${String.format("%.0f", metrics.strideScore)}/200
  Glide: ${String.format("%.0f", metrics.glideScore)}/100
  Power: ${String.format("%.0f", metrics.powerScore)}/100
  Symmetry: ${String.format("%.0f", metrics.symmetryScore)}/100
  Technique: ${String.format("%.0f", metrics.techniqueScore)}/100

Distance: ${String.format("%.1f", metrics.totalDistanceM / 1000f)} km
Duration: ${metrics.sessionDurationMs / 60000} min
Strides: ${metrics.totalStrides}
Avg Cadence: ${String.format("%.0f", metrics.avgCycleRate)} strides/min
Avg Stride Length: ${String.format("%.1f", metrics.avgCycleLength)} m
Avg Glide Ratio: ${String.format("%.0f", metrics.avgGlideRatio * 100)}%
L/R Symmetry: ${String.format("%.0f", metrics.avgLateralSymmetry * 100)}%
Elevation: +${String.format("%.0f", metrics.totalClimbM)}m / -${String.format("%.0f", metrics.totalDescentM)}m
Technique Distribution: $techDist
Fatigue: ${String.format("%.0f", metrics.fatigueFactor)}% cadence decline

ON-DEVICE ANALYSIS:
${feedback.joinToString("\n")}
$historyBlock

Respond in this exact JSON format:
{
  "summary": "2-3 sentence assessment of their XC session, referencing specific numbers",
  "strengths": ["strength 1", "strength 2"],
  "improvements": ["improvement 1 with drill", "improvement 2", "improvement 3"],
  "nextDrill": "specific XC drill targeting their biggest weakness",
  "motivation": "encouraging observation"
}

Focus on glide efficiency, technique-terrain matching, pacing, and symmetry."""
    }

    private fun alpineMetricsToText(m: SkiMetrics): String {
        return """Kinetic Score: ${m.skiIQ}/200
Turns: ${m.turnCount} (L:${m.leftTurnCount} R:${m.rightTurnCount})
Duration: ${m.runDurationFormatted}
Max Speed: ${String.format("%.1f", m.maxSpeedKmh)} km/h
Altitude Drop: ${String.format("%.0f", m.altitudeDrop)}m

BALANCE: ${m.balanceScore.toInt()}/100
  Early Forward: ${m.earlyForwardMovement.toInt()}, Centered: ${m.centeredBalance.toInt()}
  Boot Flex: ${String.format("%.1f", m.bootFlexAngle)}deg, Knee Flex: ${String.format("%.0f", m.kneeFlexion)}deg

EDGING: ${m.edgingScore.toInt()}/100
  Edge Angle: ${String.format("%.1f", m.edgeAngle)}deg, Early Edging: ${m.earlyEdging.toInt()}
  Edge Similarity: ${m.edgeSimilarity.toInt()}, Progressive: ${m.progressiveEdgeBuild.toInt()}
  Angulation: ${String.format("%.1f", m.ankleAngulation)}deg
  Carve Quality (audio): ${m.carveQualityScore.toInt()}/100

ROTARY: ${m.rotaryScore.toInt()}/100
  Turn Shape: ${m.turnShape.toInt()}, Parallel: ${m.parallelSkis.toInt()}
  Kinetic Chain Bottom-Up: ${String.format("%.0f", m.kinChainBottomUpPct)}%

PRESSURE: ${m.pressureScore.toInt()}/100
  G-Force: avg ${String.format("%.1f", m.gForce)}G, max ${String.format("%.1f", m.maxGForce)}G
  Weight Release: ${m.transitionWeightRelease.toInt()}, Smoothness: ${m.pressureSmoothness.toInt()}

TURN PHASES: Init ${m.phaseInitScore.toInt()} | SteerIn ${m.phaseSteerInScore.toInt()} | Apex ${m.phaseApexScore.toInt()} | SteerOut ${m.phaseSteerOutScore.toInt()} | Transition ${m.phaseTransitionScore.toInt()}

ASYMMETRY: ${m.asymmetryScore.toInt()}% symmetric
  Left edge: ${String.format("%.1f", m.leftEdgeAngleAvg)}deg, Right edge: ${String.format("%.1f", m.rightEdgeAngleAvg)}deg

FATIGUE: ${String.format("%.0f", m.fatigueIndicator)}% decline
Snow: ${m.snowType}
Jumps: ${m.jumpCount}"""
    }

    private fun callLLM(messages: List<ChatMessage>, maxTokens: Int = 500): String? {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.doOutput = true

        val msgArray = JSONArray()
        for (msg in messages) {
            msgArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", msgArray)
            put("max_tokens", maxTokens)
            put("temperature", 0.7)
        }

        conn.outputStream.write(body.toString().toByteArray())

        if (conn.responseCode != 200) {
            val error = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "unknown" }
            Log.e(TAG, "LLM API error ${conn.responseCode}: $error")
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

    private fun parseInsightResponse(text: String): AiInsight {
        return try {
            val cleaned = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val json = JSONObject(cleaned)
            AiInsight(
                summary = json.optString("summary", text),
                strengths = jsonArrayToList(json.optJSONArray("strengths")),
                improvements = jsonArrayToList(json.optJSONArray("improvements")),
                nextDrill = json.optString("nextDrill", "Practice your weakest skill"),
                motivation = json.optString("motivation", "Keep pushing — every session makes you better!"),
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
