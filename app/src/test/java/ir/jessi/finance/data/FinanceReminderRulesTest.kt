package ir.jessi.finance.data

import ir.jessi.finance.ui.Jalali
import ir.jessi.finance.ui.JalaliDates
import org.junit.Assert.*
import org.junit.Test

class FinanceReminderRulesTest {
    private val period = FinancePeriod(
        1000L, 10000L, "current", "current"
    )

    @Test fun budgetOnlyWarnsAtNinetyPercentForExpensesInPeriod() {
        val b = Budget(id = 2, category = "خوراک", monthlyLimitToman = 1000)
        val tx = listOf(
            Transaction(id = 1, accountId = 1, amountToman = 899, kind = "OUT",
                category = "خوراک", createdAt = 1500),
            Transaction(id = 2, accountId = 1, amountToman = 5000, kind = "OUT",
                category = "خوراک", createdAt = 50),
            Transaction(id = 3, accountId = 1, amountToman = 500, kind = "IN",
                category = "خوراک", createdAt = 1500),
            Transaction(id = 4, accountId = 1, amountToman = 100, kind = "DEBT_OUT",
                category = "خوراک", createdAt = 1500)
        )
        assertTrue(FinanceReminderRules.budgetAlerts(listOf(b), tx, period).isEmpty())
        val reached = FinanceReminderRules.budgetAlerts(
            listOf(b), tx + Transaction(id = 5, accountId = 1, amountToman = 1,
                kind = "OUT", category = "خوراک", createdAt = 1600), period
        )
        assertEquals(1, reached.size)
        assertEquals(90, reached.single().percent)
        assertEquals("budget_2_1000", reached.single().key)
    }

    @Test fun budgetThresholdUsesCeilingWithoutRoundingDown() {
        val b = Budget(id = 1, category = "سایر", monthlyLimitToman = 11)
        val t = Transaction(accountId = 2, amountToman = 9, kind = "OUT",
            category = "سایر", createdAt = 2000)
        assertTrue(FinanceReminderRules.budgetAlerts(listOf(b), listOf(t), period).isEmpty())
        assertEquals(1, FinanceReminderRules.budgetAlerts(listOf(b),
            listOf(t.copy(amountToman = 10)), period).size)
    }

    @Test fun installmentAlertsOnlyForUnpaidDayAndOurDebt() {
        val day = Jalali.Date(1405, 8, 30)
        val debt = Debt(id = 7, person = "آزمایش", direction = "I_OWE",
            totalToman = 3000, paidToman = 1000, installmentMonths = 3,
            firstInstallmentJalali = "1405/07/30")
        val due = FinanceReminderRules.installmentsDueToday(listOf(debt), day)
        assertEquals(1, due.size)
        assertEquals(2, due.single().installmentNumber)
        assertEquals(1000L, due.single().amountToman)
        assertEquals("installment_7_2_1405_8_30", due.single().key)
        assertTrue(FinanceReminderRules.installmentsDueToday(
            listOf(debt.copy(paidToman = 2000)), day).isEmpty())
        assertTrue(FinanceReminderRules.installmentsDueToday(
            listOf(debt.copy(direction = "OWED_TO_ME")), day).isEmpty())
    }

    @Test fun overdueInstallmentDoesNotCreateDailyNotification() {
        val start = Jalali.Date(1405, 8, 20)
        val debt = Debt(id = 8, person = "الف", direction = "I_OWE",
            totalToman = 5000, installmentMonths = 5,
            firstInstallmentJalali = JalaliDates.format(start))
        assertTrue(FinanceReminderRules.installmentsDueToday(
            listOf(debt), Jalali.Date(1405, 8, 21)).isEmpty())
    }
}
