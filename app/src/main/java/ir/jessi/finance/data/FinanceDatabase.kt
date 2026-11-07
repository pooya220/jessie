package ir.jessi.finance.data

import android.content.Context
import androidx.room.*
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
    val note: String = ""
)

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
    val accountGuess: Long? = null
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id DESC") fun watchAccounts(): Flow<List<Account>>
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, id DESC") fun watchTransactions(): Flow<List<Transaction>>
    @Query("SELECT * FROM debts ORDER BY id DESC") fun watchDebts(): Flow<List<Debt>>
    @Query("SELECT * FROM budgets ORDER BY category") fun watchBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM sms_reviews ORDER BY receivedAt DESC") fun watchReviews(): Flow<List<SmsReview>>
    @Query("SELECT * FROM accounts") suspend fun getAccounts(): List<Account>
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
    @Query("DELETE FROM budgets WHERE id=:id") suspend fun deleteBudget(id: Long)
    @androidx.room.Transaction suspend fun applyDebtPayment(row: Debt, amount: Long, accountId: Long) {
        require(amount > 0 && amount <= row.totalToman - row.paidToman)
        updateDebt(row.copy(paidToman = row.paidToman + amount))
        insertTransaction(Transaction(
            accountId = accountId, amountToman = amount,
            kind = if (row.direction == "I_OWE") "DEBT_OUT" else "DEBT_IN",
            category = if (row.direction == "I_OWE") "بازپرداخت بدهی" else "دریافت طلب",
            note = row.person
        ))
    }
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReview(row: SmsReview): Long
    @Query("DELETE FROM sms_reviews WHERE id=:id") suspend fun deleteReview(id: Long)
    @androidx.room.Transaction suspend fun transfer(fromId: Long, toId: Long, amount: Long, note: String) {
        require(fromId != toId && amount > 0)
        val group = java.util.UUID.randomUUID().toString()
        val time = System.currentTimeMillis()
        insertTransaction(Transaction(accountId = fromId, amountToman = amount, kind = "TRANSFER_OUT", category = "انتقال", note = note, createdAt = time, groupId = group))
        insertTransaction(Transaction(accountId = toId, amountToman = amount, kind = "TRANSFER_IN", category = "انتقال", note = note, createdAt = time, groupId = group))
    }
}

@Database(entities = [Account::class, Transaction::class, Debt::class, Budget::class, SmsReview::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        @Volatile private var instance: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "jessi_finance.db")
                .build().also { instance = it }
        }
    }
}
