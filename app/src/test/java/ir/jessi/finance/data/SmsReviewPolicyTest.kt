package ir.jessi.finance.data

import org.junit.Assert.*
import org.junit.Test

class SmsReviewPolicyTest {
    @Test fun simulationRequiresDistinctSourceAndVisibleNote() {
        val key = SmsReviewPolicy.TEST_PREFIX + "sample-123"
        assertTrue(SmsReviewPolicy.isSimulation(key))
        assertEquals("SMS_TEST", SmsReviewPolicy.sourceFor(key))
        assertEquals("آزمایشی • خرید", SmsReviewPolicy.noteFor(key, "خرید", "آزمایشی"))
        assertEquals("آزمایشی • شبیه‌سازی پیامک بانکی", SmsReviewPolicy.noteFor(key, "", "آزمایشی"))
    }
    @Test fun realSmsRemainsReal() {
        assertFalse(SmsReviewPolicy.isSimulation("abc123"))
        assertEquals("SMS", SmsReviewPolicy.sourceFor("abc123"))
        assertEquals("پیامک بانکی • SINA", SmsReviewPolicy.noteFor("abc123", "", "SINA"))
    }
}
