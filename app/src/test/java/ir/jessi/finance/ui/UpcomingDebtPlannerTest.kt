package ir.jessi.finance.ui

import ir.jessi.finance.data.Debt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingDebtPlannerTest {
    private fun epoch(raw: String): Long = JalaliDates.toEpoch(JalaliDates.parse(raw)!!)!!

    @Test fun countsOrdinaryDatedDebtsRegardlessOfTheirTitle() {
        val now = epoch("1405/08/19")
        val ordinary = Debt(person = "هزینه دانشگاه", direction = "I_OWE",
            totalToman = 3_000_000, paidToman = 500_000, dueDateJalali = "1405/09/20")
        val ordinary2 = Debt(person = "خرید", direction = "I_OWE",
            totalToman = 1_000_000, dueDateJalali = "۱۴۰۵/۰۹/۰۱")
        val result = UpcomingDebtPlanner.nextMonth(listOf(ordinary, ordinary2), now)
        assertEquals(3_500_000L, result.ordinaryDebtsToman)
        assertEquals(3_500_000L, result.totalToman)
        assertEquals(2, result.payments.size)
        assertTrue(result.payments.none { it.isInstallment })
    }

    @Test fun combinesOnlyNextMonthInstallmentsAndOrdinaryDebts() {
        val now = epoch("1405/08/19")
        val loan = Debt(person = "وام", direction = "I_OWE",
            totalToman = 1_200_000, installmentMonths = 6,
            firstInstallmentJalali = "1405/08/10")
        val ordinary = Debt(person = "دوست", direction = "I_OWE",
            totalToman = 2_000_000, paidToman = 400_000, dueDateJalali = "1405/09/11")
        val nowDebt = ordinary.copy(person = "این ماه", dueDateJalali = "1405/08/25")
        val laterDebt = ordinary.copy(person = "دو ماه دیگر", dueDateJalali = "1405/10/05")
        val received = ordinary.copy(person = "طلب", direction = "OWED_TO_ME")
        val undated = ordinary.copy(person = "بی‌تاریخ", dueDateJalali = "")
        val settled = ordinary.copy(person = "تسویه", paidToman = 2_000_000)
        val result = UpcomingDebtPlanner.nextMonth(
            listOf(loan, ordinary, nowDebt, laterDebt, received, undated, settled), now)
        assertEquals(200_000L, result.installmentsToman)
        assertEquals(1_600_000L, result.ordinaryDebtsToman)
        assertEquals(1_800_000L, result.totalToman)
        assertEquals(2, result.payments.size)
    }

    @Test fun handlesJalaliYearChangeForOrdinaryAndInstallmentDates() {
        val now = epoch("1405/12/15")
        val installment = Debt(person = "قسط", direction = "I_OWE", totalToman = 600_000,
            installmentMonths = 3, firstInstallmentJalali = "1406/01/03")
        val debt = Debt(person = "بدهی", direction = "I_OWE",
            totalToman = 400_000, dueDateJalali = "1406/01/20")
        val summary = UpcomingDebtPlanner.nextMonth(listOf(installment, debt), now)
        assertEquals(1, summary.jalaliMonth.month)
        assertEquals(1406, summary.jalaliMonth.year)
        assertEquals(600_000L, summary.totalToman)
    }

    @Test fun respectsPartialInstallmentPayment() {
        val now = epoch("1405/08/10")
        val loan = Debt(person = "وام", direction = "I_OWE", totalToman = 1_200_000,
            paidToman = 250_000, installmentMonths = 6,
            firstInstallmentJalali = "1405/08/15")
        val summary = UpcomingDebtPlanner.nextMonth(listOf(loan), now)
        assertEquals(150_000L, summary.installmentsToman)
    }
}
