package ir.jessi.finance.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ir.jessi.finance.R
import ir.jessi.finance.data.Debt
import ir.jessi.finance.data.FinanceDatabase
import ir.jessi.finance.data.GoalMovement
import ir.jessi.finance.data.SavingsGoal
import ir.jessi.finance.data.SavingsGoalMath
import ir.jessi.finance.notifications.FinanceReminderScheduler
import ir.jessi.finance.ui.Installments
import ir.jessi.finance.ui.JalaliDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.util.Locale

/** A genuine launcher ListView adapter with scrollable, real Room data. */
class JessiWidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        JessiWidgetListFactory(applicationContext,
            intent.getStringExtra(EXTRA_TYPE) ?: "PAYMENTS")

    companion object {
        const val EXTRA_TYPE = "jessi_widget_collection_type"
    }
}

private data class WidgetListRow(
    val id: Long,
    val title: String,
    val amount: String,
    val note: String,
    val progress: Int,
    val tab: String
)

private class JessiWidgetListFactory(
    private val context: Context,
    private val type: String
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<WidgetListRow> = emptyList()
    private val number = NumberFormat.getNumberInstance(Locale("fa", "IR"))

    override fun onCreate() { onDataSetChanged() }
    override fun onDestroy() { rows = emptyList() }

    /** Android invokes this on a Binder thread; database work stays off UI. */
    override fun onDataSetChanged() {
        rows = runCatching {
            runBlocking(Dispatchers.IO) {
                val dao = FinanceDatabase.get(context).dao()
                when (type) {
                    "PAYMENTS" -> unpaidInstallments(dao.getDebts())
                    "GOALS" -> goalRows(dao.getGoals(), dao.getGoalMovements())
                    else -> emptyList()
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun pretty(value: Long): String = number.format(value) + " ت"

    /**
     * Next *calendar Jalali month*, not all future installments.
     * Paid installments and earlier overdue items are excluded from
     * this widget (overdue notices remain in the notification worker).
     */
    private fun unpaidInstallments(debts: List<Debt>): List<WidgetListRow> =
        NextMonthInstallmentSelector.select(
            debts, ir.jessi.finance.ui.Jalali.of(System.currentTimeMillis())
        ).take(60).map { item ->
            WidgetListRow(
                id = (item.debtId shl 8) + item.installmentIndex,
                title = "☐ " + item.person,
                amount = pretty(item.amountToman),
                note = "قسط " + (item.installmentIndex + 1) + "/" + item.totalCount +
                    " • " + item.due.year + "/" + item.due.month + "/" + item.due.day,
                progress = -1, tab = "DEBTS"
            )
        }

    private fun goalRows(goals: List<SavingsGoal>, movements: List<GoalMovement>) =
        goals.asSequence().filterNot { it.archived }
            .sortedByDescending { it.createdAt }.take(60).map { goal ->
                val saved = SavingsGoalMath.saved(movements, goal.id).coerceAtLeast(0L)
                val pct = if (goal.targetToman > 0L)
                    (saved.toDouble() / goal.targetToman * 100).toInt().coerceIn(0, 100)
                else -1
                WidgetListRow(
                    id = goal.id,
                    title = goal.title,
                    amount = if (goal.targetToman > 0L)
                        pretty(saved) + " / " + pretty(goal.targetToman)
                    else pretty(saved),
                    note = if (pct >= 0) "$pct٪ تکمیل" else "پس‌انداز ماهانه",
                    progress = pct, tab = "GOALS"
                )
            }.toList()

    override fun getCount(): Int = rows.size
    override fun getViewTypeCount(): Int = 1
    override fun hasStableIds(): Boolean = true
    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.id ?: position.toLong()
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews? {
        val item = rows.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.jessi_scroll_item).apply {
            setTextViewText(R.id.jessi_scroll_item_title, item.title)
            setTextViewText(R.id.jessi_scroll_amount, item.amount)
            setTextViewText(R.id.jessi_scroll_item_note, item.note)
            if (item.progress >= 0) {
                setViewVisibility(R.id.jessi_scroll_progress, View.VISIBLE)
                setProgressBar(R.id.jessi_scroll_progress, 100, item.progress, false)
            } else setViewVisibility(R.id.jessi_scroll_progress, View.GONE)
            // A tap only opens the protected app; never silently pays a debt.
            setOnClickFillInIntent(R.id.jessi_scroll_row,
                Intent().putExtra(FinanceReminderScheduler.EXTRA_OPEN_TAB, item.tab))
        }
    }
}
