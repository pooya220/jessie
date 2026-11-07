package ir.jessi.finance.sms

/** Deliberately conservative. Does not retain or log the original SMS body. */
object BankSmsParser {
    data class Result(val amountToman: Long, val kind: String)
    private val money = Regex("([0-9][0-9,٬،. ]{0,24})\\s*(ریال|تومان)")
    private val incoming = listOf("واریز", "واریزی", "بستانکار", "وارد شد", "دریافت شد")
    private val outgoing = listOf("برداشت", "برداشتی", "خرید", "پرداخت", "بدهکار", "کسر شد")
    private val verification = listOf("رمز پویا", "رمز یکبار", "کد تایید", "کد تأیید", "otp", "verification", "احراز هویت")

    fun normalizeDigits(s: String) = buildString(s.length) {
        s.forEach { c -> append(when (c) {
            in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar()
            else -> c
        }) }
    }

    fun parse(raw: String): Result? {
        val text = normalizeDigits(raw).lowercase()
        if (verification.any { text.contains(it) }) return null
        // Ambiguous account transfers require manual entry rather than double counting.
        if (text.contains("انتقال") || text.contains("کارت به کارت")) return null
        val credit = incoming.any { text.contains(it) }
        val debit = outgoing.any { text.contains(it) }
        if (credit == debit) return null
        val match = money.find(text) ?: return null
        val digits = match.groupValues[1].filter { it.isDigit() }
        if (digits.length !in 1..15) return null
        val rawAmount = digits.toLongOrNull() ?: return null
        val toman = if (match.groupValues[2] == "ریال") {
            if (rawAmount % 10L != 0L) return null
            rawAmount / 10L
        } else rawAmount
        if (toman <= 0L || toman > 100_000_000_000L) return null
        return Result(amountToman = toman, kind = if (credit) "IN" else "OUT")
    }
}
