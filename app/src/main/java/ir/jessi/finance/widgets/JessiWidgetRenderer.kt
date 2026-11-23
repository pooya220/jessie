package ir.jessi.finance.widgets

import android.content.Context
import ir.jessi.finance.data.Budget
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Native Android Canvas bitmap rendered into a RemoteViews ImageView.
 * Compatible with Android 8+ launchers; no fake figures or network requests.
 * The same card adapts to the actual app-widget bounds on resize.
 */
object JessiWidgetRenderer {
    private val lilac = Color.rgb(197, 143, 255)
    private val blue = Color.rgb(109, 163, 255)
    private val pink = Color.rgb(247, 135, 195)
    private val teal = Color.rgb(94, 213, 203)
    private val gold = Color.rgb(255, 195, 125)
    private val white = Color.rgb(246, 242, 255)
    private val muted = Color.rgb(185, 181, 210)
    private val track = Color.rgb(52, 49, 80)
    private val expenseColors = intArrayOf(lilac, blue, pink, teal, gold, Color.rgb(132, 139, 180))

    fun render(
        context: Context,
        kind: HomeWidgetKind,
        model: HomeWidgetSnapshot,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        // Small RemoteViews bitmaps avoid Binder transaction overflows when several
        // widgets update together; fitCenter (not fitXY) keeps the donut round.
        val scale = 1.2f
        val bitmap = Bitmap.createBitmap(
            (widthDp * scale).toInt().coerceAtLeast(1),
            (heightDp * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val w = widthDp.toFloat()
        val h = heightDp.toFloat()
        background(canvas, w, h)
        when (kind) {
            HomeWidgetKind.BALANCE -> balance(canvas, w, h, model)
            HomeWidgetKind.BUDGETS -> budgets(canvas, w, h, model)
            HomeWidgetKind.EXPENSES -> expenses(canvas, w, h, model)
            HomeWidgetKind.PAYMENTS, HomeWidgetKind.GOALS -> Unit
        }
        return bitmap
    }

    private fun background(c: Canvas, w: Float, h: Float) {
        val shape = RectF(1f, 1f, w - 1f, h - 1f)
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.rgb(32, 23, 54), Color.rgb(17, 22, 44),
                    Color.rgb(35, 19, 45)), null, Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(shape, 22f, 22f, outer)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 139, 111, 196)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        c.drawRoundRect(shape, 22f, 22f, outline)
        c.save()
        val clip = Path().apply { addRoundRect(shape, 22f, 22f, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawCircle(w * .86f, h * .02f, maxOf(w, h) * .52f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * .86f, h * .02f, maxOf(w, h) * .52f,
                    Color.argb(53, 125, 85, 241), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            })
        c.drawCircle(w * .14f, h * .96f, maxOf(w, h) * .43f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * .14f, h * .96f, maxOf(w, h) * .43f,
                    Color.argb(34, 252, 91, 158), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            })
        c.restore()
    }

    private fun text(
        c: Canvas, value: String, x: Float, y: Float,
        size: Float, color: Int = white, bold: Boolean = false,
        right: Boolean = true, maxWidth: Float = Float.MAX_VALUE
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        }
        var adjusted = value
        if (paint.measureText(adjusted) > maxWidth) {
            while (adjusted.length > 1 && paint.measureText("$adjusted…") > maxWidth) {
                adjusted = adjusted.dropLast(1)
            }
            adjusted += "…"
        }
        c.drawText(adjusted, x, y, paint)
    }

    private fun title(c: Canvas, w: Float, heading: String, period: String) {
        text(c, heading, w - 15f, 28f, 14f, white, true, maxWidth = w - 87f)
        text(c, period, 15f, 27f, 10f, muted, right = false, maxWidth = 65f)
        c.drawRoundRect(
            RectF(w - 39f, 35f, w - 15f, 37.3f),
            2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    w - 39f, 35f, w - 15f, 35f,
                    lilac, pink, Shader.TileMode.CLAMP
                )
            }
        )
    }

    private fun shortAmount(amount: Long): String {
        val absolute = abs(amount.toDouble())
        val persian = NumberFormat.getIntegerInstance(Locale("fa", "IR"))
        return when {
            absolute >= 1_000_000_000.0 -> formatDecimal(amount / 1_000_000_000.0) + " میلیارد"
            absolute >= 10_000_000.0 -> formatDecimal(amount / 1_000_000.0) + " میلیون"
            else -> persian.format(amount)
        }
    }

    private fun formatDecimal(value: Double): String {
        val f = NumberFormat.getNumberInstance(Locale("fa", "IR"))
        f.maximumFractionDigits = 1
        f.minimumFractionDigits = 0
        return f.format(value)
    }

    private fun balance(c: Canvas, w: Float, h: Float, m: HomeWidgetSnapshot) {
        title(c, w, m.displayedTitle, "")
        val amount = when {
            !m.showBalance -> "••••••"
            m.accounts.isEmpty() -> "بدون حساب"
            else -> shortAmount(m.displayedBalance)
        }
        val large = w >= 260f
        val y = if (h < 120f) 66f else 79f
        text(c, amount, w - 16f, y, if (large) 28f else 23f, white, true,
            maxWidth = w - 32f)
        if (h < 125f) {
            text(c, if (m.showBalance) "تومان" else "موجودی پنهان",
                w - 17f, h - 12f, 10f, muted, maxWidth = w - 92f)
            return
        }
        text(c, if (m.showBalance) "تومان • " +
                (if (m.selectedAccountIndex < 0) "جمع حساب‌ها" else "این حساب")
                else "با دکمه چشم نمایش بده",
            w - 17f, y + 19f, 10f, muted, maxWidth = w - 30f)
        if (h > 170f && large) {
            val width = (w - 45f) / 2f
            metric(c, 16f, 115f, width, "درآمد دوره", shortAmount(m.income), teal)
            metric(c, 29f + width, 115f, width, "هزینه دوره", shortAmount(m.expenses), pink)
            text(c, "دوره مالی: " + m.periodLabel,
                w - 17f, h - 15f, 10f, muted, maxWidth = w - 30f)
        } else {
            text(c, "درآمد " + shortAmount(m.income) + "  •  خرج " + shortAmount(m.expenses),
                w - 17f, h - 17f, 10f, muted, maxWidth = w - 30f)
        }
    }

    private fun metric(
        c: Canvas, x: Float, y: Float, width: Float,
        label: String, amount: String, accent: Int
    ) {
        c.drawRoundRect(RectF(x, y, x + width, y + 50f), 13f, 13f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 58, 55, 91) })
        text(c, label, x + width - 10f, y + 18f, 10f, muted, maxWidth = width - 20f)
        text(c, amount, x + width - 10f, y + 39f, 14f, accent, true,
            maxWidth = width - 20f)
    }

    private fun budgets(c: Canvas, w: Float, h: Float, m: HomeWidgetSnapshot) {
        title(c, w, "پیشرفت بودجه‌ها", m.periodLabel)
        if (m.budgets.isEmpty()) {
            empty(c, w, h, "هنوز سقف بودجه ثبت نکردی")
            return
        }
        // Respect the owner's selected budget IDs and preserve their order.
        // Five rows fit a 2×2 widget at ~155dp without cutting off the fifth.
        val rows = m.widgetBudgets.map { b ->
            val spent = m.periodTransactions.asSequence()
                .filter { it.kind == "OUT" && it.category == b.category }
                .sumOf { it.amountToman.coerceAtLeast(0L) }
            Triple(b, spent, if (b.monthlyLimitToman > 0L)
                spent.toDouble() / b.monthlyLimitToman else 0.0)
        }
        if (rows.isEmpty()) {
            empty(c, w, h, "در تنظیمات جسی بودجه انتخاب کن")
            return
        }
        val topY = 43f
        // 5×21.2dp + header 43dp fits the original 155dp size.
        val rowHeight = ((h - topY - 3f) / 5f).coerceAtMost(29f).coerceAtLeast(16f)
        val count = min(rows.size, ((h - topY) / rowHeight).toInt().coerceAtLeast(1))
        rows.take(count).forEachIndexed { i, (budget, _, share) ->
            val top = topY + i * rowHeight
            val percent = (share * 100).toInt().coerceAtMost(999)
            text(c, budget.category, w - 15f, top + 9f,
                10f, white, true, maxWidth = w * .64f)
            text(c, formatDecimal(percent.toDouble()) + "٪", 15f, top + 9f,
                9.5f, if (share >= .90) pink else blue, true, right = false)
            val left = 15f
            val right = w - 15f
            val bar = RectF(left, top + 13f, right, top + 17f)
            c.drawRoundRect(bar, 3f, 3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track })
            if (share > 0) {
                val filled = RectF(left, bar.top,
                    left + (right - left) * share.toFloat().coerceIn(0f, 1f), bar.bottom)
                c.drawRoundRect(filled, 3f, 3f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(left, 0f, right, 0f,
                            if (share >= 1.0) pink else lilac,
                            if (share >= .90) pink else blue,
                            Shader.TileMode.CLAMP)
                    })
            }
        }
    }

    private fun expenses(c: Canvas, w: Float, h: Float, m: HomeWidgetSnapshot) {
        title(c, w, "سهم هزینه‌ها", m.periodLabel)
        val data = m.expensesByCategory.filter { it.second > 0 }
        val total = data.sumOf { it.second }
        if (total <= 0L) {
            empty(c, w, h, "در این دوره هزینه‌ای ثبت نشده")
            return
        }
        val wide = w >= 280f
        val centerX = if (wide) min(105f, w * .31f) else w * .42f
        val centerY = if (h < 145f) h * .57f else h * .56f
        val radius = min(
            if (wide) 52f else 48f,
            (h - 59f).coerceAtLeast(40f) * .45f
        )
        val stroke = if (radius < 38f) 12f else 16f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }
        val arc = RectF(centerX - radius, centerY - radius,
            centerX + radius, centerY + radius)
        paint.color = track
        c.drawArc(arc, -90f, 360f, false, paint)
        val chart = data.take(5).toMutableList()
        if (data.size > 5) chart.add("سایر" to data.drop(5).sumOf { it.second })
        var angle = -90f
        chart.forEachIndexed { index, (_, amount) ->
            val sweep = 360f * amount.toDouble().div(total).toFloat()
            paint.color = expenseColors[index % expenseColors.size]
            c.drawArc(arc, angle, (sweep - .9f).coerceAtLeast(0f), false, paint)
            angle += sweep
        }
        paint.style = Paint.Style.FILL
        text(c, "هزینه", centerX + 16f, centerY - 3f, 10f, muted, maxWidth = 40f)
        text(c, shortAmount(total), centerX + 29f, centerY + 15f, 11.5f,
            white, true, maxWidth = 60f)
        if (wide) {
            val left = centerX + radius + 26f
            val right = w - 16f
            val maxRows = min(4, ((h - 60f) / 31f).toInt().coerceAtLeast(1))
            chart.take(maxRows).forEachIndexed { index, (category, amount) ->
                val y = 60f + index * 31f
                c.drawCircle(right - 3f, y - 3f, 4f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = expenseColors[index % expenseColors.size]
                    })
                text(c, category, right - 14f, y, 11f, white, maxWidth = w - left - 46f)
                text(c, formatDecimal(amount * 100.0 / total) + "٪",
                    left + 2f, y + 14f, 10f, muted, right = false,
                    maxWidth = (w - left) * .75f)
            }
        } else {
            val largest = data.first()
            text(c, largest.first + " " +
                formatDecimal(largest.second * 100.0 / total) + "٪",
                w - 16f, h - 11f, 10.5f, muted, maxWidth = w - 28f)
        }
    }

    private fun empty(c: Canvas, w: Float, h: Float, message: String) {
        text(c, message, w - 17f, h * .58f, 12f, muted,
            maxWidth = w - 32f)
        text(c, "برای ثبت، ویجت را لمس کن",
            w - 17f, h - 16f, 10f, lilac, maxWidth = w - 32f)
    }
}
