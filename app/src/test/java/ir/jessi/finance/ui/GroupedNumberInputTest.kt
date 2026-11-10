package ir.jessi.finance.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupedNumberInputTest {
    @Test fun separatesEveryThreeDigits() {
        assertEquals("0", groupThousandsForInput("0"))
        assertEquals("123", groupThousandsForInput("123"))
        assertEquals("1,234", groupThousandsForInput("1234"))
        assertEquals("12,345,678", groupThousandsForInput("12345678"))
        assertEquals("", groupThousandsForInput(""))
    }

    @Test fun acceptsPersianAndPastedSeparatorsAsRawDigits() {
        assertEquals("1234567", normalizeNumericInput("۱,۲۳۴,۵۶۷"))
        assertEquals("1234567", normalizeNumericInput("1٬234٬567"))
        assertEquals("3000000", normalizeNumericInput("۳ ۰۰۰ ۰۰۰"))
    }
}
