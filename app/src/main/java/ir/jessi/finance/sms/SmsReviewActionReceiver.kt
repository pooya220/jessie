package ir.jessi.finance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.jessi.finance.data.FinanceDatabase
import kotlinx.coroutines.runBlocking

/** Explicit, non-exported notification action. Never launches the UI for ignore. */
class SmsReviewActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsReviewNotifications.ACTION_IGNORE) return
        val reviewId = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
        if (reviewId <= 0L) return
        val pending = goAsync()
        Thread {
            try {
                runBlocking { FinanceDatabase.get(context).dao().ignoreReview(reviewId) }
                SmsReviewNotifications.cancel(context, reviewId)
            } catch (_: Exception) {
                // Do not expose SMS data in logs.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
