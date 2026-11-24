package ir.jessi.finance.widgets

import ir.jessi.finance.data.Debt
import ir.jessi.finance.ui.Jalali
import org.junit.Assert.*
import org.junit.Test

class NextMonthInstallmentSelectorTest {
    @Test fun crossesEsfandToFarvardinAndExcludesCurrentMonth() {
        val a = Debt(id = 1, person = "دانشگاه", direction = "I_OWE",
            totalToman = 3000, installmentMonths = 3,
            firstInstallmentJalali = "1405/12/15")
        val result = NextMonthInstallmentSelector.select(
            listOf(a), Jalali.Date(1405, 12, 20)
        )
        assertEquals(1, result.size)
        assertEquals(Jalali.Date(1406, 1, 15), result.single().due)
        assertEquals(1000L, result.single().amountToman)
    }

    @Test fun ignoresPaidFutureAndReceivables() {
        val a = Debt(id = 2, person = "قسط", direction = "I_OWE",
            totalToman = 4000, paidToman = 2000,
            installmentMonths = 4, firstInstallmentJalali = "1405/10/10")
        val receivable = a.copy(id = 3, direction = "OWED_TO_ME")
        // Current Jalali 1405/11 => next is Esfand. December-equivalent
        // installment 1405/12/10 is the third scheduled payment.
        val result = NextMonthInstallmentSelector.select(
            listOf(a, receivable), Jalali.Date(1405, 11, 10))
        assertEquals(1, result.size)
        assertEquals(2, result.single().installmentIndex)
        assertEquals(1000L, result.single().amountToman)
    }

    @Test fun excludesOverdueAndFollowingMonthItems() {
        val a = Debt(id = 4, person = "قسط", direction = "I_OWE",
            totalToman = 3000, installmentMonths = 3,
            firstInstallmentJalali = "1405/10/05")
        val items = NextMonthInstallmentSelector.select(
            listOf(a), Jalali.Date(1405, 10, 15)
        )
        assertEquals(1, items.size)
        assertEquals(1, items.single().installmentIndex)
        assertEquals(11, items.single().due.month)
    }
}
