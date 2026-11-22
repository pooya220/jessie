package ir.jessi.finance.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionSearchTest {
    private val accounts = listOf(
        Account(id = 1, title = "بانک سینا"),
        Account(id = 2, title = "مهر")
    )
    private val rows = listOf(
        Transaction(id = 1, accountId = 1, amountToman = 90000, kind = "OUT",
            category = "خوراک", note = "ناهار", createdAt = 100),
        Transaction(id = 2, accountId = 2, amountToman = 50000, kind = "IN",
            category = "حقوق", note = "دریافتی", createdAt = 200),
        Transaction(id = 3, accountId = 1, amountToman = 30000, kind = "TRANSFER_OUT",
            category = "انتقال", createdAt = 300),
        Transaction(id = 4, accountId = 2, amountToman = 30000, kind = "TRANSFER_IN",
            category = "انتقال", createdAt = 300)
    )

    @Test fun searchesPersianDigitsAmountAndAccountName() {
        assertEquals(listOf(1L),
            TransactionSearch.filter(rows, accounts, query = "۹۰۰۰۰").map { it.id })
        assertEquals(listOf(1L, 3L),
            TransactionSearch.filter(rows, accounts, query = "سینا").map { it.id })
    }

    @Test fun accountCategoryAndTypeCombineAndExcludeTransferDuplicate() {
        assertEquals(listOf(1L),
            TransactionSearch.filter(rows, accounts, type = TransactionSearch.EXPENSE,
                accountId = 1, category = "خوراک").map { it.id })
        assertEquals(listOf(3L),
            TransactionSearch.filter(rows, accounts, type = TransactionSearch.TRANSFER)
                .map { it.id })
        assertEquals(3, TransactionSearch.filter(rows, accounts).size)
    }
}
