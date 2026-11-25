package ir.jessi.finance.data

import org.junit.Assert.*
import org.junit.Test

class SplitDebtAllocationTest {
    @Test fun oneLedgerRowKeepsDistinctAllocations() {
        val shares = mapOf(7L to 600L, 8L to 400L)
        val tx = Transaction(accountId = 1L, amountToman = 1000L, kind = "DEBT_OUT",
            groupId = encodeDebtAllocations(shares))
        assertEquals(shares, tx.debtAllocations())
        assertEquals(mapOf(7L to 1000L), tx.copy(groupId = "DEBT_REF:7:batch").debtAllocations())
        assertTrue(tx.copy(groupId = "DEBT_MULTI:7=600,7=400").debtAllocations().isEmpty())
        assertTrue(tx.copy(amountToman = 999L).debtAllocations().isEmpty())
    }

    @Test fun rejectUnbalancedAndInvalidAllocations() {
        assertTrue(validDebtAllocationTotal(mapOf(1L to 500L, 2L to 500L), 1000L))
        assertFalse(validDebtAllocationTotal(mapOf(1L to 500L), 1000L))
        assertFalse(validDebtAllocationTotal(mapOf(1L to 1001L, 2L to -1L), 1000L))
        assertFalse(validDebtAllocationTotal(mapOf(1L to Long.MAX_VALUE), 1000L))
    }

    @Test fun reeditingCreditsOldPaymentBack() {
        val debt = Debt(id = 1L, person = "A", direction = "I_OWE",
            totalToman = 1000L, paidToman = 1000L)
        assertEquals(400L, debt.editablePaymentLimit(400L))
        assertEquals(0L, debt.editablePaymentLimit(1001L))
    }
}
