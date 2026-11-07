package ir.jessi.finance.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ir.jessi.finance.sms.SmsReviewNotifications

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FinanceDatabase.get(application).dao()
    val accounts = dao.watchAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.watchTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val debts = dao.watchDebts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.watchBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = dao.watchReviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveAccount(row: Account) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertAccount(row) else dao.updateAccount(row)
    }
    fun deleteAccount(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteUnusedAccount(id) }
    fun saveTransaction(row: Transaction) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertTransaction(row) else dao.updateTransaction(row)
    }
    fun transfer(from: Long, to: Long, amount: Long, note: String) = viewModelScope.launch(Dispatchers.IO) {
        if (from != to && amount > 0L) dao.transfer(from, to, amount, note)
    }
    fun deleteTransaction(row: Transaction) = viewModelScope.launch(Dispatchers.IO) {
        if (row.groupId != null) dao.deleteTransactionGroup(row.groupId)
        else dao.deleteTransaction(row.id)
    }
    fun saveDebt(row: Debt) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertDebt(row) else dao.updateDebt(row)
    }
    fun deleteDebt(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteDebt(id) }
    fun settleDebt(row: Debt, amount: Long, accountId: Long) = viewModelScope.launch(Dispatchers.IO) {
        if (amount <= 0 || amount > row.totalToman - row.paidToman) return@launch
        dao.applyDebtPayment(row, amount, accountId)
    }
    fun saveBudget(row: Budget) = viewModelScope.launch(Dispatchers.IO) {
        if (row.id == 0L) dao.insertBudget(row) else dao.updateBudget(row)
    }
    fun deleteBudget(id: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deleteBudget(id) }
    fun acceptReview(row: SmsReview, accountId: Long, category: String, note: String) = viewModelScope.launch(Dispatchers.IO) {
        dao.approveReview(row.id, accountId, category, note)
        SmsReviewNotifications.cancel(getApplication(), row.id)
    }
    fun dismissReview(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.ignoreReview(id)
        SmsReviewNotifications.cancel(getApplication(), id)
    }
}
