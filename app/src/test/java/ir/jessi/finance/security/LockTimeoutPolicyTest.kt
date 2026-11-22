package ir.jessi.finance.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimeoutPolicyTest {
    @Test fun shortAppSwitchNeverLocksBeforeConfiguredWindow() {
        val stopped = 5_000L
        assertFalse(LockTimeoutPolicy.hasExpired(stopped, stopped + 1_000L, 5))
        assertFalse(LockTimeoutPolicy.hasExpired(stopped, stopped + 299_999L, 5))
        assertTrue(LockTimeoutPolicy.hasExpired(stopped, stopped + 300_000L, 5))
    }

    @Test fun immediateSettingAndClockResetRemainSecure() {
        assertTrue(LockTimeoutPolicy.hasExpired(1000L, 1001L, 0))
        assertTrue(LockTimeoutPolicy.hasExpired(1000L, 900L, 5))
        assertFalse(LockTimeoutPolicy.hasExpired(0L, 99999999L, 5))
    }
}
