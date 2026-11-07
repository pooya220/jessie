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

/** No bank amount, sender, or SMS content is shown on the lock screen. */
object SmsReviewNotifications {
    const val EXTRA_REVIEW_ID = "ir.jessi.finance.EXTRA_REVIEW_ID"
    const val ACTION_IGNORE = "ir.jessi.finance.IGNORE_SMS_REVIEW"
    private const val CHANNEL_ID = "sms_review_v1"

    fun show(context: Context, reviewId: Long, kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "بررسی پیامک‌های بانکی", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "ثبت یا نادیده گرفتن پیامک‌های بانکی جدید" })

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
    }

    fun cancel(context: Context, reviewId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(reviewId.toInt())
    }
}
