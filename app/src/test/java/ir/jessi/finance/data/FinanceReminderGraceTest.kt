package ir.jessi.finance.data

import ir.jessi.finance.ui.Jalali
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceReminderGraceTest {
    @Test fun reminderSurvivesOneDayOfAndroidBackgroundDelay() {
        val debt = Debt(id = 51, person = "قسط آزمایشی", direction = "I_OWE",
            totalToman = 300_000, installmentMonths = 1,
            firstInstallmentJalali = "1405/08/20")
        val now = Jalali.Date(1405, 8, 21)
        val late = FinanceReminderRules.installmentsDueWithGrace(listOf(debt), now)
        assertEquals(1, late.size)
        assertEquals(300_000L, late.single().amountToman)
        assertTrue(FinanceReminderRules.installmentsDueToday(listOf(debt), now).isEmpty())
    }

    @Test fun noBacklogForOldOrAlreadyPaidInstallment() {
        val debt = Debt(id = 51, person = "قسط آزمایشی", direction = "I_OWE",
            totalToman = 300_000, installmentMonths = 1,
            firstInstallmentJalali = "1405/08/20")
        assertTrue(FinanceReminderRules.installmentsDueWithGrace(
            listOf(debt), Jalali.Date(1405, 8, 26)).isEmpty())
        assertTrue(FinanceReminderRules.installmentsDueWithGrace(
            listOf(debt.copy(paidToman = 300_000)), Jalali.Date(1405, 8, 21)
        ).isEmpty())
    }
}
