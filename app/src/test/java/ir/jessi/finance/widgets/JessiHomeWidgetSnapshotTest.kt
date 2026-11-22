package ir.jessi.finance.widgets

import ir.jessi.finance.data.Account
import ir.jessi.finance.data.Budget
import ir.jessi.finance.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class JessiHomeWidgetSnapshotTest {
    @Test fun balanceTracksTransfersSavingsAndRepaymentsLikeAccountLedger() {
        val rows = listOf(
            Transaction(accountId = 1, amountToman = 1000L, kind = "IN", createdAt = 1000L),
            Transaction(accountId = 1, amountToman = 300L, kind = "OUT", createdAt = 1001L),
            Transaction(accountId = 1, amountToman = 200L, kind = "GOAL_OUT", createdAt = 1002L),
            Transaction(accountId = 1, amountToman = 150L, kind = "GOAL_IN", createdAt = 1003L),
            Transaction(accountId = 1, amountToman = 100L, kind = "DEBT_OUT", createdAt = 1004L)
        )
        val model = HomeWidgetSnapshot(listOf(Account(id = 1, title = "تست", openingToman = 500)),
            rows, emptyList(), 1000, 2000, "دوره", false)
        assertEquals(1050L, model.balance)
        assertEquals(1000L, model.income)
        assertEquals(300L, model.expenses)
    }

    @Test fun expenseChartUsesCurrentPeriodOnlyAndIgnoresRepaymentsTransfers() {
        val rows = listOf(
            Transaction(accountId = 1, amountToman = 800, kind = "OUT",
                category = "خوراک", createdAt = 1200),
            Transaction(accountId = 1, amountToman = 200, kind = "OUT",
                category = "سوخت", createdAt = 1300),
            Transaction(accountId = 1, amountToman = 1000, kind = "OUT",
                category = "خوراک", createdAt = 2900),
            Transaction(accountId = 1, amountToman = 700, kind = "TRANSFER_OUT",
                category = "خوراک", createdAt = 1300)
        )
        val model = HomeWidgetSnapshot(listOf(Account(id = 1, title = "تست")),
            rows, listOf(Budget(category = "خوراک", monthlyLimitToman = 1000)),
            1000, 2000, "دوره", true)
        assertEquals(1000L, model.expenses)
        assertEquals(listOf("خوراک" to 800L, "سوخت" to 200L), model.expensesByCategory)
    }
}
