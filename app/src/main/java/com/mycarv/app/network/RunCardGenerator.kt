package com.mycarv.app.network

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.mycarv.app.analysis.SkiMetrics
import java.io.File
import java.io.FileOutputStream

/**
 * Generates shareable run summary cards as images.
 * Creates a branded card with Ski:IQ, key metrics, and the MyCarv logo
 * that can be shared to Instagram, WhatsApp, etc.
 */
class RunCardGenerator(private val context: Context) {

    companion object {
        private const val CARD_WIDTH = 1080
        private const val CARD_HEIGHT = 1920
    }

    /**
     * Generate a run card image and return a shareable intent.
     */
    fun generateAndShare(metrics: SkiMetrics): Intent? {
        val bitmap = generateCard(metrics)
        val file = saveBitmap(bitmap) ?: return null
        return createShareIntent(file)
    }

    fun generateCard(metrics: SkiMetrics): Bitmap {
        val bmp = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background gradient (dark mountain theme)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#0C4A6E")),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        val white = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val accent = Paint().apply { color = Color.parseColor("#38BDF8"); isAntiAlias = true }
        val gray = Paint().apply { color = Color.parseColor("#94A3B8"); isAntiAlias = true }
        val green = Paint().apply { color = Color.parseColor("#4ADE80"); isAntiAlias = true }
        val orange = Paint().apply { color = Color.parseColor("#FB923C"); isAntiAlias = true }

        var y = 120f

        // App name
        white.textSize = 48f; white.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MyCarv", 80f, y, accent); y += 30f
        gray.textSize = 28f
        canvas.drawText("AI Ski Coach", 80f, y, gray); y += 100f

        // Ski:IQ (big)
        val iqColor = when {
            metrics.skiIQ >= 140 -> green
            metrics.skiIQ >= 115 -> accent
            metrics.skiIQ >= 90 -> orange
            else -> white
        }
        iqColor.textSize = 180f; iqColor.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${metrics.skiIQ}", 80f, y + 150f, iqColor)
        gray.textSize = 40f
        canvas.drawText("Ski:IQ / 200", 80f, y + 200f, gray)
        y += 280f

        // Level label
        val level = when {
            metrics.skiIQ >= 140 -> "EXPERT"
            metrics.skiIQ >= 115 -> "ADVANCED"
            metrics.skiIQ >= 90 -> "INTERMEDIATE"
            else -> "DEVELOPING"
        }
        accent.textSize = 36f; accent.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(level, 80f, y, accent); y += 80f

        // Category bars
        y = drawCategoryBar(canvas, "Balance", metrics.balanceScore, y, Color.parseColor("#38BDF8"))
        y = drawCategoryBar(canvas, "Edging", metrics.edgingScore, y, Color.parseColor("#4ADE80"))
        y = drawCategoryBar(canvas, "Rotary", metrics.rotaryScore, y, Color.parseColor("#FB923C"))
        y = drawCategoryBar(canvas, "Pressure", metrics.pressureScore, y, Color.parseColor("#FACC15"))
        y += 40f

        // Key stats
        white.textSize = 36f; white.typeface = Typeface.DEFAULT
        val stats = listOf(
            "Max Speed" to "${String.format("%.1f", metrics.maxSpeedKmh)} km/h",
            "Turns" to "${metrics.turnCount} (L:${metrics.leftTurnCount} R:${metrics.rightTurnCount})",
            "Duration" to metrics.runDurationFormatted,
            "Vertical" to "${String.format("%.0f", metrics.altitudeDrop)}m",
            "Max G-Force" to "${String.format("%.1f", metrics.maxGForce)}G",
            "Snow" to metrics.snowType,
        )

        stats.forEach { (label, value) ->
            gray.textSize = 28f
            canvas.drawText(label, 80f, y, gray)
            white.textSize = 32f; white.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(value, 500f, y, white)
            y += 55f
        }

        // Jumps
        if (metrics.jumpCount > 0) {
            y += 20f
            orange.textSize = 32f
            canvas.drawText("${metrics.jumpCount} jumps, ${String.format("%.1f", metrics.totalAirtimeMs / 1000f)}s airtime", 80f, y, orange)
            y += 50f
        }

        // Footer
        y = CARD_HEIGHT - 100f
        gray.textSize = 24f; gray.typeface = Typeface.DEFAULT
        canvas.drawText("Generated by MyCarv — AI Ski Coach", 80f, y, gray)

        return bmp
    }

    private fun drawCategoryBar(canvas: Canvas, label: String, score: Float, y: Float, color: Int): Float {
        val paint = Paint().apply { isAntiAlias = true }

        // Label
        paint.color = Color.WHITE; paint.textSize = 30f; paint.typeface = Typeface.DEFAULT
        canvas.drawText(label, 80f, y, paint)

        // Score
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${score.toInt()}", CARD_WIDTH - 150f, y, paint)

        // Bar background
        paint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(RectF(80f, y + 10f, CARD_WIDTH - 80f, y + 30f), 10f, 10f, paint)

        // Bar fill
        val fillWidth = (score / 100f) * (CARD_WIDTH - 160f)
        paint.color = color
        canvas.drawRoundRect(RectF(80f, y + 10f, 80f + fillWidth, y + 30f), 10f, 10f, paint)

        return y + 65f
    }

    private fun saveBitmap(bitmap: Bitmap): File? {
        return try {
            val dir = File(context.cacheDir, "share")
            dir.mkdirs()
            val file = File(dir, "mycarv_run.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Check out my ski run! #MyCarv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
