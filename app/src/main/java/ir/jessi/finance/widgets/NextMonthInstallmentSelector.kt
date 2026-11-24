package ir.jessi.finance.widgets

import ir.jessi.finance.data.Debt
import ir.jessi.finance.ui.Installments
import ir.jessi.finance.ui.Jalali
import ir.jessi.finance.ui.JalaliDates

/** Pure selector, independent from Android UI; preserves year-end transitions. */
object NextMonthInstallmentSelector {
    data class DueItem(
        val debtId: Long,
        val person: String,
        val installmentIndex: Int,
        val totalCount: Int,
        val due: Jalali.Date,
        val amountToman: Long
    )

    fun select(debts: List<Debt>, today: Jalali.Date): List<DueItem> {
        val next = JalaliDates.addMonths(
            Jalali.Date(today.year, today.month, 1), 1
        )
        return buildList {
            for (debt in debts) {
                if (debt.direction != "I_OWE" || debt.totalToman <= debt.paidToman ||
                    debt.installmentMonths <= 0) continue
                val first = JalaliDates.parse(debt.firstInstallmentJalali) ?: continue
                for (i in Installments.countPaid(debt) until debt.installmentMonths) {
                    val due = JalaliDates.addMonths(first, i)
                    if (due.year != next.year || due.month != next.month) continue
                    val amount = (
                        Installments.cumulative(debt, i + 1) -
                        maxOf(Installments.cumulative(debt, i), debt.paidToman)
                    ).coerceAtLeast(0L)
                    if (amount > 0L) add(DueItem(
                        debt.id, debt.person, i, debt.installmentMonths, due, amount
                    ))
                }
            }
        }.sortedWith(compareBy<DueItem> { it.due.day }.thenBy { it.debtId })
    }
}
