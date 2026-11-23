package ir.jessi.finance.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

/**
 * Render ONLY the antialiased transparent donut itself.
 * All backgrounds, titles, percentages, balances and controls are native
 * Android TextViews / ProgressBars / ImageViews inside RemoteViews.
 * No outer bitmap, double frame, bitmap scaling, or fake blur.
 */
object JessiWidgetRenderer {
    val sliceColors = intArrayOf(
        Color.rgb(190, 138, 255),
        Color.rgb(112, 173, 255),
        Color.rgb(246, 137, 190),
        Color.rgb(86, 216, 197)
    )

    fun renderDonut(amounts: List<Long>, pixels: Int = 240): Bitmap {
        val size = pixels.coerceIn(96, 384)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * .135f
            strokeCap = Paint.Cap.BUTT
        }
        val inset = paint.strokeWidth / 2f + size * .035f
        val ring = RectF(inset, inset, size - inset, size - inset)
        paint.color = Color.rgb(55, 49, 79)
        canvas.drawArc(ring, -90f, 360f, false, paint)

        val total = amounts.filter { it > 0L }.sumOf { it.toDouble() }
        if (total <= 0.0) return bitmap

        var current = -90f
        amounts.forEachIndexed { i, amount ->
            if (amount <= 0L) return@forEachIndexed
            val sweep = 360.0 * amount.toDouble() / total
            paint.color = sliceColors[i % sliceColors.size]
            // Small gaps make the donut crisp instead of a flat painted disc.
            val gap = min(1.4, sweep * .16).toFloat()
            canvas.drawArc(
                ring, current + gap / 2f, (sweep.toFloat() - gap).coerceAtLeast(0f),
                false, paint
            )
            current += sweep.toFloat()
        }
        return bitmap
    }
}
