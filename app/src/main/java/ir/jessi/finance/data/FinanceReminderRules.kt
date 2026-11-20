package ir.jessi.finance.data

import ir.jessi.finance.ui.Installments
import ir.jessi.finance.ui.Jalali
import ir.jessi.finance.ui.JalaliDates

/**
 * Pure rules shared by foreground changes and background checks.
 * Reminders never modify a transaction, debt, budget or goal.
 */
object FinanceReminderRules {
    const val BUDGET_THRESHOLD_PERCENT = 90

    data class BudgetAlert(
        val budgetId: Long,
        val category: String,
        val spentToman: Long,
        val limitToman: Long,
        val periodStart: Long
    ) {
        val key: String get() = "budget_" + budgetId + "_" + periodStart
        val percent: Int get() =
            (100.0 * spentToman / limitToman).toInt().coerceIn(0, 9999)
    }

    data class InstallmentAlert(
        val debtId: Long,
        val person: String,
        val installmentNumber: Int,
        val installmentMonths: Int,
        val dueDate: Jalali.Date,
        val amountToman: Long
    ) {
        val key: String get() =
            "installment_" + debtId + "_" + installmentNumber + "_" +
                dueDate.year + "_" + dueDate.month + "_" + dueDate.day
    }

    /** Budget usage counts only OUT (not transfers, repayments or goal deposits). */
    fun budgetAlerts(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        period: FinancePeriod
    ): List<BudgetAlert> = budgets.mapNotNull { budget ->
        if (budget.monthlyLimitToman <= 0L) return@mapNotNull null
        val spent = transactions.asSequence()
            .filter { it.kind == "OUT" && it.category == budget.category &&
                period.contains(it.createdAt) }
            .sumOf { it.amountToman.coerceAtLeast(0L) }
        // Integer equivalent of ceil(90% of limit), no overflow from 90*limit.
        val threshold = budget.monthlyLimitToman -
            budget.monthlyLimitToman / 10L
        if (spent < threshold) null else BudgetAlert(
            budget.id, budget.category, spent, budget.monthlyLimitToman, period.start
        )
    }

    /** One alert for each unpaid installment whose SHAMSI date is today. */
    fun installmentsDueToday(
        debts: List<Debt>, today: Jalali.Date
    ): List<InstallmentAlert> = buildList {
        for (debt in debts) {
            if (debt.direction != "I_OWE" || debt.installmentMonths <= 0 ||
                debt.paidToman >= debt.totalToman || debt.totalToman <= 0L
            ) continue
            val first = JalaliDates.parse(debt.firstInstallmentJalali) ?: continue
            for (i in Installments.countPaid(debt) until debt.installmentMonths) {
                val date = JalaliDates.addMonths(first, i)
                if (date != today) continue
                val amount = (
                    Installments.cumulative(debt, i + 1) -
                    maxOf(Installments.cumulative(debt, i), debt.paidToman)
                ).coerceAtLeast(0L)
                if (amount > 0L) add(InstallmentAlert(
                    debt.id, debt.person, i + 1, debt.installmentMonths,
                    date, amount
                ))
            }
        }
    }
}
