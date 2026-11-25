package ir.jessi.finance.data

import ir.jessi.finance.ui.Installments
import ir.jessi.finance.ui.accountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebtPaymentReconciliationTest {
    @Test fun nextInstallmentOnlyAndPartialPayment() {
        val debt = Debt(id = 1, person = "loan", direction = "I_OWE",
            totalToman = 36_000_000L, installmentMonths = 12, paidToman = 9_000_000L)
        assertEquals(3_000_000L, debt.paymentPeriodRemaining())
        val partial = debt.copy(paidToman = 10_200_000L)
        assertEquals(1_800_000L, partial.paymentPeriodRemaining())
        assertEquals(3, Installments.countPaid(partial))
        assertEquals(4, Installments.countPaid(debt.copy(paidToman = 12_000_000L)))
        assertEquals(0L, debt.copy(paidToman = 36_000_000L).paymentPeriodRemaining())
    }

    @Test fun ordinaryDebtUsesRemainingAmount() {
        val debt = Debt(person = "debt", direction = "I_OWE",
            totalToman = 2_000_000L, paidToman = 1_200_000L)
        assertEquals(800_000L, debt.paymentPeriodRemaining())
        assertEquals(0L, debt.copy(paidToman = 2_000_000L).paymentPeriodRemaining())
    }

    @Test fun groupedAndLegacyPaymentsKeepDebtReferenceAndBalances() {
        val a = Transaction(id = 1, accountId = 1, amountToman = 1_200_000L, kind = "OUT")
        val b = Transaction(id = 2, accountId = 2, amountToman = 800_000L, kind = "OUT")
        val grouped = listOf(a, b).map { it.copy(kind = "DEBT_OUT", groupId = "DEBT_REF:7:batch") }
        assertEquals(7L, grouped.first().debtReferenceId())
        assertEquals(7L, a.copy(groupId = "DEBT_REF:7").debtReferenceId())
        assertNull(a.debtReferenceId())
        for (accountId in listOf(1L, 2L)) {
            assertEquals(accountBalance(accountId, 5_000_000L, listOf(a, b)),
                accountBalance(accountId, 5_000_000L, grouped))
        }
        assertEquals(2_000_000L, grouped.sumOf { it.amountToman })
    }
}
