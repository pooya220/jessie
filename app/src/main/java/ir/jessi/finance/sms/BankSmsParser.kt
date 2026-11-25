package ir.jessi.finance.sms

/** Parses confirmed transaction patterns without retaining/logging the original message. */
object BankSmsParser {
    const val RIAL = "RIAL"
    const val TOMAN = "TOMAN"
    const val UNKNOWN = "UNKNOWN"

    data class Result(
        val amountToman: Long,
        val kind: String,
        val amountRaw: Long,
        val amountUnit: String,
        val bankName: String = ""
    )

    // The explicit currency amount must not be confused with a reported balance.
    private val currencyAmount = Regex("""([0-9][0-9,٬،. ]{0,24})\s*(ریال|تومان)""")
    // Some banks send only a negative amount on a line, followed by date and balance.
    private val signedLine = Regex("""^(?:مبلغ\s*[:：]?\s*)?([0-9][0-9,٬،.]{0,20})\s*([+＋\-−])\s*$""")
    private val jalaliDate = Regex("""14[0-9]{2}/[0-9]{1,2}/[0-9]{1,2}""")

    private val incoming = listOf("واریز", "واریزی", "بستانکار", "وارد شد", "دریافت شد")
    private val outgoing = listOf("برداشت", "برداشتی", "خرید", "پرداخت", "بدهکار", "کسر شد")
    private val verification = listOf(
        "رمز پویا", "رمز یکبار", "رمز یک بار", "کد تایید", "کد تأیید",
        "otp", "verification", "احراز هویت"
    )

    /** Also removes bidi-format marks present in copied Persian bank SMS texts. */
    fun normalizeDigits(s: String) = buildString(s.length) {
        s.forEach { c ->
            if (c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' ||
                c == '\u200E' || c == '\u200F' || c == '\u061C'
            ) return@forEach
            append(when (c) {
                in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                else -> c
            })
        }
    }

    private fun number(raw: String): Long? {
        val clean = raw.filter(Char::isDigit)
        if (clean.length !in 1..15) return null
        return clean.toLongOrNull()?.takeIf { it > 0L }
    }

    /** Financial storage uses whole tomans; round a confirmed rial amount to nearest toman. */
    fun rialToToman(raw: Long): Long = raw / 10L + if (raw % 10L >= 5L) 1L else 0L

    private fun validAmount(toman: Long): Boolean = toman in 1..100_000_000_000L

    fun parse(raw: String, sender: String = ""): Result? {
        val text = normalizeDigits(raw).lowercase()
        val from = normalizeDigits(sender).lowercase()
        if (verification.any { text.contains(it) }) return null
        if (text.contains("انتقال") || text.contains("کارت به کارت")) return null

        val context = "$text $from"
        val bank = when {
            context.contains("بانک تجارت") || context.contains("tejarat") -> "بانک تجارت"
            context.contains("بانک سینا") || context.contains("sina") -> "بانک سینا"
            else -> ""
        }
        val credit = incoming.any { text.contains(it) }
        val debit = outgoing.any { text.contains(it) }
        if (credit && debit) return null

        val candidates = currencyAmount.findAll(text).filter { match ->
            // Don't ever use a balance as the value being spent/received.
            val lineStart = text.lastIndexOf('\n', match.range.first).let { if (it == -1) 0 else it + 1 }
            val prefix = text.substring(lineStart, match.range.first)
            !prefix.contains("مانده") && !prefix.contains("موجودی")
        }.toList()
        if (credit != debit && candidates.isNotEmpty()) {
            val match = candidates.first()
            val amount = number(match.groupValues[1]) ?: return null
            val unit = if (match.groupValues[2] == "ریال") RIAL else TOMAN
            val toman = if (unit == RIAL) rialToToman(amount) else amount
            if (!validAmount(toman)) return null
            return Result(toman, if (credit) "IN" else "OUT", amount, unit, bank)
        }

        // Signed credit/debit with a Jalali date and balance requires manual unit selection.
        // Do not infer Rial/Toman. User MUST choose the unit before posting.
        if (!text.contains("مانده") || !jalaliDate.containsMatchIn(text)) return null
        val line = text.lineSequence().map { it.trim() }
            .mapNotNull { signedLine.matchEntire(it) }.toList().singleOrNull() ?: return null
        val amount = number(line.groupValues[1]) ?: return null
        if (!validAmount(amount)) return null
        val kind = if (line.groupValues[2] in listOf("+", "＋")) "IN" else "OUT"
        if ((credit && kind != "IN") || (debit && kind != "OUT")) return null
        return Result(0L, kind, amount, UNKNOWN, bank)
    }
}
