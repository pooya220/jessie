package ir.jessi.finance.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** User-selected, local JSON financial backup; never sends data to a server.
 * The exported JSON contains financial records and pending SMS metadata and is NOT encrypted.
 */
object FinanceBackup {
    private const val FORMAT = "jessi-finance-backup"
    private const val SCHEMA = 2
    private const val MAX_SIZE = 25 * 1024 * 1024
    private const val MAX_ROWS = 100_000

    data class Snapshot(
        val accounts: List<Account>,
        val transactions: List<Transaction>,
        val debts: List<Debt>,
        val budgets: List<Budget>,
        val reviews: List<SmsReview>,
        val ignored: List<IgnoredSms>,
        val goals: List<SavingsGoal> = emptyList(),
        val movements: List<GoalMovement> = emptyList()
    ) {
        fun summary(): String = "حساب‌ها: ${accounts.size} • تراکنش‌ها: ${transactions.size}" +
            "\nبدهی‌ها و اقساط: ${debts.size} • بودجه‌ها: ${budgets.size}" +
            "\nپیامک‌های در صف: ${reviews.size} • پیامک‌های نادیده‌گرفته‌شده: ${ignored.size}" +
            "\nاهداف پس‌انداز: ${goals.size} • واریز و برداشت اهداف: ${movements.size}"
    }

    private fun JSONArray.objects(): List<JSONObject> {
        require(length() <= MAX_ROWS) { "تعداد رکوردها بیش از حد مجاز است" }
        return List(length()) { i -> getJSONObject(i) }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun Account.json() = JSONObject().apply {
        put("id", id); put("title", title); put("bank", bank)
        put("last4", last4); put("openingToman", openingToman)
    }
    private fun Transaction.json() = JSONObject().apply {
        put("id", id); put("accountId", accountId); put("amountToman", amountToman)
        put("kind", kind); put("category", category); put("note", note)
        put("createdAt", createdAt); put("source", source)
        put("groupId", groupId ?: JSONObject.NULL)
        put("dedupKey", dedupKey ?: JSONObject.NULL)
    }
    private fun Debt.json() = JSONObject().apply {
        put("id", id); put("person", person); put("direction", direction)
        put("totalToman", totalToman); put("paidToman", paidToman)
        put("dueDateJalali", dueDateJalali); put("note", note)
        put("installmentMonths", installmentMonths)
        put("firstInstallmentJalali", firstInstallmentJalali)
    }
    private fun Budget.json() = JSONObject().apply {
        put("id", id); put("category", category); put("monthlyLimitToman", monthlyLimitToman)
    }
    private fun SmsReview.json() = JSONObject().apply {
        put("id", id); put("sender", sender); put("amountToman", amountToman)
        put("kind", kind); put("receivedAt", receivedAt)
        put("fingerprint", fingerprint)
        put("accountGuess", accountGuess ?: JSONObject.NULL)
        put("amountRaw", amountRaw); put("amountUnit", amountUnit); put("bankName", bankName)
    }
    private fun IgnoredSms.json() = JSONObject().apply { put("fingerprint", fingerprint) }
    private fun SavingsGoal.json() = JSONObject().apply {
        put("id", id); put("title", title); put("category", category)
        put("targetToman", targetToman); put("monthlyToman", monthlyToman)
        put("deadlineJalali", deadlineJalali); put("note", note)
        put("createdAt", createdAt); put("archived", archived)
    }
    private fun GoalMovement.json() = JSONObject().apply {
        put("id", id); put("goalId", goalId); put("accountId", accountId)
        put("transactionId", transactionId); put("amountToman", amountToman)
        put("operation", operation); put("createdAt", createdAt)
    }
    private fun <T> List<T>.jsonArray(transform: (T) -> JSONObject): JSONArray =
        JSONArray().also { out -> forEach { out.put(transform(it)) } }

    private fun encode(snapshot: Snapshot): ByteArray {
        val root = JSONObject().apply {
            put("format", FORMAT); put("schema", SCHEMA)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", snapshot.accounts.jsonArray { it.json() })
            put("transactions", snapshot.transactions.jsonArray { it.json() })
            put("debts", snapshot.debts.jsonArray { it.json() })
            put("budgets", snapshot.budgets.jsonArray { it.json() })
            put("smsReviews", snapshot.reviews.jsonArray { it.json() })
            put("smsIgnored", snapshot.ignored.jsonArray { it.json() })
            put("goals", snapshot.goals.jsonArray { it.json() })
            put("goalMovements", snapshot.movements.jsonArray { it.json() })
        }
        val bytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_SIZE) { "حجم فایل پشتیبان بیشتر از حد مجاز است" }
        return bytes
    }

    private fun decode(input: ByteArray): Snapshot {
        require(input.isNotEmpty() && input.size <= MAX_SIZE) { "حجم فایل نامعتبر است" }
        val root = JSONObject(String(input, StandardCharsets.UTF_8))
        val schema = root.getInt("schema")
        require(root.getString("format") == FORMAT && schema in 1..SCHEMA) {
            "این فایل متعلق به نسخه پشتیبانی‌شده جسی نیست"
        }
        val snapshot = Snapshot(
            accounts = root.getJSONArray("accounts").objects().map {
                Account(it.getLong("id"), it.getString("title"), it.getString("bank"),
                    it.getString("last4"), it.getLong("openingToman"))
            },
            transactions = root.getJSONArray("transactions").objects().map {
                Transaction(it.getLong("id"), it.getLong("accountId"), it.getLong("amountToman"),
                    it.getString("kind"), it.getString("category"), it.getString("note"),
                    it.getLong("createdAt"), it.getString("source"),
                    it.optNullableString("groupId"), it.optNullableString("dedupKey"))
            },
            debts = root.getJSONArray("debts").objects().map {
                Debt(it.getLong("id"), it.getString("person"), it.getString("direction"),
                    it.getLong("totalToman"), it.getLong("paidToman"),
                    it.getString("dueDateJalali"), it.getString("note"),
                    it.getInt("installmentMonths"), it.getString("firstInstallmentJalali"))
            },
            budgets = root.getJSONArray("budgets").objects().map {
                Budget(it.getLong("id"), it.getString("category"), it.getLong("monthlyLimitToman"))
            },
            reviews = root.getJSONArray("smsReviews").objects().map {
                SmsReview(it.getLong("id"), it.getString("sender"), it.getLong("amountToman"),
                    it.getString("kind"), it.getLong("receivedAt"), it.getString("fingerprint"),
                    it.optNullableLong("accountGuess"), it.getLong("amountRaw"),
                    it.getString("amountUnit"), it.getString("bankName"))
            },
            ignored = root.getJSONArray("smsIgnored").objects().map {
                IgnoredSms(it.getString("fingerprint"))
            },
            goals = if (schema == 1) emptyList() else root.getJSONArray("goals").objects().map {
                SavingsGoal(
                    id = it.getLong("id"), title = it.getString("title"),
                    category = it.getString("category"), targetToman = it.getLong("targetToman"),
                    monthlyToman = it.getLong("monthlyToman"),
                    deadlineJalali = it.getString("deadlineJalali"),
                    note = it.getString("note"), createdAt = it.getLong("createdAt"),
                    archived = it.getBoolean("archived")
                )
            },
            movements = if (schema == 1) emptyList()
                else root.getJSONArray("goalMovements").objects().map {
                    GoalMovement(
                        id = it.getLong("id"), goalId = it.getLong("goalId"),
                        accountId = it.getLong("accountId"),
                        transactionId = it.getLong("transactionId"),
                        amountToman = it.getLong("amountToman"),
                        operation = it.getString("operation"),
                        createdAt = it.getLong("createdAt")
                    )
                }
        )
        validate(snapshot)
        return snapshot
    }

    private fun validate(data: Snapshot) {
        fun <T> unique(values: List<T>) = values.size == values.toSet().size
        val accountIds = data.accounts.map { it.id }
        require(unique(accountIds) && accountIds.all { it > 0L }) { "شناسه حساب‌های فایل نامعتبر است" }
        require(data.accounts.all { it.title.isNotBlank() && it.openingToman in 0..100_000_000_000_000L }) {
            "اطلاعات یکی از حساب‌ها نامعتبر است"
        }
        require(data.accounts.all { it.last4.isBlank() || it.last4.length == 4 && it.last4.all(Char::isDigit) }) {
            "چهار رقم آخر حساب نامعتبر است"
        }
        require(unique(data.transactions.map { it.id }) && data.transactions.all {
            it.id > 0L && it.accountId in accountIds &&
                it.amountToman in 1..100_000_000_000L &&
                it.kind in setOf("IN","OUT","TRANSFER_IN","TRANSFER_OUT","DEBT_IN","DEBT_OUT","GOAL_IN","GOAL_OUT") &&
                it.createdAt > 0L
        }) { "تراکنش‌ها یا ارتباط آن‌ها با حساب‌ها نامعتبر است" }
        val keys = data.transactions.mapNotNull { it.dedupKey }
        require(unique(keys)) { "شناسه‌های یکتای تراکنش تکراری هستند" }
        require(unique(data.debts.map { it.id }) && data.debts.all {
            it.id > 0L && it.person.isNotBlank() && it.totalToman > 0L &&
                it.paidToman in 0..it.totalToman && it.direction in setOf("I_OWE","OWED_TO_ME") &&
                it.installmentMonths in 0..120 &&
                (it.installmentMonths == 0 ||
                    ir.jessi.finance.ui.JalaliDates.parse(it.firstInstallmentJalali) != null)
        }) { "اطلاعات بدهی و اقساط معتبر نیست" }
        require(unique(data.budgets.map { it.id }) &&
            unique(data.budgets.map { it.category }) &&
            data.budgets.all { it.id > 0L && it.category.isNotBlank() && it.monthlyLimitToman > 0 }) {
            "بودجه‌های فایل معتبر نیست"
        }
        require(unique(data.reviews.map { it.id }) &&
            unique(data.reviews.map { it.fingerprint }) &&
            data.reviews.all {
                it.id > 0L && it.fingerprint.isNotBlank() &&
                    (it.accountGuess == null || it.accountGuess in accountIds) &&
                    it.amountToman >= 0L && it.amountRaw >= 0L &&
                    it.kind in setOf("IN","OUT") &&
                    it.amountUnit in setOf("RIAL","TOMAN","UNKNOWN")
            }) { "صف پیامک‌های فایل معتبر نیست" }
        require(unique(data.ignored.map { it.fingerprint }) &&
            data.ignored.all { it.fingerprint.isNotBlank() }) {
            "شناسه پیامک‌های نادیده‌گرفته‌شده نامعتبر است"
        }
        val goalIds = data.goals.map { it.id }
        require(unique(goalIds) && data.goals.all {
            it.id > 0 && it.title.isNotBlank() && it.title.length <= 120 &&
            it.category.isNotBlank() && it.targetToman in 0L..100_000_000_000L &&
            it.monthlyToman in 0L..100_000_000_000L &&
            (it.targetToman > 0L || it.monthlyToman > 0L) &&
            it.createdAt > 0L &&
            (it.deadlineJalali.isBlank() ||
                ir.jessi.finance.ui.JalaliDates.parse(it.deadlineJalali) != null)
        }) { "اطلاعات اهداف پس‌انداز نامعتبر است" }
        val txById = data.transactions.associateBy { it.id }
        require(unique(data.movements.map { it.id }) &&
            unique(data.movements.map { it.transactionId }) &&
            data.movements.all { m ->
                val tx = txById[m.transactionId]
                m.id > 0 && m.goalId in goalIds && m.accountId in accountIds &&
                    m.amountToman in 1L..100_000_000_000L && m.createdAt > 0L &&
                    m.operation in setOf("DEPOSIT", "WITHDRAW") &&
                    tx != null && tx.accountId == m.accountId &&
                    tx.amountToman == m.amountToman &&
                    tx.kind == if (m.operation == "DEPOSIT") "GOAL_OUT" else "GOAL_IN"
            }
        ) { "واریزها و برداشت‌های اهداف با تراکنش‌های بانکی همخوانی ندارند" }
        val goalTransactionIds = data.transactions
            .filter { it.kind in setOf("GOAL_IN", "GOAL_OUT") }.map { it.id }.toSet()
        require(goalTransactionIds == data.movements.map { it.transactionId }.toSet()) {
            "داده‌های حساب و بانک اهداف ناقص است"
        }
        require(data.goals.all { goal ->
            val balance = SavingsGoalMath.saved(data.movements, goal.id)
            balance >= 0 && (!goal.archived || balance == 0L)
        }) { "مانده یکی از اهداف نامعتبر است" }
    }

    private suspend fun snapshot(context: Context): Snapshot {
        val database = FinanceDatabase.get(context)
        return database.withTransaction {
            val dao = database.dao()
            Snapshot(
                dao.getAccounts(), dao.getTransactions(), dao.getDebts(),
                dao.getBudgets(), dao.getSmsReviews(), dao.getIgnoredSms(),
                dao.getGoals(), dao.getGoalMovements()
            )
        }
    }

    suspend fun export(context: Context, target: Uri): Snapshot {
        val data = snapshot(context)
        val bytes = encode(data)
        val output = context.contentResolver.openOutputStream(target, "w")
            ?: error("فایل مقصد قابل نوشتن نیست")
        output.use { it.write(bytes); it.flush() }
        return data
    }

    suspend fun inspect(context: Context, target: Uri): Snapshot {
        val stream = context.contentResolver.openInputStream(target)
            ?: error("فایل پشتیبان قابل خواندن نیست")
        val out = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_SIZE) error("فایل پشتیبان بزرگ‌تر از ۲۵ مگابایت است")
                out.write(buffer, 0, n)
            }
        }
        return try { decode(out.toByteArray()) }
        catch (e: Exception) { throw IllegalArgumentException("فایل پشتیبان معتبر نیست: ${e.message}", e) }
    }

    suspend fun restore(context: Context, snapshot: Snapshot) {
        validate(snapshot) // Revalidate immediately before any destructive write.
        val database = FinanceDatabase.get(context)
        database.withTransaction {
            val dao = database.dao()
            dao.clearAllGoalMovements()
            dao.clearAllTransactions()
            dao.clearAllGoals()
            dao.clearAllSmsReviews()
            dao.clearAllIgnoredSms()
            dao.clearAllBudgets()
            dao.clearAllDebts()
            dao.clearAllAccounts()

            dao.restoreAccounts(snapshot.accounts)
            dao.restoreTransactions(snapshot.transactions)
            dao.restoreDebts(snapshot.debts)
            dao.restoreBudgets(snapshot.budgets)
            dao.restoreSmsReviews(snapshot.reviews)
            dao.restoreIgnoredSms(snapshot.ignored)
            dao.restoreGoals(snapshot.goals)
            dao.restoreGoalMovements(snapshot.movements)
        }
    }
}
