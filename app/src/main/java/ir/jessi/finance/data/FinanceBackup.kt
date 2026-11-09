package ir.jessi.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Lossless local backup for Jessie schema v4. Full SMS bodies are never present
 * in the database; pending reviews and de-duplication fingerprints are included.
 * The document is unencrypted, so treat it as sensitive financial information.
 */
object FinanceBackup {
    private const val MARKER = "ir.jessi.finance.local-backup"
    private const val BACKUP_VERSION = 1
    private const val DATABASE_VERSION = 4
    private const val MAX_BYTES = 24_000_000
    private val TABLES = listOf(
        "accounts", "transactions", "debts", "budgets", "sms_reviews", "sms_ignored"
    )

    data class Preview(val content: JSONObject) {
        fun count(table: String): Int = content.getJSONObject("tables").getJSONArray(table).length()
        fun summary(): String =
            "حساب: " + count("accounts") + "  •  تراکنش: " + count("transactions") +
            "\nاقساط و بدهی: " + count("debts") + "  •  بودجه: " + count("budgets") +
            "\nپیامک منتظر: " + count("sms_reviews") + "  •  موارد نادیده‌گرفته‌شده: " + count("sms_ignored")
    }

    private fun currentColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("SELECT * FROM " + table + " LIMIT 0").use { cursor ->
            cursor.columnNames.toList()
        }

    private fun rows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): JSONArray =
        JSONArray().apply {
            db.query("SELECT * FROM " + table).use { cursor ->
                while (cursor.moveToNext()) {
                    val item = JSONObject()
                    for (index in 0 until cursor.columnCount) {
                        val value: Any = when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            else -> error("نوع داده غیرقابل پشتیبانی در فایل جسی")
                        }
                        item.put(cursor.getColumnName(index), value)
                    }
                    put(item)
                }
            }
        }

    private fun userPreferences(context: Context): JSONObject {
        val settings = context.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)
        val theme = context.getSharedPreferences("jessi_ui_theme", Context.MODE_PRIVATE)
        return JSONObject()
            .put("period_mode", settings.getString("period_mode", "monthly"))
            .put("show_balance", settings.getBoolean("show_balance", true))
            .put("sms_monitoring_enabled", settings.getBoolean("sms_monitoring_enabled", true))
            .put("sms_review_notifications_enabled",
                settings.getBoolean("sms_review_notifications_enabled", true))
            .put("is_dark", theme.getBoolean("is_dark", true))
    }

    suspend fun exportTo(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val database = FinanceDatabase.get(context)
        val root = database.withTransaction {
            val db = database.openHelper.readableDatabase
            require(db.version == DATABASE_VERSION) { "نسخه پایگاه داده جسی پشتیبانی نمی‌شود" }
            val data = JSONObject()
            TABLES.forEach { data.put(it, rows(db, it)) }
            JSONObject()
                .put("marker", MARKER)
                .put("formatVersion", BACKUP_VERSION)
                .put("databaseVersion", DATABASE_VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put("tables", data)
                .put("preferences", userPreferences(context))
        }
        val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "فایل پشتیبان بزرگ‌تر از حد مجاز است" }
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(bytes)
            it.flush()
        } ?: error("امکان نوشتن فایل انتخابی وجود ندارد")
        Preview(root).summary()
    }

    suspend fun load(context: Context, uri: Uri): Preview = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("فایل باز نشد")
        val bytes = input.use {
            val buffer = ByteArrayOutputStream()
            val part = ByteArray(8192)
            while (true) {
                val size = it.read(part)
                if (size < 0) break
                require(buffer.size() + size <= MAX_BYTES) { "فایل بیش از حد بزرگ است" }
                buffer.write(part, 0, size)
            }
            buffer.toByteArray()
        }
        val result = Preview(JSONObject(String(bytes, StandardCharsets.UTF_8)))
        validate(context, result)
        result
    }

    private fun validate(context: Context, preview: Preview) {
        val root = preview.content
        require(root.getString("marker") == MARKER && root.getInt("formatVersion") == BACKUP_VERSION) {
            "فایل انتخابی پشتیبان معتبر جسی نیست"
        }
        require(root.getInt("databaseVersion") == DATABASE_VERSION) {
            "نسخه پایگاه داده فایل با این جسی سازگار نیست"
        }
        val db = FinanceDatabase.get(context).openHelper.readableDatabase
        val tables = root.getJSONObject("tables")
        TABLES.forEach { table ->
            val expected = currentColumns(db, table).toSet()
            val array = tables.getJSONArray(table)
            require(array.length() <= 300_000) { "تعداد رکوردها غیرمجاز است" }
            val uniqueKeys = HashSet<String>()
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val fields = row.keys().asSequence().toSet()
                require(fields == expected) { "ستون‌های جدول " + table + " معتبر نیستند" }
                for (field in expected) {
                    val value = row.get(field)
                    require(value == JSONObject.NULL || value is String || value is Number) {
                        "نوع داده فایل معتبر نیست"
                    }
                }
                val primary = if (table == "sms_ignored") row.getString("fingerprint")
                    else row.getLong("id").toString()
                require(primary.isNotBlank() && uniqueKeys.add(primary)) {
                    "کلید تکراری در جدول " + table
                }
                if (table != "sms_ignored") require(row.getLong("id") > 0L)
            }
        }
        val accounts = tables.getJSONArray("accounts")
        val accountIds = (0 until accounts.length()).map {
            accounts.getJSONObject(it).getLong("id")
        }.toSet()
        val transactions = tables.getJSONArray("transactions")
        for (i in 0 until transactions.length()) {
            val row = transactions.getJSONObject(i)
            require(row.getLong("accountId") in accountIds && row.getLong("amountToman") > 0L) {
                "تراکنش با حساب نامعتبر یا مبلغ نامعتبر وجود دارد"
            }
        }
        val reviews = tables.getJSONArray("sms_reviews")
        for (i in 0 until reviews.length()) {
            val row = reviews.getJSONObject(i)
            require(row.isNull("accountGuess") || row.getLong("accountGuess") in accountIds) {
                "پیامک منتظر به حساب نامعتبر اشاره دارد"
            }
        }
        val prefs = root.getJSONObject("preferences")
        prefs.getString("period_mode")
        prefs.getBoolean("show_balance")
        prefs.getBoolean("sms_monitoring_enabled")
        prefs.getBoolean("sms_review_notifications_enabled")
        prefs.getBoolean("is_dark")
    }

    suspend fun restore(context: Context, preview: Preview) = withContext(Dispatchers.IO) {
        validate(context, preview)
        val database = FinanceDatabase.get(context)
        val tables = preview.content.getJSONObject("tables")
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            // No data changes until the entire backup passes validation.
            // Any insert error rolls back all deletes and inserts.
            TABLES.reversed().forEach { db.delete(it, null, null) }
            TABLES.forEach { table ->
                val items = tables.getJSONArray(table)
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val values = ContentValues()
                    val columns = currentColumns(db, table)
                    for (column in columns) {
                        when (val value = item.get(column)) {
                            JSONObject.NULL -> values.putNull(column)
                            is String -> values.put(column, value)
                            is Int -> values.put(column, value)
                            is Long -> values.put(column, value)
                            is Double -> values.put(column, value)
                            is Number -> values.put(column, value.toLong())
                            else -> error("نوع داده نامعتبر")
                        }
                    }
                    db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
        }
        val preferences = preview.content.getJSONObject("preferences")
        val financeSaved = context.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("period_mode", preferences.getString("period_mode"))
            .putBoolean("show_balance", preferences.getBoolean("show_balance"))
            .putBoolean("sms_monitoring_enabled", preferences.getBoolean("sms_monitoring_enabled"))
            .putBoolean("sms_review_notifications_enabled",
                preferences.getBoolean("sms_review_notifications_enabled"))
            .commit()
        val themeSaved = context.getSharedPreferences("jessi_ui_theme", Context.MODE_PRIVATE)
            .edit().putBoolean("is_dark", preferences.getBoolean("is_dark")).commit()
        check(financeSaved && themeSaved) {
            "اطلاعات مالی بازیابی شد ولی ذخیره تنظیمات کامل نشد"
        }
    }
}
