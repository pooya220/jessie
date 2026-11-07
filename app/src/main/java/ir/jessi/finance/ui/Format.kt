package ir.jessi.finance.ui

import ir.jessi.finance.sms.BankSmsParser
import ir.jessi.finance.data.Transaction
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

fun digitsFa(raw: String) = raw.map { if (it in '0'..'9') ('۰'.code + it.code - '0'.code).toChar() else it }.joinToString("")
fun toman(value: Long) = digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(value)) + " تومان"
fun readToman(input: String): Long? {
    val cleaned = BankSmsParser.normalizeDigits(input).trim()
        .replace(",", "").replace("٬", "").replace("،", "").replace(" ", "")
    return cleaned.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
}

object Jalali {
    data class Date(val year: Int, val month: Int, val day: Int) {
        val monthKey: String get() = "%04d-%02d".format(Locale.US, year, month)
        override fun toString(): String = digitsFa("%04d/%02d/%02d".format(Locale.US, year, month, day))
    }
    fun of(timestamp: Long): Date {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return convert(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    fun convert(gy: Int, gm: Int, gd: Int): Date {
        val months = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + months[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        return if (days < 186) Date(jy, 1 + days / 31, 1 + days % 31)
        else Date(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
    }
}

fun accountBalance(accountId: Long, initial: Long, transactions: List<Transaction>): Long =
    transactions.filter { it.accountId == accountId }.fold(initial) { balance, tx ->
        balance + (if (tx.kind in setOf("IN", "TRANSFER_IN", "DEBT_IN")) tx.amountToman else -tx.amountToman)
    }

val categories = listOf("خوراک", "سوخت", "خرید", "قبوض", "تفریح", "دانشگاه", "سلامت", "حمل‌ونقل", "حقوق", "سایر")
