package ir.jessi.finance.sms

import org.junit.Assert.*
import org.junit.Test

class BankSmsParserTest {
    @Test fun debitInRials() {
        val result = BankSmsParser.parse("برداشت مبلغ ۱٬۲۵۰٬۰۰۰ ریال از حساب")
        assertEquals(125000L, result?.amountToman)
        assertEquals("OUT", result?.kind)
        assertEquals(BankSmsParser.RIAL, result?.amountUnit)
    }

    @Test fun creditInToman() {
        assertEquals(650000L, BankSmsParser.parse("واریز مبلغ ۶۵۰,۰۰۰ تومان")?.amountToman)
    }

    @Test fun tejaratWithBalanceAndDateUsesDebitNotBalance() {
        val sms = """
            *بانک تجارت*
            حساب: 0136000000000
            برداشت: 10,514,500 ریال
            از طريق: شتاب
            مانده: 27,410,752 ریال
            1405/08/16
            20:40
        """.trimIndent()
        val r = BankSmsParser.parse(sms)
        assertNotNull(r)
        assertEquals("OUT", r?.kind)
        assertEquals(10_514_500L, r?.amountRaw)
        assertEquals(1_051_450L, r?.amountToman)
        assertEquals("بانک تجارت", r?.bankName)
    }

    @Test fun signedNumberWithBidiMarksAndNoUnitRequiresReview() {
        val sms = "\u202A300359900000\u202C\n900,000-\n1405/7/11-2:14\nمانده:8,988,388"
        val r = BankSmsParser.parse(sms, "300359900000")
        assertNotNull(r)
        assertEquals("OUT", r?.kind)
        assertEquals(900_000L, r?.amountRaw)
        assertEquals(0L, r?.amountToman)
        assertEquals(BankSmsParser.UNKNOWN, r?.amountUnit)
    }

    @Test fun sinaWithArabicYehParsesDebit() {
        val sms = """
            بانک سينا
            برداشت از 412-700-0000000-1
            مبلغ  8,990,000 ريال
            مانده 3,819,181 ريال
            زمان : 1405/8/17 ; 11:03:44
        """.trimIndent()
        val r = BankSmsParser.parse(sms)
        assertNotNull(r)
        assertEquals(899_000L, r?.amountToman)
        assertEquals(8_990_000L, r?.amountRaw)
        assertEquals(BankSmsParser.RIAL, r?.amountUnit)
        assertEquals("بانک سینا", r?.bankName)
        assertEquals("OUT", r?.kind)
    }

    @Test fun otpIgnoredEvenIfFinancialWordsPresent() {
        assertNull(BankSmsParser.parse("کد تایيد 4567، برداشت مبلغ 500 تومان"))
    }

    @Test fun ambiguousTransferIgnored() {
        assertNull(BankSmsParser.parse("انتقال مبلغ 200000 تومان"))
    }

    @Test fun unknownUnitWithoutBankStructureIsNotImported() {
        assertNull(BankSmsParser.parse("برداشت 200000"))
        assertNull(BankSmsParser.parse("مانده: 900000\n1405/07/11"))
        assertNull(BankSmsParser.parse("900,000-"))
    }

    @Test fun normalizeBidiAndArabicNumbers() {
        assertEquals("بانک سینا 123", BankSmsParser.normalizeDigits("\u202Aبانك سينا ١٢٣\u202C"))
    }

    @Test fun saldoOnlyDoesNotCreateFakeDebit() {
        assertNull(BankSmsParser.parse("برداشت از حساب\nمانده 20,000 ریال"))
    }
}
