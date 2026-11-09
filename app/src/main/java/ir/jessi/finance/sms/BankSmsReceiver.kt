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
        SmsDiagnostics.mark(context, SmsDiagnostics.RECEIVED)
        val preferences = context.getSharedPreferences("jessi_finance_settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("sms_monitoring_enabled", true)) {
            SmsDiagnostics.mark(context, SmsDiagnostics.DISABLED)
            return
        }
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (_: Exception) {
            SmsDiagnostics.mark(context, SmsDiagnostics.ERROR)
            return
        }
        if (messages.isEmpty()) {
            SmsDiagnostics.mark(context, SmsDiagnostics.UNRECOGNIZED)
            return
        }
        val text = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val sender = messages.first().originatingAddress.orEmpty()
        val timestamp = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val parsed = BankSmsParser.parse(text, sender)
        if (parsed == null) {
            SmsDiagnostics.mark(context, SmsDiagnostics.UNRECOGNIZED)
            return
        }
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
                        fingerprint = hash, accountGuess = guessed,
                        amountRaw = parsed.amountRaw, amountUnit = parsed.amountUnit,
                        bankName = parsed.bankName
                    ))
                    if (reviewId > 0L) {
                        SmsDiagnostics.mark(context, SmsDiagnostics.QUEUED)
                        val sent = SmsReviewNotifications.show(context, reviewId, parsed.kind)
                        SmsDiagnostics.mark(context,
                            if (sent) SmsDiagnostics.POSTED else SmsDiagnostics.BLOCKED
                        )
                    } else {
                        SmsDiagnostics.mark(context, SmsDiagnostics.DUPLICATE)
                    }
                }
            } catch (_: Exception) {
                // Report a category only. Never leak private bank text to logs or settings.
                SmsDiagnostics.mark(context, SmsDiagnostics.ERROR)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
