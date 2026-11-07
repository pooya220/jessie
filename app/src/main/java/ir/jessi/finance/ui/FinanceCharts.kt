package ir.jessi.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ir.jessi.finance.data.*

private val lightPositive = Color(0xFF009B73)
private val darkPositive = Color(0xFF12E2C5)
private val lightNegative = Color(0xFFE34D76)
private val darkNegative = Color(0xFFFF66AB)

@Composable
fun FinancialTrendChart(periods: List<FinancePeriod>, selectedIndex: Int, transactions: List<Transaction>) {
    val dark = MaterialTheme.colorScheme.background.red < .1f
    val incomeColor = if (dark) darkPositive else lightPositive
    val expenseColor = if (dark) darkNegative else lightNegative
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)
    val shown = periods.drop(selectedIndex).take(6).reversed()
    val incomes = shown.map { p -> transactions.filter { p.contains(it.createdAt) && it.kind == "IN" }.sumOf { it.amountToman } }
    val expenses = shown.map { p -> transactions.filter { p.contains(it.createdAt) && it.kind == "OUT" }.sumOf { it.amountToman } }
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("روند درآمد و هزینه", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text("۶ دوره اخیر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (shown.isEmpty() || (incomes + expenses).all { it == 0L }) {
            Text("هنوز تراکنشی برای نمایش روند ثبت نشده.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val maximum = (incomes + expenses).maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val column = size.width / shown.size
                val plotHeight = size.height - 4.dp.toPx()
                for (i in shown.indices) {
                    val baseX = i * column + column * .19f
                    val bar = column * .25f
                    val inHeight = plotHeight * (incomes[i].toFloat() / maximum)
                    val outHeight = plotHeight * (expenses[i].toFloat() / maximum)
                    val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    drawRoundRect(trackColor,
                        topLeft = Offset(baseX, 0f), size = Size(bar, plotHeight), cornerRadius = radius)
                    drawRoundRect(incomeColor, topLeft = Offset(baseX, plotHeight - inHeight),
                        size = Size(bar, inHeight), cornerRadius = radius)
                    drawRoundRect(expenseColor, topLeft = Offset(baseX + bar + column * .07f, plotHeight - outHeight),
                        size = Size(bar, outHeight), cornerRadius = radius)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                shown.forEach { p ->
                    Text(p.shortLabel, Modifier.weight(1f), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("● درآمد", color = incomeColor, fontSize = 12.sp)
                Text("● هزینه", color = expenseColor, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SpendingCategoryChart(transactions: List<Transaction>) {
    val totals = transactions.filter { it.kind == "OUT" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }
    val biggest = totals.take(4)
    val rest = totals.drop(4).sumOf { it.second }
    val rows = if (rest > 0L) biggest + ("سایر موارد" to rest) else biggest
    val total = rows.sumOf { it.second }
    val chartColors = listOf(
        Color(0xFF16BD91), Color(0xFF32A8FF), Color(0xFFFFC15C),
        Color(0xFFF271B6), Color(0xFFA979FF)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("تفکیک هزینه‌ها", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        if (rows.isEmpty()) {
            Text("هنوز هزینه‌ای در این دوره ثبت نشده.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                        val stroke = Stroke(width = 23.dp.toPx(), cap = StrokeCap.Butt)
                        drawArc(Color(0xFFD5E0E9).copy(alpha = .24f), -90f, 360f, false, style = stroke)
                        var angle = -90f
                        rows.forEachIndexed { i, row ->
                            val sweep = 360f * row.second.toFloat() / total.coerceAtLeast(1L)
                            drawArc(chartColors[i], angle + 1.2f, (sweep - 2.4f).coerceAtLeast(0f),
                                false, style = stroke)
                            angle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مجموع", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(digitsFa((total / 1_000_000.0).let {
                            String.format(java.util.Locale.US, "%.1f", it)
                        }) + " م", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("تومان", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    rows.forEachIndexed { i, (name, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(chartColors[i], RoundedCornerShape(3.dp)))
                            Text(name, Modifier.weight(1f), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(digitsFa(((100 * value) / total.coerceAtLeast(1L)).toString()) + "٪",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallmentForecastChart(debts: List<Debt>, now: Long) {
    val negative = if (MaterialTheme.colorScheme.background.red < .1f) darkNegative else lightNegative
    val start = FinancePeriodEngine.monthStart(now)
    val periods = buildList {
        var month = start
        repeat(6) {
            val end = FinancePeriodEngine.nextMonthStart(month)
            add(FinancePeriod(month, end, "", Jalali.of(month).month.toString()))
            month = end
        }
    }
    val upcoming = periods.map { p -> debts.sumOf { Installments.dueInPeriod(it, p.start, p.endExclusive) } }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("برنامه اقساط ۶ ماه آینده", color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (upcoming.all { it == 0L }) {
            Text("قسطی با سررسید ۶ ماه آینده ثبت نشده.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val max = upcoming.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(83.dp)) {
                val col = size.width / 6
                upcoming.forEachIndexed { i, amount ->
                    val height = size.height * (amount.toFloat() / max)
                    drawRoundRect(negative,
                        topLeft = Offset(i * col + col * .2f, size.height - height),
                        size = Size(col * .6f, height.coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                periods.forEach { p ->
                    Text(digitsFa(p.shortLabel), Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("مجموع اقساط پیش‌رو: " + toman(upcoming.sum()), fontSize = 12.sp, color = negative)
        }
    }
}
