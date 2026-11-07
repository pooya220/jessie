package ir.jessi.finance.ui

import ir.jessi.finance.data.Debt
import ir.jessi.finance.data.FinancePeriodEngine
import ir.jessi.finance.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FinancePeriodsTest {
    @Test fun monthlyPeriodsAreContiguous() {
        val now = System.currentTimeMillis()
        val ranges = FinancePeriodEngine.monthly(now, 3)
        assertEquals(3, ranges.size)
        assertTrue(ranges[0].contains(now))
        assertEquals(ranges[0].start, ranges[1].endExclusive)
        assertEquals(ranges[1].start, ranges[2].endExclusive)
    }

    @Test fun salaryCreditStartsNextPeriodOnlyWhenMarkedAsSalary() {
        val now = System.currentTimeMillis()
        val payday = now - 100000
        val regular = Transaction(accountId = 1L, amountToman = 10L, kind = "IN",
            category = "درآمد", createdAt = payday)
        val salary = regular.copy(id = 2L, category = "حقوق")
        val first = FinancePeriodEngine.periods(listOf(regular), FinancePeriodEngine.SALARY, now)
        assertTrue(first[0].start != payday)
        val after = FinancePeriodEngine.periods(listOf(regular, salary), FinancePeriodEngine.SALARY, now)
        assertEquals(payday, after[0].start)
    }

    @Test fun installmentPaymentsAndUpcomingDateAreCalculated() {
        val first = "۱۴۰۵/۰۹/۱۵"
        val loan = Debt(person = "وام", direction = "I_OWE", totalToman = 1200000L,
            installmentMonths = 6, firstInstallmentJalali = first)
        assertEquals(200000L, Installments.nextAmount(loan))
        assertEquals(6, Installments.remainingCount(loan))
        val after = loan.copy(paidToman = 250000L)
        assertEquals(1, Installments.countPaid(after))
        assertEquals(150000L, Installments.nextAmount(after))
        assertEquals(5, Installments.remainingCount(after))
        assertEquals("۱۴۰۵/۱۰/۱۵", JalaliDates.format(Installments.nextDate(after)!!))
    }

    @Test fun invalidJalaliDateIsRejected() {
        assertNull(JalaliDates.parse("۱۴۰۵/۱۳/۱۵"))
        assertNotNull(JalaliDates.parse("۱۴۰۵/۰۹/۱۵"))
    }
}
