package ir.jessi.finance.data

import ir.jessi.finance.ui.Jalali
import org.junit.Assert.*
import org.junit.Test

class DailyOverdueReminderTest {
    @Test fun overdueOlderThanGracePeriodStillNotified() {
        val old = Debt(id = 1, person = "قسط قدیمی", direction = "I_OWE",
            totalToman = 3000, installmentMonths = 3,
            firstInstallmentJalali = "1405/01/10")
        val result = FinanceReminderRules.installmentsDueOrOverdue(
            listOf(old), Jalali.Date(1405, 9, 12)
        )
        assertEquals(3, result.size)
        assertEquals(3000L, result.sumOf { it.amountToman })
    }

    @Test fun partialPaymentAndFullSettlementChangeOverdueBalance() {
        val debt = Debt(id = 2, person = "اقساط", direction = "I_OWE",
            totalToman = 3000, paidToman = 1500,
            installmentMonths = 3, firstInstallmentJalali = "1405/03/10")
        val list = FinanceReminderRules.installmentsDueOrOverdue(
            listOf(debt), Jalali.Date(1405, 8, 15)
        )
        assertEquals(2, list.size)
        assertEquals(1500L, list.sumOf { it.amountToman })
        assertTrue(FinanceReminderRules.installmentsDueOrOverdue(
            listOf(debt.copy(paidToman = 3000)), Jalali.Date(1405, 8, 15)
        ).isEmpty())
    }

    @Test fun futureInstallmentsNeverAlert() {
        val future = Debt(id = 7, person = "آینده", direction = "I_OWE",
            totalToman = 2500, installmentMonths = 5,
            firstInstallmentJalali = "1406/01/10")
        assertTrue(FinanceReminderRules.installmentsDueOrOverdue(
            listOf(future), Jalali.Date(1405, 10, 9)
        ).isEmpty())
    }
}
