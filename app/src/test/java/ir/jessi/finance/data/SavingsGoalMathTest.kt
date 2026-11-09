package ir.jessi.finance.data

import org.junit.Assert.*
import org.junit.Test

class SavingsGoalMathTest {
    private val moves = listOf(
        GoalMovement(1, 7, 2, 101, 3_000_000, "DEPOSIT", 100),
        GoalMovement(2, 7, 2, 102, 2_000_000, "DEPOSIT", 200),
        GoalMovement(3, 7, 2, 103, 1_000_000, "WITHDRAW", 300),
        GoalMovement(4, 8, 2, 104, 8_000_000, "DEPOSIT", 100)
    )

    @Test fun balancesIncludeOnlyMatchingGoal() {
        assertEquals(4_000_000L, SavingsGoalMath.saved(moves, 7))
        assertEquals(8_000_000L, SavingsGoalMath.saved(moves, 8))
        assertEquals(0L, SavingsGoalMath.saved(moves, 9))
    }

    @Test fun monthlySavingsAreNetMovementWithinPeriod() {
        assertEquals(5_000_000L, SavingsGoalMath.monthlyProgress(moves, 7, 0, 250))
        assertEquals(1_000_000L, SavingsGoalMath.monthlyProgress(moves, 7, 150, 350))
        assertEquals(0L, SavingsGoalMath.monthlyProgress(moves, 7, 500, 900))
    }

    @Test fun projectPurchaseCompletionWithoutOverflow() {
        assertEquals(3L, SavingsGoalMath.monthsToGoal(2_000_000, 10_000_000, 3_000_000))
        assertEquals(0L, SavingsGoalMath.monthsToGoal(10_000_000, 10_000_000, 3_000_000))
        assertNull(SavingsGoalMath.monthsToGoal(0, 0, 3_000_000))
        assertNull(SavingsGoalMath.monthsToGoal(0, 10_000_000, 0))
        assertEquals(1f, SavingsGoalMath.fraction(14_000_000, 10_000_000))
        assertEquals(0f, SavingsGoalMath.fraction(0, 0))
    }
}
