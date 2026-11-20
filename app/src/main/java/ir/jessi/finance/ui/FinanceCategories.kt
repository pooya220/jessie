package ir.jessi.finance.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * The user's locally editable income and expense pickers. Category labels in
 * historical transactions are stored in Room, independently of the picker.
 * A removed label stops appearing as a new choice; deleting it never deletes
 * a transaction or a budget.
 */
object FinanceCategories {
    const val EXPENSE = "OUT"
    const val INCOME = "IN"

    val expenseDefaults = listOf(
        "خوراک", "سوخت", "خرید", "قبوض", "تفریح", "دانشگاه",
        "سلامت", "حمل‌ونقل", "حقوق", "سایر"
    )
    val incomeDefaults = listOf("درآمد", "حقوق", "هدیه", "بازگشت وجه", "سایر")

    // Preserve the existing salary-period detector and a safe fallback for
    // uncategorized SMS or manually entered transactions.
    fun isProtected(kind: String, name: String): Boolean = when (kind) {
        EXPENSE -> name == "سایر"
        INCOME -> name == "حقوق" || name == "درآمد"
        else -> true
    }

    private const val PREF_NAME = "jessi_finance_categories_v1"
    private const val EXPENSE_KEY = "expense"
    private const val INCOME_KEY = "income"
    private const val MAX_LABEL_LENGTH = 36
    private const val MAX_CATEGORIES = 80

    private var context: Context? = null
    private var expenseState by mutableStateOf(expenseDefaults)
    private var incomeState by mutableStateOf(incomeDefaults)

    val expense: List<String> get() = expenseState
    val income: List<String> get() = incomeState

    private fun key(kind: String): String =
        if (kind == INCOME) INCOME_KEY else EXPENSE_KEY

    fun defaults(kind: String): List<String> =
        if (kind == INCOME) incomeDefaults else expenseDefaults

    private fun cleaned(kind: String, supplied: List<String>): List<String> {
        val values = supplied.map { it.trim() }
            .filter { it.isNotBlank() && it.length <= MAX_LABEL_LENGTH }
            .distinct().take(MAX_CATEGORIES)
        val required = defaults(kind).filter { isProtected(kind, it) }
        return (required + values).distinct().take(MAX_CATEGORIES)
    }

    fun initialize(appContext: Context) {
        val saved = appContext.applicationContext.getSharedPreferences(
            PREF_NAME, Context.MODE_PRIVATE
        )
        fun load(kind: String): List<String> {
            val raw = saved.getString(key(kind), null) ?: return defaults(kind)
            return runCatching {
                val arr = JSONArray(raw)
                cleaned(kind, List(arr.length()) { arr.getString(it) })
            }.getOrElse { defaults(kind) }
        }
        context = appContext.applicationContext
        expenseState = load(EXPENSE)
        incomeState = load(INCOME)
    }

    /** Reads the saved category preferences without relying on an Activity. */
    fun readSaved(appContext: Context, kind: String): List<String> {
        val saved = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = saved.getString(key(kind), null) ?: return defaults(kind)
        return runCatching {
            val arr = JSONArray(raw)
            cleaned(kind, List(arr.length()) { arr.getString(it) })
        }.getOrElse { defaults(kind) }
    }

    private fun save(kind: String, value: List<String>) {
        val prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val safe = cleaned(kind, value)
        if (kind == INCOME) incomeState = safe else expenseState = safe
        prefs?.edit()?.putString(key(kind), JSONArray(safe).toString())?.apply()
    }

    fun validation(kind: String, label: String, old: String? = null): String? {
        if (kind != INCOME && kind != EXPENSE) return "نوع دسته‌بندی نامعتبر است"
        val name = label.trim()
        if (name.isEmpty()) return "نام دسته‌بندی را وارد کن"
        if (name.length > MAX_LABEL_LENGTH)
            return "نام دسته‌بندی نباید بیشتر از ۳۶ حرف باشد"
        if (old != null && isProtected(kind, old))
            return "برای حفظ محاسبات، نام این دسته سیستمی قابل تغییر نیست"
        val list = if (kind == INCOME) income else expense
        if (list.any { it == name && it != old })
            return "دسته‌ای با همین نام وجود دارد"
        if (old == null && list.size >= MAX_CATEGORIES)
            return "حداکثر تعداد دسته‌بندی‌ها ثبت شده"
        if (old != null && old !in list)
            return "این دسته دیگر موجود نیست"
        return null
    }

    fun add(kind: String, name: String): String? {
        validation(kind, name)?.let { return it }
        val list = if (kind == INCOME) income else expense
        save(kind, list + name.trim())
        return null
    }

    /** Called after a successful transactional Room rename. */
    fun rename(kind: String, old: String, name: String): String? {
        validation(kind, name, old)?.let { return it }
        val list = if (kind == INCOME) income else expense
        save(kind, list.map { if (it == old) name.trim() else it })
        return null
    }

    fun delete(kind: String, name: String): String? {
        if (isProtected(kind, name)) return "این دسته سیستمی قابل حذف نیست"
        val list = if (kind == INCOME) income else expense
        if (name !in list) return "دسته پیدا نشد"
        // Do NOT delete historical records or budget amounts.
        save(kind, list - name)
        return null
    }

    /** Restores the category catalog from backup. Old backups don't affect it. */
    fun restore(appContext: Context, expense: List<String>?, income: List<String>?) {
        if (context == null) initialize(appContext)
        if (expense != null) save(EXPENSE, expense)
        if (income != null) save(INCOME, income)
    }
}
