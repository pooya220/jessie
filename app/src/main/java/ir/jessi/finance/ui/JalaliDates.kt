package ir.jessi.finance.ui

import ir.jessi.finance.data.Debt
import java.util.Calendar

/**
 * Jalali date operations with strict Gregorian round-trip validation.
 * Search is logarithmic over a bounded two-Gregorian-year range; uses local
 * midnight so DST changes do not shift due dates by a day.
 */
object JalaliDates {
    fun parse(raw: String): Jalali.Date? {
        val text = ir.jessi.finance.sms.BankSmsParser.normalizeDigits(raw.trim())
            .replace('-', '/')
        val match = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$").matchEntire(text) ?: return null
        val (y, m, d) = match.destructured
        val date = Jalali.Date(y.toInt(), m.toInt(), d.toInt())
        return date.takeIf { it.year in 1300..1600 && it.month in 1..12 &&
            it.day in 1..31 && toEpoch(it) != null }
    }

    fun toEpoch(target: Jalali.Date): Long? {
        if (target.year !in 1300..1600 || target.month !in 1..12 || target.day !in 1..31) return null
        val base = Calendar.getInstance().apply {
            clear()
            set(target.year + 621, Calendar.JANUARY, 1)
        }
        var low = 0
        var high = 735
        while (low <= high) {
            val mid = (low + high) / 2
            val cal = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, mid) }
            val test = Jalali.of(cal.timeInMillis)
            val cmp = compareValues(test.year, target.year).takeIf { it != 0 }
                ?: compareValues(test.month, target.month).takeIf { it != 0 }
                ?: compareValues(test.day, target.day)
            if (cmp == 0) return cal.timeInMillis
            if (cmp < 0) low = mid + 1 else high = mid - 1
        }
        return null
    }

    fun format(date: Jalali.Date): String =
        digitsFa("%04d/%02d/%02d".format(java.util.Locale.US, date.year, date.month, date.day))

    fun addMonths(date: Jalali.Date, offset: Int): Jalali.Date {
        val monthIndex = date.year * 12 + (date.month - 1) + offset
        val y = Math.floorDiv(monthIndex, 12)
        val m = Math.floorMod(monthIndex, 12) + 1
        // Clamp the day when transitioning into a shorter month (including Esfand).
        for (day in date.day.coerceAtMost(31) downTo 1) {
            val candidate = Jalali.Date(y, m, day)
            if (toEpoch(candidate) != null) return candidate
        }
        return Jalali.Date(y, m, 1)
    }

    fun monthDistance(from: Jalali.Date, to: Jalali.Date): Int =
        (to.year - from.year) * 12 + to.month - from.month
}

object Installments {
    fun countPaid(debt: Debt): Int {
        if (debt.installmentMonths <= 0) return 0
        return (1..debt.installmentMonths).count { cumulative(debt, it) <= debt.paidToman }
    }

    /** The first few installments receive one extra toman for uneven division. */
    fun cumulative(debt: Debt, through: Int): Long {
        val n = debt.installmentMonths.coerceAtLeast(1)
        val i = through.coerceIn(0, n)
        val base = debt.totalToman / n
        val remainder = debt.totalToman % n
        return i * base + minOf(i.toLong(), remainder)
    }

    fun remainingCount(debt: Debt): Int = (debt.installmentMonths - countPaid(debt)).coerceAtLeast(0)

    fun nextAmount(debt: Debt): Long {
        if (remainingCount(debt) == 0) return 0
        return (cumulative(debt, countPaid(debt) + 1) - debt.paidToman).coerceAtLeast(0)
    }

    fun nextDate(debt: Debt): Jalali.Date? {
        if (remainingCount(debt) == 0) return null
        val start = JalaliDates.parse(debt.firstInstallmentJalali) ?: return null
        return JalaliDates.addMonths(start, countPaid(debt))
    }

    fun dueInPeriod(debt: Debt, start: Long, endExclusive: Long): Long {
        if (debt.direction != "I_OWE" || debt.installmentMonths <= 0) return 0L
        val first = JalaliDates.parse(debt.firstInstallmentJalali) ?: return 0L
        val paid = countPaid(debt)
        var amount = 0L
        for (i in paid until debt.installmentMonths) {
            val epoch = JalaliDates.toEpoch(JalaliDates.addMonths(first, i)) ?: continue
            if (epoch >= start && epoch < endExclusive) {
                // Respect partial payments against the earliest unpaid installment.
                val due = (cumulative(debt, i + 1) - maxOf(cumulative(debt, i), debt.paidToman)).coerceAtLeast(0L)
                amount += due
            }
        }
        return amount
    }
}
