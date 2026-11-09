package ir.jessi.finance.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A goal is a local virtual savings pot. Money is moved to it from an
 * existing account; a matching GOAL_OUT/GOAL_IN transaction preserves the
 * real accounting balance without treating savings as income or expense.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "OTHER",
    val targetToman: Long = 0L,
    val monthlyToman: Long = 0L,
    val deadlineJalali: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false
)

@Entity(
    tableName = "goal_movements",
    indices = [
        Index("goalId"), Index("accountId"),
        Index(value = ["transactionId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoal::class,
            parentColumns = ["id"], childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"], childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"], childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class GoalMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val accountId: Long,
    val transactionId: Long,
    val amountToman: Long,
    val operation: String, // DEPOSIT, WITHDRAW
    val createdAt: Long = System.currentTimeMillis()
)

/** Pure arithmetic shared by UI, backup checks and tests. No auto-debits. */
object SavingsGoalMath {
    fun saved(movements: List<GoalMovement>, goalId: Long): Long =
        movements.asSequence().filter { it.goalId == goalId }
            .sumOf { if (it.operation == "DEPOSIT") it.amountToman else -it.amountToman }

    fun monthlyProgress(
        movements: List<GoalMovement>, goalId: Long,
        startMillis: Long, endExclusiveMillis: Long
    ): Long = movements.asSequence()
        .filter { it.goalId == goalId &&
            it.createdAt >= startMillis && it.createdAt < endExclusiveMillis }
        .sumOf { if (it.operation == "DEPOSIT") it.amountToman else -it.amountToman }
        .coerceAtLeast(0L)

    fun fraction(savedToman: Long, targetToman: Long): Float =
        if (targetToman <= 0L) 0f else
            (savedToman.toDouble() / targetToman.toDouble()).toFloat().coerceIn(0f, 1f)

    fun monthsToGoal(savedToman: Long, targetToman: Long, monthlyToman: Long): Long? {
        if (targetToman <= 0 || monthlyToman <= 0) return null
        val remaining = (targetToman - savedToman).coerceAtLeast(0L)
        return remaining / monthlyToman + if (remaining % monthlyToman != 0L) 1L else 0L
    }
}
