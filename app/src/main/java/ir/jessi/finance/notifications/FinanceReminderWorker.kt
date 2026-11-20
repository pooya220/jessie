package ir.jessi.finance.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.jessi.finance.MainActivity
import ir.jessi.finance.R
import ir.jessi.finance.data.FinanceDatabase
import ir.jessi.finance.data.FinancePeriodEngine
import ir.jessi.finance.data.FinanceReminderRules
import ir.jessi.finance.ui.Jalali
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Notifications are local to this device, no server and no banking data sent out.
 * WorkManager runs after the app closes and after device restarts, subject to
 * Android background scheduling. The check also runs after in-app edits.
 */
object FinanceReminderScheduler {
    const val EXTRA_OPEN_TAB = "ir.jessi.finance.REMINDER_OPEN_TAB"
    const val PREF_BUDGET_ENABLED = "budget_reminders_enabled"
    const val PREF_INSTALLMENT_ENABLED = "installment_reminders_enabled"

    private const val PERIODIC_WORK = "jessi_finance_reminders_periodic_v1"
    private const val NOW_WORK = "jessi_finance_reminders_check_v1"
    private const val CHANNEL_BUDGET = "jessi_budget_90_v1"
    private const val CHANNEL_INSTALLMENT = "jessi_installment_due_v1"

    fun start(context: Context) {
        ensureChannels(context)
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FinanceReminderWorker>(6, TimeUnit.HOURS).build()
        )
        checkSoon(context)
    }

    fun checkSoon(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FinanceReminderWorker>().build()
        )
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_BUDGET, "هشدار ۹۰٪ بودجه", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "اعلان یک‌بار در هر دوره، هنگام رسیدن هزینه به ۹۰٪ بودجه" })
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_INSTALLMENT, "یادآوری سررسید اقساط", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "یادآوری روز سررسید قسط‌های پرداخت‌نشده" })
    }

    private fun canNotify(context: Context, channel: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannels(context)
        return manager.getNotificationChannel(channel)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }

    private fun openApp(context: Context, tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, tab)
        }
        return PendingIntent.getActivity(
            context, if (tab == "DEBTS") 8812 else 8811, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** One process-level gate prevents simultaneous checks from posting twice. */
    private val lock = Any()

    suspend fun checkNow(context: Context) {
        val dao = FinanceDatabase.get(context).dao()
        val prefs = context.getSharedPreferences(
            "jessi_finance_settings", Context.MODE_PRIVATE
        )
        val sent = context.getSharedPreferences(
            "jessi_finance_reminders_sent_v1", Context.MODE_PRIVATE
        )
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(NotificationManager::class.java)

        if (prefs.getBoolean(PREF_BUDGET_ENABLED, true) &&
            canNotify(context, CHANNEL_BUDGET)
        ) {
            val transactions = dao.getTransactions()
            val budgets = dao.getBudgets()
            val periodMode = prefs.getString(
                "period_mode", FinancePeriodEngine.MONTHLY
            ) ?: FinancePeriodEngine.MONTHLY
            val period = FinancePeriodEngine.periods(transactions, periodMode, now).first()
            for (alert in FinanceReminderRules.budgetAlerts(budgets, transactions, period)) {
                synchronized(lock) {
                    if (sent.getBoolean(alert.key, false)) continue
                    val shownPercent = alert.percent.coerceAtMost(999)
                    val number = NumberFormat.getIntegerInstance(Locale.US)
                        .format(alert.spentToman)
                    manager.notify("jessi_budget_" + alert.budgetId, 8811,
                        NotificationCompat.Builder(context, CHANNEL_BUDGET)
                            .setSmallIcon(R.drawable.ic_stat_jessi)
                            .setContentTitle("جسی • هشدار بودجه " + alert.category)
                            .setContentText("مصرف بودجه به " + shownPercent +
                                "٪ رسیده (" + number + " تومان).")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            .setContentIntent(openApp(context, "ACCOUNTS"))
                            .setAutoCancel(true)
                            .build()
                    )
                    sent.edit().putBoolean(alert.key, true).commit()
                }
            }
        }

        if (prefs.getBoolean(PREF_INSTALLMENT_ENABLED, true) &&
            canNotify(context, CHANNEL_INSTALLMENT)
        ) {
            val today = Jalali.of(now)
            val debts = dao.getDebts()
            for (due in FinanceReminderRules.installmentsDueToday(debts, today)) {
                synchronized(lock) {
                    if (sent.getBoolean(due.key, false)) continue
                    val amount = NumberFormat.getIntegerInstance(Locale.US)
                        .format(due.amountToman)
                    manager.notify("jessi_due_" + due.debtId, 8812,
                        NotificationCompat.Builder(context, CHANNEL_INSTALLMENT)
                            .setSmallIcon(R.drawable.ic_stat_jessi)
                            .setContentTitle("جسی • امروز سررسید قسط " + due.person + " است")
                            .setContentText("قسط " + due.installmentNumber + " از " +
                                due.installmentMonths + " • " + amount +
                                " تومان؛ پرداخت را فراموش نکن.")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            .setContentIntent(openApp(context, "DEBTS"))
                            .setAutoCancel(true)
                            .build()
                    )
                    sent.edit().putBoolean(due.key, true).commit()
                }
            }
        }
    }

    fun cancelDebt(context: Context, debtId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel("jessi_due_" + debtId, 8812)
    }
}

class FinanceReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        FinanceReminderScheduler.checkNow(applicationContext)
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}
