package ir.jessi.finance.sms

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A privacy-safe last-event trace. NEVER store SMS body, account, sender or amount.
 * Lets the user distinguish missing SMS broadcast from blocked notifications.
 */
object SmsDiagnostics {
    private const val PREF = "jessi_sms_diagnostics"

    const val RECEIVED = "RECEIVED"
    const val UNRECOGNIZED = "UNRECOGNIZED"
    const val QUEUED = "QUEUED"
    const val POSTED = "POSTED"
    const val BLOCKED = "BLOCKED"
    const val DUPLICATE = "DUPLICATE"
    const val DISABLED = "DISABLED"
    const val ERROR = "ERROR"

    fun mark(context: Context, state: String) {
        val settings = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        settings.edit()
            .putString("last_state", state)
            .putLong("last_event_at", now)
            .apply()
        if (state == RECEIVED) {
            settings.edit().putLong("last_received_at", now).apply()
        }
    }

    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val state = prefs.getString("last_state", "") ?: ""
        val eventAt = prefs.getLong("last_event_at", 0)
        val receivedAt = prefs.getLong("last_received_at", 0)
        val time = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        val message = when (state) {
            RECEIVED -> "پیامک دریافت شده؛ بررسی هنوز در جریان است"
            UNRECOGNIZED -> "پیامک دریافت شده، اما الگوی تراکنش تأیید نشده"
            QUEUED -> "تراکنش شناسایی و در صف بررسی قرار گرفت"
            POSTED -> "تراکنش در صف ثبت و اعلان به اندروید ارسال شد"
            BLOCKED -> "تراکنش در صف است؛ اعلان توسط تنظیمات/مجوز متوقف شد"
            DUPLICATE -> "تراکنش قبلاً در صف، ثبت یا نادیده گرفته شده"
            DISABLED -> "بررسی پیامک از تنظیمات جسی غیرفعال است"
            ERROR -> "در پردازش یا ذخیره پیامک خطا رخ داد"
            else -> "هنوز رویداد دریافت پیامک ثبت نشده"
        }
        val last = if (eventAt > 0) " • " + time.format(Date(eventAt)) else ""
        val lastReceived = if (receivedAt > 0)
            "\nآخرین دریافت SMS: " + time.format(Date(receivedAt))
        else "\nهنوز دریافت SMS توسط جسی ثبت نشده"
        return message + last + lastReceived
    }
}
