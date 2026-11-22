package ir.jessi.finance.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import ir.jessi.finance.ui.FinanceCategories
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ir.jessi.finance.sms.SmsReviewNotifications
import ir.jessi.finance.notifications.FinanceReminderScheduler
import ir.jessi.finance.widgets.JessiWidgetUpdater
import ir.jessi.finance.sms.BankSmsParser
import java.util.UUID
import kotlinx.coroutines.withContext

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FinanceDatabase.get(application)
    private val dao = database.dao()
    val accounts = dao.watchAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.watchTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val debts = dao.watchDebts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.watchBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = dao.watchReviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = dao.watchGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goalMovements = dao.watchGoalMovements().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun saveAccount(row: Account) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertAccount(row) else dao.updateAccount(row)
        JessiWidgetUpdater.request(getApplication())
    }
    fun deleteAccount(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteUnusedAccount(id)
        JessiWidgetUpdater.request(getApplication())
    }
    fun saveTransaction(row: Transaction) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertTransaction(row) else dao.updateTransaction(row)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun transfer(from: Long, to: Long, amount: Long, note: String) = viewModelScope.launch(Dispatchers.IO) {
        if (from != to && amount > 0L) {
            dao.transfer(from, to, amount, note)
            JessiWidgetUpdater.request(getApplication())
        }
    }
    fun deleteTransaction(row: Transaction) = viewModelScope.launch(Dispatchers.IO) {
        if (row.groupId != null) dao.deleteTransactionGroup(row.groupId)
        else dao.deleteTransaction(row.id)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun saveDebt(row: Debt) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertDebt(row) else dao.updateDebt(row)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun deleteDebt(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteDebt(id)
        FinanceReminderScheduler.cancelDebt(getApplication(), id)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun settleDebt(row: Debt, amount: Long, accountId: Long) = viewModelScope.launch(Dispatchers.IO) {
        if (amount <= 0 || amount > row.totalToman - row.paidToman) return@launch
        dao.applyDebtPayment(row, amount, accountId)
        FinanceReminderScheduler.cancelDebt(getApplication(), row.id)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun saveBudget(row: Budget) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertBudget(row) else dao.updateBudget(row)
        FinanceReminderScheduler.checkSoon(getApplication())
        JessiWidgetUpdater.request(getApplication())
    }
    fun deleteBudget(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteBudget(id)
        JessiWidgetUpdater.request(getApplication())
    }

    /**
     * Rename historical transactions and related budget labels in one
     * transaction so charts, budget alerts and existing totals stay connected.
     * No amounts or transaction dates are modified.
     */
    fun renameCategory(
        kind: String, old: String, new: String,
        onComplete: (Boolean, String?) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val outcome = runCatching {
            require(kind == FinanceCategories.INCOME || kind == FinanceCategories.EXPENSE)
            require(old.isNotBlank() && new.isNotBlank() && old != new)
            database.withTransaction {
                if (kind == FinanceCategories.EXPENSE) {
                    val budgets = dao.getBudgets()
                    if (budgets.any { it.category == old } &&
                        budgets.any { it.category == new }) {
                        error("برای نام جدید از قبل سقف بودجه ثبت شده است؛ اول آن را ویرایش کن")
                    }
                }
                dao.renameTransactionCategory(kind, old, new)
                if (kind == FinanceCategories.EXPENSE)
                    dao.renameBudgetCategory(old, new)
            }
        }
        // A background scheduler issue must never be mistaken for failure of
        // the already-committed financial record rename.
        if (outcome.isSuccess) runCatching {
            FinanceReminderScheduler.checkSoon(getApplication())
            JessiWidgetUpdater.request(getApplication())
        }
        withContext(Dispatchers.Main) {
            onComplete(outcome.isSuccess, outcome.exceptionOrNull()?.message)
        }
    }
    fun saveGoal(row: SavingsGoal, onComplete: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                require(row.title.isNotBlank() && row.title.length <= 120)
                require(row.targetToman in 0L..100_000_000_000L)
                require(row.monthlyToman in 0L..100_000_000_000L)
                require(row.targetToman > 0L || row.monthlyToman > 0L)
                if (row.id == 0L) dao.insertGoal(row) > 0L
                else { dao.updateGoal(row); true }
            } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { onComplete(ok) }
        }

    fun moveGoalMoney(
        goalId: Long, accountId: Long, amountToman: Long, operation: String,
        onComplete: (Boolean) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val ok = try { dao.moveGoalMoney(goalId, accountId, amountToman, operation) }
            catch (_: Exception) { false }
        if (ok) JessiWidgetUpdater.request(getApplication())
        withContext(Dispatchers.Main) { onComplete(ok) }
    }

    fun setGoalArchive(id: Long, archived: Boolean, onComplete: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                if (archived) dao.archiveGoalIfEmpty(id)
                else dao.setGoalArchived(id, false) > 0
            } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { onComplete(ok) }
        }

    fun deleteEmptyGoal(id: Long, onComplete: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { dao.deleteUnusedGoal(id) > 0 } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { onComplete(ok) }
        }

    fun acceptReview(
        row: SmsReview, accountId: Long, category: String, note: String,
        unitChoice: String, onResult: (Boolean) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val saved = try { dao.approveReview(row.id, accountId, category, note, unitChoice) }
        catch (_: Exception) { false }
        if (saved) {
            SmsReviewNotifications.cancel(getApplication(), row.id)
            FinanceReminderScheduler.checkSoon(getApplication())
            JessiWidgetUpdater.request(getApplication())
        }
        withContext(Dispatchers.Main) { onResult(saved) }
    }
    fun simulateSms(raw: String, sender: String, onComplete: (String, SmsReview?) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val result = BankSmsParser.parse(raw, sender)
            var newReview: SmsReview? = null
            val report = if (result == null) {
                "این متن تراکنش بانکی تشخیص داده نشد"
            } else {
                try {
                    // The sample body and actual account number are NEVER written to the database.
                    val row = SmsReview(
                        sender = "آزمایشی • بدون ثبت مالی",
                        amountToman = result.amountToman,
                        kind = result.kind,
                        receivedAt = System.currentTimeMillis(),
                        fingerprint = "JESSI_TEST_" + UUID.randomUUID().toString(),
                        accountGuess = null,
                        amountRaw = result.amountRaw,
                        amountUnit = result.amountUnit,
                        bankName = result.bankName
                    )
                    val id = dao.enqueueReview(row)
                    if (id > 0L) {
                        newReview = dao.findReview(id)
                        val notified = SmsReviewNotifications.show(
                            getApplication(), id, result.kind, isTest = true
                        )
                        if (notified) "نمونه آزمایشی به صف رفت و اعلان ارسال شد"
                        else "نمونه آزمایشی به صف رفت، اما نمایش اعلان مسدود است"
                    } else "این نمونه قبلاً در صف ثبت شده"
                } catch (_: Exception) {
                    "خطا هنگام ثبت نمونه آزمایشی در صف"
                }
            }
            withContext(Dispatchers.Main) { onComplete(report, newReview) }
        }

    fun dismissReview(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.ignoreReview(id)
        SmsReviewNotifications.cancel(getApplication(), id)
    }
}
