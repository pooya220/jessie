package ir.jessi.finance.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.jessi.finance.MainActivity
import ir.jessi.finance.R
import ir.jessi.finance.data.Account
import ir.jessi.finance.data.Budget
import ir.jessi.finance.data.FinanceDatabase
import ir.jessi.finance.data.FinancePeriodEngine
import ir.jessi.finance.data.Transaction
import ir.jessi.finance.notifications.FinanceReminderScheduler

/** Three independent widget choices; each is fully resizable by the launcher. */
enum class HomeWidgetKind { BALANCE, BUDGETS, EXPENSES }

abstract class JessiHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isNotEmpty()) JessiWidgetUpdater.request(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        JessiWidgetUpdater.request(context)
    }
}

class JessiBalanceWidget : JessiHomeWidgetProvider()
class JessiBudgetsWidget : JessiHomeWidgetProvider()
class JessiExpensesWidget : JessiHomeWidgetProvider()

data class HomeWidgetSnapshot(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val periodStart: Long,
    val periodEnd: Long,
    val periodLabel: String,
    val showBalance: Boolean
) {
    val balance: Long get() {
        val opening = accounts.sumOf { it.openingToman }
        val change = transactions.asSequence()
            .filter { tx -> accounts.any { it.id == tx.accountId } }
            .sumOf { tx ->
                when (tx.kind) {
                    "IN", "TRANSFER_IN", "DEBT_IN", "GOAL_IN" -> tx.amountToman
                    else -> -tx.amountToman
                }
            }
        return opening + change
    }

    val periodTransactions: List<Transaction> get() =
        transactions.filter { it.createdAt >= periodStart && it.createdAt < periodEnd }

    val income: Long get() = periodTransactions.asSequence()
        .filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expenses: Long get() = periodTransactions.asSequence()
        .filter { it.kind == "OUT" }.sumOf { it.amountToman }

    val expensesByCategory: List<Pair<String, Long>> get() =
        periodTransactions.asSequence().filter { it.kind == "OUT" }
            .groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.amountToman } }
            .toList().sortedByDescending { it.second }
}

object JessiWidgetUpdater {
    const val BALANCE_VISIBLE_KEY = "widget_balance_visible"
    private const val WORK_NAME = "jessi_widgets_update_v1"

    /** Safe to call after every financial write; only updates locally installed widgets. */
    fun request(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        if (!hasAny(manager, app)) return
        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<JessiWidgetWorker>().build()
        )
    }

    private fun hasAny(manager: AppWidgetManager, app: Context): Boolean =
        targets(manager, app).any { it.second.isNotEmpty() }

    private fun targets(
        manager: AppWidgetManager, context: Context
    ): List<Pair<HomeWidgetKind, IntArray>> = listOf(
        HomeWidgetKind.BALANCE to manager.getAppWidgetIds(
            ComponentName(context, JessiBalanceWidget::class.java)),
        HomeWidgetKind.BUDGETS to manager.getAppWidgetIds(
            ComponentName(context, JessiBudgetsWidget::class.java)),
        HomeWidgetKind.EXPENSES to manager.getAppWidgetIds(
            ComponentName(context, JessiExpensesWidget::class.java))
    )

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val current = targets(manager, app).filter { it.second.isNotEmpty() }
        if (current.isEmpty()) return

        val prefs = app.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)
        val dao = FinanceDatabase.get(app).dao()
        val accounts = dao.getAccounts()
        val transactions = dao.getTransactions()
        val budgets = dao.getBudgets()
        val mode = prefs.getString("period_mode", FinancePeriodEngine.MONTHLY)
            ?: FinancePeriodEngine.MONTHLY
        val now = System.currentTimeMillis()
        val period = FinancePeriodEngine.periods(transactions, mode, now).first()
        val snapshot = HomeWidgetSnapshot(
            accounts, transactions, budgets, period.start, period.endExclusive,
            period.shortLabel, prefs.getBoolean(BALANCE_VISIBLE_KEY, false)
        )
        for ((type, ids) in current) for (id in ids) {
            val opts = manager.getAppWidgetOptions(id)
            val landscape = app.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            val defaultWidth = if (type == HomeWidgetKind.EXPENSES) 290 else 185
            val defaultHeight = if (type == HomeWidgetKind.BALANCE) 100 else 180
            val proposedWidth = opts.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultWidth)
            val proposedHeight = opts.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultHeight)
            val w = proposedWidth.takeIf { it > 0 } ?: defaultWidth
            val h = proposedHeight.takeIf { it > 0 } ?: defaultHeight
            val bitmap = JessiWidgetRenderer.render(
                app, type, snapshot, w.coerceIn(140, 490), h.coerceIn(72, 340)
            )
            val views = RemoteViews(app.packageName, R.layout.jessi_home_widget)
            views.setImageViewBitmap(R.id.jessi_widget_image, bitmap)
            views.setContentDescription(
                R.id.jessi_widget_image, when (type) {
                    HomeWidgetKind.BALANCE -> "جسی؛ موجودی حساب‌ها"
                    HomeWidgetKind.BUDGETS -> "جسی؛ نمودار پیشرفت بودجه‌ها"
                    HomeWidgetKind.EXPENSES -> "جسی؛ سهم دسته‌های هزینه"
                }
            )
            val tab = when (type) {
                HomeWidgetKind.BALANCE, HomeWidgetKind.BUDGETS -> "ACCOUNTS"
                HomeWidgetKind.EXPENSES -> "TXS"
            }
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(FinanceReminderScheduler.EXTRA_OPEN_TAB, tab)
                putExtra("jessi_open_budgets", type == HomeWidgetKind.BUDGETS)
            }
            val pending = PendingIntent.getActivity(
                app, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.jessi_widget_image, pending)
            manager.updateAppWidget(id, views)
        }
    }
}

class JessiWidgetWorker(
    appContext: Context, params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        JessiWidgetUpdater.updateAll(applicationContext)
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}
