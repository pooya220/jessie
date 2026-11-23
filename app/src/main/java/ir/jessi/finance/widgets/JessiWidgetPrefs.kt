package ir.jessi.finance.widgets

import android.content.Context
import ir.jessi.finance.data.Budget

/** Settings persist per user, not in the exported database or widget provider. */
object JessiWidgetPrefs {
    const val KEY_SELECTED_BUDGETS = "widget_selected_budgets_v1"
    const val KEY_BALANCE_VISIBLE = "widget_balance_visible"

    private fun prefs(context: Context) =
        context.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)

    /** No saved choice: show the first 5 budgets. Empty saved choice: show none. */
    fun selectedBudgets(context: Context, budgets: List<Budget>): List<Long> {
        val raw = prefs(context).getString(KEY_SELECTED_BUDGETS, null)
            ?: return budgets.take(5).map { it.id }
        val requested = raw.split(",").mapNotNull { it.toLongOrNull() }
            .distinct().take(5)
        val active = budgets.map { it.id }.toSet()
        return requested.filter { it in active }
    }

    fun saveBudgets(context: Context, ids: List<Long>) {
        prefs(context).edit()
            .putString(KEY_SELECTED_BUDGETS, ids.distinct().take(5).joinToString(","))
            .apply()
        JessiWidgetUpdater.request(context)
    }
}
