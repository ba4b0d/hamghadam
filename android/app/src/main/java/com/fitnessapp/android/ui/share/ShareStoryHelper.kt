package com.fitnessapp.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareStoryHelper {

    fun shareDailyProgressStory(
        context: Context,
        displayName: String?,
        steps: Long,
        distanceKm: Double,
        caloriesKcal: Int,
    ) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Sun -> Ember Background Gradient (#FFBA08 -> #F15B2A)
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFFFFBA08.toInt(), 0xFFF15B2A.toInt(), 0xFFC8380A.toInt()),
                    floatArrayOf(0f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. White Card Container
            val cardPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                isAntiAlias = true
            }
            val cardRect = RectF(80f, 320f, width - 80f, height - 320f)
            canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

            // 3. Header Text
            val textPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            textPaint.color = 0xFFF15B2A.toInt()
            textPaint.textSize = 54f
            canvas.drawText("HAMPA · هم پا", width / 2f, 440f, textPaint)

            textPaint.color = 0xFF635A53.toInt()
            textPaint.textSize = 36f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val nameText = if (displayName.isNullOrBlank()) "Daily Progress" else "$displayName's Activity"
            canvas.drawText(nameText, width / 2f, 510f, textPaint)

            // 4. Hero Step Count
            textPaint.color = 0xFF1F1B18.toInt()
            textPaint.textSize = 120f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("%,d".format(steps), width / 2f, 760f, textPaint)

            textPaint.color = 0xFFF15B2A.toInt()
            textPaint.textSize = 44f
            canvas.drawText("STEPS TODAY 👟", width / 2f, 830f, textPaint)

            // 5. Divider Line
            val linePaint = Paint().apply {
                color = 0xFFF3EDE6.toInt()
                strokeWidth = 4f
            }
            canvas.drawLine(140f, 920f, width - 140f, 920f, linePaint)

            // 6. Distance & Calories Metrics
            textPaint.color = 0xFF1F1B18.toInt()
            textPaint.textSize = 64f
            canvas.drawText("%.2f km".format(distanceKm), width / 3f, 1060f, textPaint)
            canvas.drawText("$caloriesKcal kcal", (width / 3f) * 2f, 1060f, textPaint)

            textPaint.color = 0xFF635A53.toInt()
            textPaint.textSize = 32f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Distance 📍", width / 3f, 1120f, textPaint)
            canvas.drawText("Calories 🔥", (width / 3f) * 2f, 1120f, textPaint)

            // 7. Footer Branding
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textSize = 42f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("هم پا — باهم تا هدف 🚀", width / 2f, height - 200f, textPaint)

            textPaint.textSize = 32f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("api.hamghadam.ba4b0d.ir", width / 2f, height - 140f, textPaint)

            // Save image to cache
            val file = File(context.cacheDir, "story_share.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            // Launch Share Intent
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Progress Story").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

        } catch (e: Exception) {
            android.util.Log.e("ShareStoryHelper", "Failed to generate share story", e)
        }
    }
}
