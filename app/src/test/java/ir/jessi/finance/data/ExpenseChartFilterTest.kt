package ir.jessi.finance.data

import org.junit.Assert.*
import org.junit.Test

class ExpenseChartFilterTest {
    @Test fun debtPaymentsCountAsExpensesButChartCanHideThem() {
        val rows = listOf(
            Transaction(id = 1, accountId = 1, amountToman = 100, kind = "OUT", category = "خوراک"),
            Transaction(id = 2, accountId = 1, amountToman = 200, kind = "DEBT_OUT", category = "بازپرداخت بدهی"),
            Transaction(id = 3, accountId = 1, amountToman = 300, kind = "TRANSFER_OUT"),
            Transaction(id = 4, accountId = 1, amountToman = 400, kind = "DEBT_IN")
        )
        assertEquals(300L, rows.filter { it.isExpense() }.sumOf { it.amountToman })
        assertEquals(listOf(1L, 2L), TransactionSearch.filter(rows, emptyList(), type = TransactionSearch.EXPENSE).map { it.id })
        assertEquals(listOf(2L), TransactionSearch.filter(rows, emptyList(), category = DEBT_EXPENSE_CATEGORY).map { it.id })
        assertEquals(listOf("خوراک" to 100L), expenseBreakdown(rows, setOf(DEBT_EXPENSE_CATEGORY)))
        assertEquals(300L, expenseBreakdown(rows, emptySet()).sumOf { it.second })
        assertTrue(expenseBreakdown(rows, setOf("خوراک", DEBT_EXPENSE_CATEGORY)).isEmpty())
    }
}
