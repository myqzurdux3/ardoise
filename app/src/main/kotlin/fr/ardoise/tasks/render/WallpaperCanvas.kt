package fr.ardoise.tasks.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Draws a snapshot onto a bitmap sized for the lock screen.
 *
 * Kept free of Android context and of [android.app.WallpaperManager] so it can
 * be exercised in unit tests: give it a size, get a bitmap back.
 *
 * Every dimension is a fraction of the screen height, so the composition holds
 * from a compact phone to a tall foldable without a table of breakpoints.
 */
object WallpaperCanvas {

    /** The clock and date own the top of the lock screen; the list starts below. */
    private const val TOP_RESERVED = 0.40f
    private const val SIDE_MARGIN = 0.085f
    private const val LINE_HEIGHT = 0.0345f
    private const val TITLE_SIZE = 0.0155f
    private const val TASK_SIZE = 0.0205f
    private const val FOOTER_SIZE = 0.0125f
    private const val BULLET_RADIUS = 0.0026f

    fun render(
        snapshot: RenderSnapshot?,
        width: Int,
        height: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width, height)

        val margin = width * SIDE_MARGIN
        val available = width - margin * 2f
        var cursor = height * TOP_RESERVED

        cursor = drawHeader(canvas, snapshot, margin, cursor, available, height)

        if (snapshot == null || snapshot.isEmpty) {
            drawEmptyState(canvas, margin, cursor, height)
            return bitmap
        }

        val taskPaint = textPaint(height * TASK_SIZE, ArdoisePalette.CHALK, "sans-serif")
        val overduePaint = textPaint(height * TASK_SIZE, ArdoisePalette.OCHRE_SOFT, "sans-serif")
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bulletInset = width * 0.028f
        val textInset = margin + bulletInset * 1.9f

        snapshot.tasks.forEach { task ->
            val overdue = task.isOverdue(today)
            val paint = if (overdue) overduePaint else taskPaint
            bulletPaint.color = if (overdue) ArdoisePalette.OCHRE else ArdoisePalette.CHALK_DIM

            canvas.drawCircle(
                margin + bulletInset * 0.6f,
                cursor - height * TASK_SIZE * 0.32f,
                height * BULLET_RADIUS,
                bulletPaint,
            )

            val label = TextUtils.ellipsize(
                task.title,
                paint,
                available - bulletInset * 1.9f,
                TextUtils.TruncateAt.END,
            )
            canvas.drawText(label, 0, label.length, textInset, cursor, paint)
            cursor += height * LINE_HEIGHT
        }

        drawFooter(canvas, snapshot, margin, height, zone)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(ArdoisePalette.SLATE_DEEP)
        // A single off-centre glow keeps the slate from reading as flat black
        // once the system dims the lock screen.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.22f,
                height * 0.20f,
                height * 0.62f,
                intArrayOf(ArdoisePalette.SLATE_RAISED, ArdoisePalette.SLATE_DEEP),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
    }

    private fun drawHeader(
        canvas: Canvas,
        snapshot: RenderSnapshot?,
        margin: Float,
        top: Float,
        available: Float,
        height: Int,
    ): Float {
        val title = snapshot?.listTitle?.takeIf { it.isNotBlank() } ?: "ARDOISE"
        val headerPaint = textPaint(height * TITLE_SIZE, ArdoisePalette.OCHRE, "sans-serif-medium").apply {
            letterSpacing = 0.22f
        }
        val label = TextUtils.ellipsize(
            title.uppercase(),
            headerPaint,
            available,
            TextUtils.TruncateAt.END,
        )
        canvas.drawText(label, 0, label.length, margin, top, headerPaint)

        val rulePaint = Paint().apply {
            color = ArdoisePalette.CHALK_DIM
            alpha = 60
            strokeWidth = height * 0.0008f
        }
        val ruleY = top + height * 0.014f
        canvas.drawLine(margin, ruleY, margin + available * 0.42f, ruleY, rulePaint)

        return ruleY + height * 0.038f
    }

    private fun drawEmptyState(canvas: Canvas, margin: Float, top: Float, height: Int) {
        val paint = textPaint(height * TASK_SIZE, ArdoisePalette.CHALK_DIM, "sans-serif")
        canvas.drawText("Rien en attente.", margin, top, paint)
    }

    private fun drawFooter(
        canvas: Canvas,
        snapshot: RenderSnapshot,
        margin: Float,
        height: Int,
        zone: ZoneId,
    ) {
        val paint = textPaint(height * FOOTER_SIZE, ArdoisePalette.CHALK_DIM, "sans-serif").apply {
            alpha = 130
            letterSpacing = 0.08f
        }
        val stamp = if (snapshot.syncedAtEpochMs <= 0L) {
            "en attente de synchronisation"
        } else {
            val time = Instant.ofEpochMilli(snapshot.syncedAtEpochMs)
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            if (snapshot.stale) "hors ligne, $time" else "à $time"
        }
        canvas.drawText(stamp, margin, height * 0.945f, paint)
    }

    private fun textPaint(size: Float, color: Int, family: String): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(family, Typeface.NORMAL)
        }
}
