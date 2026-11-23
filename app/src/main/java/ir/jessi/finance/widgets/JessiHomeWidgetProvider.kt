package ir.jessi.finance.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import java.util.UUID
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
enum class HomeWidgetKind { BALANCE, BUDGETS, EXPENSES, PAYMENTS, GOALS }

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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == JessiWidgetUpdater.ACTION_CONTROL) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val manager = AppWidgetManager.getInstance(context)
            val owner = ComponentName(context, JessiBalanceWidget::class.java)
            if (id !in manager.getAppWidgetIds(owner)) return
            val widgetPrefs = JessiWidgetUpdater.controlPrefs(context)
            val token = widgetPrefs.getString("token_$id", null)
            if (token.isNullOrBlank() || token != intent.getStringExtra("token")) return
            val op = intent.getStringExtra("operation")
            val editor = widgetPrefs.edit()
            when (op) {
                "eye" -> {
                    val defaultVisible = context.getSharedPreferences(
                        "jessi_finance_settings", Context.MODE_PRIVATE
                    ).getBoolean(JessiWidgetUpdater.BALANCE_VISIBLE_KEY, false)
                    val visible = widgetPrefs.getBoolean("visible_$id", defaultVisible)
                    editor.putBoolean("visible_$id", !visible)
                }
                "next" -> editor.putInt("page_$id",
                    widgetPrefs.getInt("page_$id", 0) + 1)
                "prev" -> editor.putInt("page_$id",
                    widgetPrefs.getInt("page_$id", 0) - 1)
                else -> return
            }
            editor.apply()
            JessiWidgetUpdater.request(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = JessiWidgetUpdater.controlPrefs(context).edit()
        appWidgetIds.forEach {
            editor.remove("visible_$it").remove("page_$it").remove("token_$it")
        }
        editor.apply()
    }
}

class JessiBalanceWidget : JessiHomeWidgetProvider()
class JessiBudgetsWidget : JessiHomeWidgetProvider()
class JessiExpensesWidget : JessiHomeWidgetProvider()
class JessiPaymentsWidget : JessiHomeWidgetProvider()
class JessiGoalsWidget : JessiHomeWidgetProvider()


data class HomeWidgetSnapshot(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val periodStart: Long,
    val periodEnd: Long,
    val periodLabel: String,
    val showBalance: Boolean,
    val selectedAccountIndex: Int = -1,
    val selectedBudgetIds: List<Long> = emptyList()
) {
    fun accountBalance(accountId: Long): Long {
        val account = accounts.firstOrNull { it.id == accountId } ?: return 0L
        val change = transactions.asSequence()
            .filter { it.accountId == accountId }
            .sumOf { tx -> when (tx.kind) {
                "IN", "TRANSFER_IN", "DEBT_IN", "GOAL_IN" -> tx.amountToman
                else -> -tx.amountToman
            } }
        return account.openingToman + change
    }

    val displayedAccount: Account?
        get() = accounts.getOrNull(selectedAccountIndex)
    val displayedBalance: Long get() =
        displayedAccount?.let { accountBalance(it.id) } ?: balance
    val displayedTitle: String get() = displayedAccount?.title ?: "موجودی کل"
    val widgetBudgets: List<Budget> get() {
        return selectedBudgetIds.mapNotNull { id ->
            budgets.firstOrNull { it.id == id }
        }.take(5)
    }

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
    const val ACTION_CONTROL = "ir.jessi.finance.WIDGET_BALANCE_CONTROL"
    private const val WORK_NAME = "jessi_widgets_update_v1"

    fun controlPrefs(context: Context) =
        context.getSharedPreferences("jessi_widget_controls_v1", Context.MODE_PRIVATE)

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
            ComponentName(context, JessiExpensesWidget::class.java)),
        HomeWidgetKind.PAYMENTS to manager.getAppWidgetIds(
            ComponentName(context, JessiPaymentsWidget::class.java)),
        HomeWidgetKind.GOALS to manager.getAppWidgetIds(
            ComponentName(context, JessiGoalsWidget::class.java))
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
        val selectedBudgets = JessiWidgetPrefs.selectedBudgets(app, budgets)
        val snapshot = HomeWidgetSnapshot(
            accounts, transactions, budgets, period.start, period.endExclusive,
            period.shortLabel, prefs.getBoolean(BALANCE_VISIBLE_KEY, false),
            selectedBudgetIds = selectedBudgets
        )
        val controls = controlPrefs(app)
        for ((type, ids) in current) for (id in ids) {
            if (type == HomeWidgetKind.PAYMENTS || type == HomeWidgetKind.GOALS) {
                updateScrollable(app, manager, type, id)
                continue
            }
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
            val page = Math.floorMod(controls.getInt("page_$id", 0),
                accounts.size + 1) - 1
            val visible = controls.getBoolean("visible_$id", snapshot.showBalance)
            val model = snapshot.copy(
                showBalance = visible, selectedAccountIndex = page
            )
            val bitmap = JessiWidgetRenderer.render(
                app, type, model, w.coerceIn(140, 400), h.coerceIn(72, 300)
            )
            val views = RemoteViews(app.packageName, R.layout.jessi_home_widget)
            views.setImageViewBitmap(R.id.jessi_widget_image, bitmap)
            views.setContentDescription(
                R.id.jessi_widget_image, when (type) {
                    HomeWidgetKind.BALANCE -> "جسی؛ موجودی حساب‌ها"
                    HomeWidgetKind.BUDGETS -> "جسی؛ نمودار پیشرفت بودجه‌ها"
                    else -> "جسی؛ سهم دسته‌های هزینه"
                }
            )
            val tab = when (type) {
                HomeWidgetKind.BALANCE -> "BANK_ACCOUNTS"
                HomeWidgetKind.BUDGETS -> "ACCOUNTS"
                else -> "TXS"
            }
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("jessi://widget/open/$type/$id")
                putExtra(FinanceReminderScheduler.EXTRA_OPEN_TAB, tab)
            }
            val pending = PendingIntent.getActivity(
                app, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.jessi_widget_image, pending)
            for (button in arrayOf(R.id.jessi_widget_eye, R.id.jessi_widget_previous,
                R.id.jessi_widget_next)) {
                views.setViewVisibility(button,
                    if (type == HomeWidgetKind.BALANCE) View.VISIBLE else View.GONE)
            }
            if (type == HomeWidgetKind.BALANCE) {
                val token = controls.getString("token_$id", null)
                    ?: UUID.randomUUID().toString().also {
                        controls.edit().putString("token_$id", it).apply()
                    }
                views.setImageViewResource(
                    R.id.jessi_widget_eye,
                    if (visible) R.drawable.jessi_widget_eye else R.drawable.jessi_widget_eye_off
                )
                listOf(
                    Triple(R.id.jessi_widget_eye, "eye", 0),
                    Triple(R.id.jessi_widget_previous, "prev", 1),
                    Triple(R.id.jessi_widget_next, "next", 2)
                ).forEach { (button, operation, offset) ->
                    val intent = Intent(app, JessiBalanceWidget::class.java).apply {
                        action = ACTION_CONTROL
                        data = Uri.parse("jessi://widget/control/$id/$operation")
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra("operation", operation)
                        putExtra("token", token)
                    }
                    views.setOnClickPendingIntent(button,
                        PendingIntent.getBroadcast(app, id * 10 + offset, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE))
                }
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun updateScrollable(
        app: Context, manager: AppWidgetManager,
        type: HomeWidgetKind, id: Int
    ) {
        val views = RemoteViews(app.packageName, R.layout.jessi_scroll_widget)
        views.setTextViewText(R.id.jessi_scroll_title,
            if (type == HomeWidgetKind.PAYMENTS) "☐ سررسید اقساط" else "اهداف پس‌انداز")
        views.setTextViewText(R.id.jessi_scroll_empty,
            if (type == HomeWidgetKind.PAYMENTS)
                "قسط پرداخت‌نشده‌ای ثبت نشده" else "هدف فعالی ثبت نشده")
        val serviceIntent = Intent(app, JessiWidgetCollectionService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(JessiWidgetCollectionService.EXTRA_TYPE, type.name)
            data = Uri.parse("jessi://widget/list/$type/$id")
        }
        views.setRemoteAdapter(R.id.jessi_scroll_list, serviceIntent)
        views.setEmptyView(R.id.jessi_scroll_list, R.id.jessi_scroll_empty)
        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("jessi://widget/list-open/$type/$id")
        }
        val template = PendingIntent.getActivity(app, 10_000 + id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        views.setPendingIntentTemplate(R.id.jessi_scroll_list, template)
        manager.updateAppWidget(id, views)
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(id, R.id.jessi_scroll_list)

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
