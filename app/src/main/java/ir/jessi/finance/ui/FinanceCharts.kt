package ir.jessi.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jessi.finance.data.Debt
import ir.jessi.finance.data.FinancePeriod
import ir.jessi.finance.data.FinancePeriodEngine
import ir.jessi.finance.data.Transaction

private val incomeColor = Color(0xFF8DE5BE)
private val expenseColor = Color(0xFFFF9B9B)
private val faint = Color(0xFF263A3B)

@Composable
fun FinancialTrendChart(periods: List<FinancePeriod>, selectedIndex: Int, transactions: List<Transaction>) {
    val shown = periods.drop(selectedIndex).take(6).reversed()
    val incomes = shown.map { p -> transactions.filter { p.contains(it.createdAt) && it.kind == "IN" }.sumOf { it.amountToman } }
    val expenses = shown.map { p -> transactions.filter { p.contains(it.createdAt) && it.kind == "OUT" }.sumOf { it.amountToman } }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("روند درآمد و هزینه", color = MaterialTheme.colorScheme.onSurface)
        if (shown.isEmpty() || (incomes + expenses).all { it == 0L }) {
            Text("هنوز تراکنشی برای نمایش روند ثبت نشده.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val maximum = (incomes + expenses).maxOrNull()?.coerceAtLeast(1) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(142.dp)) {
                val column = size.width / shown.size
                val plotHeight = size.height - 12.dp.toPx()
                for (index in shown.indices) {
                    val x = index * column + column * .23f
                    val barWidth = column * .23f
                    val hIn = plotHeight * (incomes[index].toFloat() / maximum)
                    val hOut = plotHeight * (expenses[index].toFloat() / maximum)
                    drawRoundRect(faint, topLeft = Offset(x, 0f), size = Size(barWidth, plotHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    drawRoundRect(incomeColor, topLeft = Offset(x, plotHeight - hIn), size = Size(barWidth, hIn.coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    drawRoundRect(expenseColor, topLeft = Offset(x + barWidth + column * .06f, plotHeight - hOut),
                        size = Size(barWidth, hOut.coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                shown.forEach { period ->
                    Text(period.shortLabel, Modifier.weight(1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("● درآمد", color = incomeColor, fontSize = 12.sp)
                Text("● هزینه", color = expenseColor, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SpendingCategoryChart(transactions: List<Transaction>) {
    val rows = transactions.filter { it.kind == "OUT" }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("هزینه‌ها به تفکیک دسته", color = MaterialTheme.colorScheme.onSurface)
        if (rows.isEmpty()) {
            Text("هنوز هزینه‌ای در این دوره ثبت نشده.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val max = rows.maxOf { it.second }.coerceAtLeast(1L)
            rows.forEach { (category, amount) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category, fontSize = 12.sp)
                    Text(toman(amount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { (amount.toFloat() / max).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = incomeColor, trackColor = faint
                )
            }
        }
    }
}

@Composable
fun InstallmentForecastChart(debts: List<Debt>, now: Long) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("برنامه اقساط ۶ ماه آینده", color = MaterialTheme.colorScheme.onSurface)
        if (upcoming.all { it == 0L }) {
            Text("قسطی با سررسید ۶ ماه آینده ثبت نشده.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val max = upcoming.maxOrNull()?.coerceAtLeast(1) ?: 1L
            Canvas(Modifier.fillMaxWidth().height(98.dp)) {
                val col = size.width / 6
                upcoming.forEachIndexed { i, amount ->
                    val height = size.height * (amount.toFloat() / max)
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(i * col + col * .2f, size.height - height),
                        size = Size(col * .6f, height.coerceAtLeast(0f)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                periods.forEach { p ->
                    Text(digitsFa(p.shortLabel), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("در مجموع: ${toman(upcoming.sum())}", fontSize = 12.sp, color = expenseColor)
        }
    }
}
