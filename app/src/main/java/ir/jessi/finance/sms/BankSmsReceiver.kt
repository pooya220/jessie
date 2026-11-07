package ir.jessi.finance.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.jessi.finance.data.FinanceDatabase
import ir.jessi.finance.data.SmsReview
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val text = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val sender = messages.first().originatingAddress.orEmpty()
        val timestamp = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val parsed = BankSmsParser.parse(text) ?: return
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    val dao = FinanceDatabase.get(context).dao()
                    val accounts = dao.getAccounts()
                    val normalized = BankSmsParser.normalizeDigits(text)
                    val matches = accounts.filter { it.last4.length == 4 && normalized.contains(it.last4) }
                    // Only auto-book when exactly one unambiguous registered account matches.
                    val guessed = matches.singleOrNull()?.id
                    val hashInput = "$sender|$text|$timestamp"
                    val hash = MessageDigest.getInstance("SHA-256")
                        .digest(hashInput.toByteArray()).joinToString("") { "%02x".format(it) }
                    // Never change balances without explicit user approval.
                    // No original SMS, account number or OTP is stored.
                    val reviewId = dao.enqueueReview(SmsReview(
                        sender = sender, amountToman = parsed.amountToman,
                        kind = parsed.kind, receivedAt = timestamp,
                        fingerprint = hash, accountGuess = guessed
                    ))
                    if (reviewId > 0L) {
                        SmsReviewNotifications.show(context, reviewId, parsed.kind)
                    }
                }
            } catch (_: Exception) {
                // Do not leak private bank text to logcat.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
