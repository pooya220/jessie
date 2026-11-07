package ir.jessi.finance.sms

import org.junit.Assert.*
import org.junit.Test

class BankSmsParserTest {
    @Test fun debitInRials() {
        val result = BankSmsParser.parse("برداشت مبلغ ۱٬۲۵۰٬۰۰۰ ریال از حساب")
        assertEquals(125000L, result?.amountToman)
        assertEquals("OUT", result?.kind)
    }
    @Test fun creditInToman() {
        assertEquals(650000L, BankSmsParser.parse("واریز مبلغ ۶۵۰,۰۰۰ تومان")?.amountToman)
    }
    @Test fun otpIsIgnored() {
        assertNull(BankSmsParser.parse("کد تایید 4567، واریز مبلغ 500 تومان"))
    }
    @Test fun ambiguousTransferIsIgnored() {
        assertNull(BankSmsParser.parse("انتقال مبلغ 200000 تومان"))
    }
    @Test fun unknownUnitIgnored() {
        assertNull(BankSmsParser.parse("برداشت 200000"))
    }
}
