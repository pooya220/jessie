package ir.jessi.finance.data

/** Search/filter is read-only and scoped to the already selected financial period. */
object TransactionSearch {
    const val ALL = "ALL"
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
    const val TRANSFER = "TRANSFER"
    const val DEBT = "DEBT"
    const val GOAL = "GOAL"

    private fun normalize(raw: String): String = buildString(raw.length) {
        for (c in raw.trim().lowercase()) append(when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            'ي' -> 'ی'
            'ك' -> 'ک'
            else -> c
        })
    }

    fun filter(
        transactions: List<Transaction>,
        accounts: List<Account>,
        query: String = "",
        type: String = ALL,
        accountId: Long? = null,
        category: String? = null
    ): List<Transaction> {
        val needle = normalize(query)
        val accountNames = accounts.associateBy { it.id }
        return transactions.asSequence().filter { it.kind != "TRANSFER_IN" }
            .filter {
                when (type) {
                    INCOME -> it.kind == "IN"
                    EXPENSE -> it.kind == "OUT"
                    TRANSFER -> it.kind == "TRANSFER_OUT"
                    DEBT -> it.kind == "DEBT_IN" || it.kind == "DEBT_OUT"
                    GOAL -> it.kind == "GOAL_IN" || it.kind == "GOAL_OUT"
                    else -> true
                }
            }.filter { accountId == null || it.accountId == accountId }
            .filter { category == null || it.category == category }
            .filter { row ->
                needle.isEmpty() || listOf(
                    row.category, row.note,
                    accountNames[row.accountId]?.title.orEmpty(),
                    accountNames[row.accountId]?.bank.orEmpty(),
                    row.amountToman.toString()
                ).any { normalize(it).contains(needle) }
            }.toList()
    }
}
