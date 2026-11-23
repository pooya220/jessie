package ir.jessi.finance.widgets

import ir.jessi.finance.data.Account
import ir.jessi.finance.data.Budget
import ir.jessi.finance.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JessiInteractiveWidgetTest {
    private val accounts = listOf(
        Account(id = 1, title = "سینا", openingToman = 1200),
        Account(id = 2, title = "مهر", openingToman = 200)
    )
    private val txs = listOf(
        Transaction(accountId = 1, amountToman = 100, kind = "IN", createdAt = 1100),
        Transaction(accountId = 2, amountToman = 75, kind = "OUT", createdAt = 1100)
    )

    @Test fun pageBetweenTotalAndIndividualBankBalances() {
        val model = HomeWidgetSnapshot(accounts, txs, emptyList(),
            1000, 2000, "دوره", showBalance = false)
        assertEquals(1425L, model.balance)
        assertEquals(1425L, model.displayedBalance)
        assertEquals("موجودی کل", model.displayedTitle)
        val first = model.copy(selectedAccountIndex = 0)
        assertEquals(1300L, first.displayedBalance)
        assertEquals("سینا", first.displayedTitle)
        val second = model.copy(selectedAccountIndex = 1)
        assertEquals(125L, second.displayedBalance)
        assertEquals("مهر", second.displayedTitle)
        assertNull(model.displayedAccount)
    }

    @Test fun fiveBudgetIDsStaySelectedAndOrdered() {
        val budgets = (1L..7L).map {
            Budget(id = it, category = "دسته " + it, monthlyLimitToman = 1000)
        }
        val model = HomeWidgetSnapshot(
            accounts, txs, budgets, 1000, 2000, "دوره", false,
            selectedBudgetIds = listOf(6, 2, 7, 1, 5, 3)
        )
        assertEquals(listOf(6L, 2L, 7L, 1L, 5L),
            model.widgetBudgets.map { it.id })
    }
}
