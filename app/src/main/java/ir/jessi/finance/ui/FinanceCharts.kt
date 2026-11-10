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
                modifier = Modifier.weight(1f), color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
            Text("۶ دوره اخیر", fontSize = 10.sp, color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
        }
        if (shown.isEmpty() || (incomes + expenses).all { it == 0L }) {
            Text("هنوز تراکنشی برای نمایش روند ثبت نشده.", fontSize = 12.sp,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
        } else {
            val maximum = (incomes + expenses).maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
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
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("● درآمد", color = jessiReadableText(incomeColor), fontSize = 12.sp)
                Text("● هزینه", color = jessiReadableText(expenseColor), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SpendingCategoryChart(transactions: List<Transaction>) {
    val spent = transactions.filter { it.kind == "OUT" }
        .groupBy { it.category }
        .mapValues { (_, items) -> items.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }
    val top = spent.take(4)
    val rest = spent.drop(4).sumOf { it.second }
    val rows = if (rest > 0L) top + ("سایر" to rest) else top
    val total = rows.sumOf { it.second }
    val dark = MaterialTheme.colorScheme.background.red < .1f
    val colors = if (dark)
        listOf(Color(0xFF22C8FF), Color(0xFF5C7BFF), Color(0xFFFFCC62),
            Color(0xFFF253C9), Color(0xFFAA58FF))
    else listOf(Color(0xFF08AB67), Color(0xFF27A9F1), Color(0xFFFFBE4A),
        Color(0xFFF16FC3), Color(0xFFB478FD))
    val track = MaterialTheme.colorScheme.outline.copy(alpha = .3f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("تفکیک هزینه‌ها", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // In RTL the first column is on the right, matching the reference legend.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (rows.isEmpty()) {
                    Text("هنوز هزینه‌ای در این دوره ثبت نشده.",
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 11.sp)
                } else {
                    rows.forEachIndexed { index, (label, amount) ->
                        Row(
                            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(8.dp).background(colors[index], RoundedCornerShape(3.dp)))
                            Text(label, Modifier.weight(1f), fontSize = 11.sp,
                                maxLines = 1, color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                            Text(digitsFa((100 * amount / total.coerceAtLeast(1L)).toString()) + "٪",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
            }
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                    val ring = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                    drawArc(track, -90f, 360f, false, style = ring)
                    if (total > 0L) {
                        var angle = -90f
                        rows.forEachIndexed { i, item ->
                            val sweep = 360f * item.second.toFloat() / total
                            drawArc(colors[i], angle + 1f, (sweep - 2f).coerceAtLeast(0f),
                                false, style = ring)
                            angle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (total == 0L) "۰" else digitsFa(
                            java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(total)
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (total > 999_999_999L) 11.sp else 13.sp,
                        maxLines = 1, color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                    Text("تومان", fontSize = 10.sp, color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
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
        Text("برنامه اقساط ۶ ماه آینده", color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
            fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (upcoming.all { it == 0L }) {
            Text("قسطی با سررسید ۶ ماه آینده ثبت نشده.", fontSize = 12.sp,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
        } else {
            val max = upcoming.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
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
                        fontSize = 10.sp, color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            Text("مجموع اقساط پیش‌رو: " + toman(upcoming.sum()), fontSize = 12.sp, color = jessiReadableText(negative))
        }
    }
}
