package ir.jessi.finance.data

import ir.jessi.finance.ui.Jalali
import ir.jessi.finance.ui.digitsFa
import java.util.Calendar

/**
 * Financial periods are display/reporting boundaries, never database resets.
 * Salary mode changes periods only after a user-approved income tagged "حقوق".
 */
data class FinancePeriod(
    val start: Long,
    val endExclusive: Long,
    val label: String,
    val shortLabel: String
) {
    fun contains(timestamp: Long): Boolean = timestamp >= start && timestamp < endExclusive
}

object FinancePeriodEngine {
    const val MONTHLY = "monthly"
    const val SALARY = "salary"
    val modes = listOf(MONTHLY, SALARY)

    fun monthStart(epoch: Long): Long {
        val j = Jalali.of(epoch)
        return Calendar.getInstance().apply {
            timeInMillis = epoch
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1 - j.day)
        }.timeInMillis
    }

    fun previousMonthStart(start: Long): Long = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis.let(::monthStart)

    fun nextMonthStart(start: Long): Long = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_MONTH, 32)
    }.timeInMillis.let(::monthStart)

    fun monthly(now: Long, count: Int = 12): List<FinancePeriod> {
        var start = monthStart(now)
        var end = nextMonthStart(start)
        return List(count) {
            val date = Jalali.of(start)
            val result = FinancePeriod(
                start, end,
                "ماه ${digitsFa(date.month.toString())} / ${digitsFa(date.year.toString())}",
                digitsFa("${date.month}/${date.year}")
            )
            end = start
            start = previousMonthStart(start)
            result
        }
    }

    fun periods(transactions: List<Transaction>, mode: String, now: Long, count: Int = 12): List<FinancePeriod> {
        if (mode == SALARY) {
            val starts = transactions.asSequence()
                .filter { it.kind == "IN" && it.category == "حقوق" && it.createdAt <= now }
                .map { it.createdAt }.distinct().sortedDescending().take(count).toList()
            if (starts.isNotEmpty()) {
                return starts.mapIndexed { index, start ->
                    val date = Jalali.of(start)
                    FinancePeriod(
                        start = start,
                        endExclusive = if (index == 0) Long.MAX_VALUE else starts[index - 1],
                        label = "از حقوق ${date}",
                        shortLabel = digitsFa("${date.month}/${date.day}")
                    )
                }
            }
        }
        // Until the first verified salary is recorded, offer ordinary Jalali months.
        return monthly(now, count)
    }
}
