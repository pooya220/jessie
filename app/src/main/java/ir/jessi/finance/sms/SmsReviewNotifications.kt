package ir.jessi.finance.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.jessi.finance.MainActivity
import ir.jessi.finance.R

/** Shows a review prompt without leaking amount, sender or SMS text on the lock screen. */
object SmsReviewNotifications {
    const val EXTRA_REVIEW_ID = "ir.jessi.finance.EXTRA_REVIEW_ID"
    const val ACTION_IGNORE = "ir.jessi.finance.IGNORE_SMS_REVIEW"
    const val CHANNEL_ID = "sms_review_v1"
    private const val TEST_ID = 9824121

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "بررسی پیامک‌های بانکی", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "ثبت یا نادیده گرفتن پیامک‌های بانکی جدید" }
        )
    }

    /** A granted POST_NOTIFICATIONS permission alone does not imply this channel can alert. */
    fun blockedReason(context: Context): String? {
        val prefs = context.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("sms_review_notifications_enabled", true))
            return "اعلان در تنظیمات جسی غیرفعال است"
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return "مجوز اعلان اندروید تأیید نشده"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return "اعلان‌های جسی در تنظیمات گوشی مسدود شده"
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE)
            return "کانال اعلان پیامک بانکی غیرفعال است"
        return null
    }

    /** Returns whether Android accepted the notification posting, not whether it was seen. */
    fun show(context: Context, reviewId: Long, kind: String): Boolean {
        if (blockedReason(context) != null) return false
        ensureChannel(context)
        if (blockedReason(context) != null) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("jessi://sms-review/$reviewId")
            putExtra(EXTRA_REVIEW_ID, reviewId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val ignoreIntent = Intent(context, SmsReviewActionReceiver::class.java).apply {
            action = ACTION_IGNORE
            data = Uri.parse("jessi://sms-ignore/$reviewId")
            putExtra(EXTRA_REVIEW_ID, reviewId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(context, reviewId.toInt(), openIntent, flags)
        val ignore = PendingIntent.getBroadcast(context, reviewId.toInt(), ignoreIntent, flags)
        val type = if (kind == "IN") "واریز" else "برداشت"

        manager.notify(reviewId.toInt(), NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jessi)
            .setContentTitle("جسی • پیامک بانکی جدید")
            .setContentText("یک $type جدید منتظر ثبت یا نادیده گرفتن است")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(0, "ثبت و دسته‌بندی", open)
            .addAction(0, "نادیده گرفتن", ignore)
            .build())
        return true
    }

    /** Independent notification test, never creates a financial transaction or fake SMS. */
    fun showTest(context: Context): Boolean {
        if (blockedReason(context) != null) return false
        ensureChannel(context)
        if (blockedReason(context) != null) return false
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context, TEST_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, flags
        )
        context.getSystemService(NotificationManager::class.java).notify(
            TEST_ID, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jessi)
                .setContentTitle("جسی • تست اعلان")
                .setContentText("اعلان‌های جسی فعال هستند؛ این پیام تراکنش نیست.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        return true
    }

    fun cancel(context: Context, reviewId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(reviewId.toInt())
    }
}
