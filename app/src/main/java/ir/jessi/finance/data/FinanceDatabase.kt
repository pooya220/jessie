package ir.jessi.finance.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val bank: String = "",
    val last4: String = "",
    val openingToman: Long = 0L
)

@Entity(tableName = "transactions", indices = [Index("accountId"), Index("dedupKey", unique = true)])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amountToman: Long,
    val kind: String, // IN, OUT, TRANSFER_IN, TRANSFER_OUT, DEBT_IN, DEBT_OUT
    val category: String = "سایر",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
    val groupId: String? = null,
    val dedupKey: String? = null
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: String, // I_OWE or OWED_TO_ME
    val totalToman: Long,
    val paidToman: Long = 0,
    val dueDateJalali: String = "",
    val note: String = "",
    @ColumnInfo(defaultValue = "0") val installmentMonths: Int = 0,
    @ColumnInfo(defaultValue = "''") val firstInstallmentJalali: String = ""
)

/** A debt reference may also carry a unique batch ID; old records remain valid. */
fun Transaction.debtReferenceId(): Long? = groupId?.takeIf { it.startsWith("DEBT_REF:") }
    ?.removePrefix("DEBT_REF:")?.substringBefore(':')?.toLongOrNull()

fun Debt.paymentPeriodRemaining(): Long =
    if (installmentMonths > 0) ir.jessi.finance.ui.Installments.nextAmount(this)
    else (totalToman - paidToman).coerceAtLeast(0L)

@Entity(tableName = "budgets", indices = [Index(value = ["category"], unique = true)])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val monthlyLimitToman: Long
)

@Entity(tableName = "sms_reviews", indices = [Index(value = ["fingerprint"], unique = true)])
data class SmsReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val amountToman: Long,
    val kind: String,
    val receivedAt: Long,
    val fingerprint: String,
    val accountGuess: Long? = null,
    @ColumnInfo(defaultValue = "0") val amountRaw: Long = 0L,
    @ColumnInfo(defaultValue = "'TOMAN'") val amountUnit: String = "TOMAN",
    @ColumnInfo(defaultValue = "''") val bankName: String = ""
)

@Entity(tableName = "sms_ignored")
data class IgnoredSms(@PrimaryKey val fingerprint: String)

/** Simulations are opt-in financial entries with explicit origin for later review/removal. */
object SmsReviewPolicy {
    const val TEST_PREFIX = "JESSI_TEST_"
    const val TEST_SOURCE = "SMS_TEST"
    fun isSimulation(fingerprint: String): Boolean = fingerprint.startsWith(TEST_PREFIX)
    fun sourceFor(fingerprint: String): String = if (isSimulation(fingerprint)) TEST_SOURCE else "SMS"
    fun noteFor(fingerprint: String, note: String, sender: String): String {
        val details = note.trim().ifBlank {
            if (isSimulation(fingerprint)) "شبیه‌سازی پیامک بانکی" else "پیامک بانکی • $sender"
        }
        return if (isSimulation(fingerprint)) "آزمایشی • $details" else details
    }
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id DESC") fun watchAccounts(): Flow<List<Account>>
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, id DESC") fun watchTransactions(): Flow<List<Transaction>>
    @Query("SELECT * FROM debts ORDER BY id DESC") fun watchDebts(): Flow<List<Debt>>
    @Query("SELECT * FROM budgets ORDER BY category") fun watchBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM sms_reviews ORDER BY receivedAt DESC") fun watchReviews(): Flow<List<SmsReview>>
    @Query("SELECT * FROM accounts") suspend fun getAccounts(): List<Account>
    @Query("SELECT * FROM transactions") suspend fun getTransactions(): List<Transaction>
    @Query("SELECT * FROM debts") suspend fun getDebts(): List<Debt>
    @Query("SELECT * FROM debts WHERE id=:id LIMIT 1") suspend fun findDebt(id: Long): Debt?
    @Query("SELECT * FROM transactions WHERE id=:id LIMIT 1") suspend fun findTransaction(id: Long): Transaction?
    @Query("SELECT COUNT(*) FROM goal_movements WHERE transactionId=:transactionId")
    suspend fun countGoalMovementsForTransaction(transactionId: Long): Int
    @Query("SELECT * FROM budgets") suspend fun getBudgets(): List<Budget>
    @Query("SELECT * FROM sms_reviews") suspend fun getSmsReviews(): List<SmsReview>
    @Query("SELECT * FROM sms_ignored") suspend fun getIgnoredSms(): List<IgnoredSms>
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC, id DESC")
    fun watchGoals(): Flow<List<SavingsGoal>>
    @Query("SELECT * FROM goal_movements ORDER BY createdAt DESC, id DESC")
    fun watchGoalMovements(): Flow<List<GoalMovement>>
    @Query("SELECT * FROM savings_goals") suspend fun getGoals(): List<SavingsGoal>
    @Query("SELECT * FROM goal_movements") suspend fun getGoalMovements(): List<GoalMovement>
    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun findGoal(id: Long): SavingsGoal?
    @Query("SELECT IFNULL(SUM(CASE WHEN operation='DEPOSIT' THEN amountToman ELSE -amountToman END),0) FROM goal_movements WHERE goalId=:id")
    suspend fun goalBalance(id: Long): Long
    @Query("SELECT openingToman + IFNULL((SELECT SUM(CASE WHEN kind IN ('IN','TRANSFER_IN','DEBT_IN','GOAL_IN') THEN amountToman ELSE -amountToman END) FROM transactions WHERE accountId=accounts.id),0) FROM accounts WHERE id=:accountId")
    suspend fun availableInAccount(accountId: Long): Long?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: SavingsGoal): Long
    @Update suspend fun updateGoal(goal: SavingsGoal)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoalMovement(movement: GoalMovement): Long
    @Query("SELECT COUNT(*) FROM goal_movements WHERE goalId=:id")
    suspend fun goalMovementCount(id: Long): Int
    @Query("DELETE FROM savings_goals WHERE id=:id AND NOT EXISTS(SELECT 1 FROM goal_movements WHERE goalId=:id)")
    suspend fun deleteUnusedGoal(id: Long): Int
    @Query("UPDATE savings_goals SET archived=:archived WHERE id=:id")
    suspend fun setGoalArchived(id: Long, archived: Boolean): Int

    /** Each deposit/withdrawal is one atomic transfer between bank money and goal pot. */
    @androidx.room.Transaction
    suspend fun moveGoalMoney(
        goalId: Long, accountId: Long, amountToman: Long, operation: String
    ): Boolean {
        require(amountToman in 1L..100_000_000_000L)
        require(operation == "DEPOSIT" || operation == "WITHDRAW")
        val goal = findGoal(goalId) ?: return false
        if (goal.archived) return false
        if (operation == "DEPOSIT") {
            val available = availableInAccount(accountId) ?: return false
            if (available < amountToman) return false
        } else {
            if (goalBalance(goalId) < amountToman) return false
            if (availableInAccount(accountId) == null) return false
        }
        val now = System.currentTimeMillis()
        val bankKind = if (operation == "DEPOSIT") "GOAL_OUT" else "GOAL_IN"
        val label = if (operation == "DEPOSIT") "پس‌انداز برای «${goal.title}»"
            else "برگشت پس‌انداز «${goal.title}»"
        val transactionId = insertTransaction(Transaction(
            accountId = accountId, amountToman = amountToman, kind = bankKind,
            category = if (operation == "DEPOSIT") "پس‌انداز هدف" else "برداشت از هدف",
            note = label, source = "GOAL", createdAt = now
        ))
        check(transactionId > 0L)
        insertGoalMovement(GoalMovement(
            goalId = goalId, accountId = accountId, transactionId = transactionId,
            amountToman = amountToman, operation = operation, createdAt = now
        ))
        return true
    }

    @androidx.room.Transaction
    suspend fun archiveGoalIfEmpty(id: Long): Boolean {
        if (findGoal(id) == null || goalBalance(id) != 0L) return false
        return setGoalArchived(id, true) > 0
    }

    // Restore only after a fully validated backup and explicit confirmation.
    // The caller encloses these in a single Room transaction for rollback on failure.
    @Query("DELETE FROM goal_movements") suspend fun clearAllGoalMovements()
    @Query("DELETE FROM savings_goals") suspend fun clearAllGoals()
    @Query("DELETE FROM transactions") suspend fun clearAllTransactions()
    @Query("DELETE FROM sms_reviews") suspend fun clearAllSmsReviews()
    @Query("DELETE FROM sms_ignored") suspend fun clearAllIgnoredSms()
    @Query("DELETE FROM budgets") suspend fun clearAllBudgets()
    @Query("DELETE FROM debts") suspend fun clearAllDebts()
    @Query("DELETE FROM accounts") suspend fun clearAllAccounts()
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreGoals(rows: List<SavingsGoal>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreGoalMovements(rows: List<GoalMovement>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreAccounts(rows: List<Account>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreTransactions(rows: List<Transaction>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreDebts(rows: List<Debt>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreBudgets(rows: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreSmsReviews(rows: List<SmsReview>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun restoreIgnoredSms(rows: List<IgnoredSms>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(row: Account): Long
    @Update suspend fun updateAccount(row: Account)
    @Query("DELETE FROM accounts WHERE id=:id AND NOT EXISTS (SELECT 1 FROM transactions WHERE accountId=:id)") suspend fun deleteUnusedAccount(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransaction(row: Transaction): Long
    @Update suspend fun updateTransaction(row: Transaction)
    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM transactions WHERE groupId=:groupId") suspend fun deleteTransactionGroup(groupId: String)
    @Insert suspend fun insertDebt(row: Debt): Long
    @Update suspend fun updateDebt(row: Debt)
    @Query("DELETE FROM debts WHERE id=:id") suspend fun deleteDebt(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudget(row: Budget): Long
    @Update suspend fun updateBudget(row: Budget)
    @Query("UPDATE transactions SET category=:to WHERE category=:from AND kind=:kind")
    suspend fun renameTransactionCategory(kind: String, from: String, to: String)
    @Query("UPDATE budgets SET category=:to WHERE category=:from")
    suspend fun renameBudgetCategory(from: String, to: String)
    @Query("DELETE FROM budgets WHERE id=:id") suspend fun deleteBudget(id: Long)
    /**
     * All receivable collections/repayments are linked by debt ID without
     * adding columns or changing existing user databases.
     * Uses the current Room row, not an old UI snapshot.
     */
    @androidx.room.Transaction suspend fun applyDebtPayment(row: Debt, amount: Long, accountId: Long) {
        val latest = findDebt(row.id) ?: error("این بدهی یا طلب پیدا نشد")
        require(amount > 0 && amount <= latest.totalToman - latest.paidToman)
        require(getAccounts().any { it.id == accountId })
        val txId = insertTransaction(Transaction(
            accountId = accountId, amountToman = amount,
            kind = if (latest.direction == "I_OWE") "DEBT_OUT" else "DEBT_IN",
            category = if (latest.direction == "I_OWE") "بازپرداخت بدهی" else "دریافت طلب",
            note = latest.person, groupId = "DEBT_REF:${latest.id}"
        ))
        check(txId > 0L)
        updateDebt(latest.copy(paidToman = latest.paidToman + amount))
    }

    /**
     * Convert a deposit into a receivable collection or record a new one,
     * atomically. Existing incoming bank SMS is reclassified, NOT duplicated
     * in the account balance or statistics.
     */
    @androidx.room.Transaction suspend fun collectReceivable(
        debtId: Long, accountId: Long, amount: Long, note: String,
        incomingTransactionId: Long = 0L
    ): Boolean {
        val debt = findDebt(debtId) ?: return false
        if (debt.direction != "OWED_TO_ME" ||
            amount <= 0L || amount > debt.totalToman - debt.paidToman ||
            getAccounts().none { it.id == accountId }) return false
        if (incomingTransactionId > 0L) {
            val tx = findTransaction(incomingTransactionId) ?: return false
            if (tx.kind != "IN" || tx.groupId != null || tx.amountToman != amount ||
                tx.accountId != accountId) return false
            updateTransaction(tx.copy(kind = "DEBT_IN",
                category = "دریافت طلب", groupId = "DEBT_REF:$debtId",
                note = note.ifBlank { debt.person }))
        } else {
            val saved = insertTransaction(Transaction(
                accountId = accountId, amountToman = amount,
                kind = "DEBT_IN", category = "دریافت طلب",
                note = note.ifBlank { debt.person },
                groupId = "DEBT_REF:$debtId"
            ))
            if (saved <= 0L) return false
        }
        updateDebt(debt.copy(paidToman = debt.paidToman + amount))
        return true
    }

    /** Save a new payment or reclassify an existing ordinary transaction,
     * including edits to its amount/account. The ledger row and debt progress
     * are committed together; an existing withdrawal is never inserted twice. */
    @androidx.room.Transaction suspend fun saveLinkedDebtTransaction(
        debtId: Long, accountId: Long, amount: Long, note: String,
        existingId: Long, kind: String
    ): Boolean {
        if (kind !in listOf("IN", "OUT") || amount !in 1L..100_000_000_000L ||
            getAccounts().none { it.id == accountId }) return false
        val debt = findDebt(debtId) ?: return false
        val outgoing = kind == "OUT"
        if (debt.direction != (if (outgoing) "I_OWE" else "OWED_TO_ME")) return false
        val limit = if (outgoing) debt.paymentPeriodRemaining()
            else (debt.totalToman - debt.paidToman).coerceAtLeast(0L)
        if (amount > limit) return false
        val linkedKind = if (outgoing) "DEBT_OUT" else "DEBT_IN"
        val linkedCategory = if (outgoing) "بازپرداخت بدهی" else "دریافت طلب"
        val group = "DEBT_REF:$debtId"
        val details = note.ifBlank { debt.person }
        if (existingId > 0L) {
            val existing = findTransaction(existingId) ?: return false
            if (existing.kind != kind || existing.groupId != null ||
                countGoalMovementsForTransaction(existingId) > 0) return false
            updateTransaction(existing.copy(accountId = accountId, amountToman = amount,
                kind = linkedKind, category = linkedCategory, note = details, groupId = group))
        } else {
            if (insertTransaction(Transaction(accountId = accountId, amountToman = amount,
                    kind = linkedKind, category = linkedCategory, note = details, groupId = group)) <= 0L)
                return false
        }
        updateDebt(debt.copy(paidToman = debt.paidToman + amount))
        return true
    }

    /** Reclassify existing withdrawals as one payment batch. No new ledger rows,
     * no duplicate account deduction, and all changes commit together. */
    @androidx.room.Transaction suspend fun assignWithdrawalsToDebt(
        transactionIds: List<Long>, debtId: Long
    ): Boolean {
        if (transactionIds.isEmpty() || transactionIds.distinct().size != transactionIds.size) return false
        val debt = findDebt(debtId) ?: return false
        if (debt.direction != "I_OWE") return false
        val limit = debt.paymentPeriodRemaining()
        val rows = transactionIds.map { findTransaction(it) ?: return false }
        var total = 0L
        for (tx in rows) {
            if (tx.kind != "OUT" || tx.groupId != null || tx.amountToman <= 0 ||
                tx.amountToman > limit - total || countGoalMovementsForTransaction(tx.id) > 0) return false
            total += tx.amountToman
        }
        val batch = "DEBT_REF:$debtId:" + java.util.UUID.randomUUID().toString()
        for (tx in rows) updateTransaction(tx.copy(kind = "DEBT_OUT",
            category = "بازپرداخت بدهی", groupId = batch))
        updateDebt(debt.copy(paidToman = debt.paidToman + total))
        return true
    }

    /** Edit one payment in place, keeping its identity, source, and date.
     * Account balances are derived from the ledger, so moving a payment to
     * another account automatically reverses its effect on the old account.
     */
    @androidx.room.Transaction suspend fun editDebtPayment(
        id: Long, accountId: Long, amount: Long, note: String,
        allowUnresolved: Boolean = false
    ): Boolean {
        val tx = findTransaction(id) ?: return false
        if (tx.kind !in listOf("DEBT_IN", "DEBT_OUT") ||
            countGoalMovementsForTransaction(id) > 0 ||
            amount !in 1L..100_000_000_000L ||
            getAccounts().none { it.id == accountId }) return false
        val direction = if (tx.kind == "DEBT_IN") "OWED_TO_ME" else "I_OWE"
        val link = tx.debtReferenceId()
        val parent = link?.let { findDebt(it) }
        if (parent != null) {
            if (parent.direction != direction || parent.paidToman < tx.amountToman) return false
            val paidWithoutThis = parent.paidToman - tx.amountToman
            if (amount > parent.totalToman - paidWithoutThis) return false
            updateDebt(parent.copy(paidToman = paidWithoutThis + amount))
        } else if (amount != tx.amountToman && !allowUnresolved) {
            // Do not guess which other debt a legacy entry belonged to.
            return false
        }
        updateTransaction(tx.copy(accountId = accountId, amountToman = amount, note = note))
        return true
    }

    /**
     * Reverse a collection/repayment in the same transaction as its deletion
     * so debt progress and account balance cannot silently diverge.
     * Historical unlinked DEBT transactions can be deleted when their parent
     * no longer exists. Ambiguous or inconsistent debt links require explicit
     * confirmation to delete just the ledger entry, without altering any debt.
     * Savings-goal ledger entries remain immutable here.
     */
    @androidx.room.Transaction suspend fun deleteFinancialTransactionSafely(
        id: Long, allowUnresolvedDebtDeletion: Boolean = false
    ): Boolean {
        val tx = findTransaction(id) ?: return false
        if (countGoalMovementsForTransaction(id) > 0) return false
        if (tx.kind in listOf("GOAL_IN", "GOAL_OUT")) return false
        if (tx.kind == "DEBT_IN" || tx.kind == "DEBT_OUT") {
            val expectedDirection = if (tx.kind == "DEBT_IN") "OWED_TO_ME" else "I_OWE"
            val link = tx.debtReferenceId()
            val candidates = if (link != null) {
                listOfNotNull(findDebt(link))
            } else {
                getDebts().filter {
                    it.direction == expectedDirection &&
                    it.person.trim() == tx.note.trim()
                }
            }
            if (candidates.isEmpty()) {
                // Includes legacy collections created before DEBT_REF existed.
                deleteTransaction(id)
                return true
            }
            val debt = candidates.singleOrNull()?.takeIf {
                it.direction == expectedDirection &&
                    tx.amountToman > 0L && it.paidToman >= tx.amountToman
            }
            if (debt == null) {
                // Never guess which other debt should change. The user can
                // explicitly remove this ledger entry after a second warning.
                if (!allowUnresolvedDebtDeletion) return false
            } else {
                updateDebt(debt.copy(paidToman = debt.paidToman - tx.amountToman))
            }
            deleteTransaction(id)
            return true
        }
        // Transfers must be removed as one pair, not as a single bank leg.
        val group = tx.groupId
        if (group != null) {
            if (group.startsWith("DEBT_REF:")) return false
            deleteTransactionGroup(group)
        } else deleteTransaction(id)
        return true
    }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReview(row: SmsReview): Long
    @Query("SELECT * FROM sms_reviews WHERE id=:id LIMIT 1") suspend fun findReview(id: Long): SmsReview?
    @Query("SELECT EXISTS(SELECT 1 FROM sms_ignored WHERE fingerprint=:fingerprint)") suspend fun isIgnored(fingerprint: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedupKey=:fingerprint)") suspend fun isRecorded(fingerprint: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnored(row: IgnoredSms): Long
    @Query("DELETE FROM sms_reviews WHERE id=:id") suspend fun deleteReview(id: Long)

    // The review stays pending until the user chooses a category and approves it.
    @androidx.room.Transaction suspend fun enqueueReview(row: SmsReview): Long {
        if (isIgnored(row.fingerprint) || isRecorded(row.fingerprint)) return -1L
        return insertReview(row)
    }

    @androidx.room.Transaction suspend fun approveReview(
        id: Long, accountId: Long, category: String, note: String,
        unitChoice: String, receivableDebtId: Long? = null
    ): Boolean {
        val row = findReview(id) ?: return false
        // Simulations may affect balances/stats ONLY through explicit review approval.
        val unit = if (row.amountUnit == "UNKNOWN") unitChoice else row.amountUnit
        val raw = row.amountRaw.takeIf { it > 0L } ?: row.amountToman
        val amount = when (unit) {
            "TOMAN" -> raw
            "RIAL" -> ir.jessi.finance.sms.BankSmsParser.rialToToman(raw)
            else -> return false
        }
        if (amount <= 0L || amount > 100_000_000_000L || getAccounts().none { it.id == accountId }) return false
        val debt = if (receivableDebtId != null) {
            if (row.kind !in listOf("IN", "OUT")) return false
            val active = findDebt(receivableDebtId) ?: return false
            val direction = if (row.kind == "IN") "OWED_TO_ME" else "I_OWE"
            if (active.direction != direction ||
                amount > (if (row.kind == "OUT") active.paymentPeriodRemaining()
                    else active.totalToman - active.paidToman)) return false
            active
        } else null
        val saved = insertTransaction(Transaction(
            accountId = accountId, amountToman = amount,
            kind = if (debt != null) (if (row.kind == "IN") "DEBT_IN" else "DEBT_OUT") else row.kind,
            category = if (debt != null) (if (row.kind == "IN") "دریافت طلب" else "بازپرداخت بدهی") else category,
            note = if (debt != null) note.ifBlank { debt.person }
                else SmsReviewPolicy.noteFor(row.fingerprint, note, row.sender),
            createdAt = row.receivedAt,
            source = SmsReviewPolicy.sourceFor(row.fingerprint),
            dedupKey = row.fingerprint,
            groupId = debt?.let { "DEBT_REF:${it.id}" }
        ))
        if (saved > 0L) {
            if (debt != null) updateDebt(debt.copy(paidToman = debt.paidToman + amount))
            deleteReview(id)
        }
        return saved > 0L
    }

    // Delete extracted SMS details, retaining only a one-way hash to prevent re-import.
    @androidx.room.Transaction suspend fun ignoreReview(id: Long): Boolean {
        val row = findReview(id) ?: return false
        insertIgnored(IgnoredSms(row.fingerprint))
        deleteReview(id)
        return true
    }
    @androidx.room.Transaction suspend fun transfer(fromId: Long, toId: Long, amount: Long, note: String) {
        require(fromId != toId && amount > 0)
        val group = java.util.UUID.randomUUID().toString()
        val time = System.currentTimeMillis()
        insertTransaction(Transaction(accountId = fromId, amountToman = amount, kind = "TRANSFER_OUT", category = "انتقال", note = note, createdAt = time, groupId = group))
        insertTransaction(Transaction(accountId = toId, amountToman = amount, kind = "TRANSFER_IN", category = "انتقال", note = note, createdAt = time, groupId = group))
    }
}

@Database(
    entities = [Account::class, Transaction::class, Debt::class, Budget::class,
        SmsReview::class, IgnoredSms::class, SavingsGoal::class, GoalMovement::class],
    version = 5, exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sms_ignored` (`fingerprint` TEXT NOT NULL, PRIMARY KEY(`fingerprint`))")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `installmentMonths` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `firstInstallmentJalali` TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sms_reviews` ADD COLUMN `amountRaw` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sms_reviews` ADD COLUMN `amountUnit` TEXT NOT NULL DEFAULT 'TOMAN'")
                db.execSQL("ALTER TABLE `sms_reviews` ADD COLUMN `bankName` TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `savings_goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL, `category` TEXT NOT NULL,
                        `targetToman` INTEGER NOT NULL,
                        `monthlyToman` INTEGER NOT NULL,
                        `deadlineJalali` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `goal_movements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `goalId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `amountToman` INTEGER NOT NULL,
                        `operation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_movements_goalId` ON `goal_movements`(`goalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_movements_accountId` ON `goal_movements`(`accountId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_movements_transactionId` ON `goal_movements`(`transactionId`)")
            }
        }
        @Volatile private var instance: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "jessi_finance.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
