package ir.jessi.finance.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App-local PIN gate. Only a PBKDF2 hash and a random salt are stored, never the PIN. */
class PinSecurity(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "jessi_app_lock", Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", false) &&
        prefs.contains("salt") && prefs.contains("pin_hash")

    fun biometricsEnabled(): Boolean = prefs.getBoolean("biometric", true)
    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric", enabled).apply()
    }

    fun isValidPin(pin: String): Boolean = pin.matches(Regex("[0-9]{4,6}"))

    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, 180_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun enable(pin: String) {
        require(isValidPin(pin)) { "پین باید ۴ تا ۶ رقم باشد" }
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = digest(pin, salt)
        prefs.edit()
            .putString("salt", encode(salt))
            .putString("pin_hash", encode(hash))
            .putBoolean("enabled", true)
            .putBoolean("biometric", true)
            .remove("failure_count").remove("locked_until")
            .commit()
    }

    /** Returns null for success; otherwise a safe, user-facing error message. */
    fun verify(pin: String): String? {
        if (!isEnabled()) return "قفل ورود فعال نیست"
        val now = System.currentTimeMillis()
        val until = prefs.getLong("locked_until", 0L)
        if (now < until) {
            val seconds = ((until - now + 999L) / 1000L).coerceAtLeast(1L)
            return "تلاش‌های ناموفق زیاد بود؛ ${seconds} ثانیه دیگر امتحان کن"
        }
        val verified = try {
            val salt = decode(prefs.getString("salt", "") ?: "")
            val expected = decode(prefs.getString("pin_hash", "") ?: "")
            isValidPin(pin) && MessageDigest.isEqual(expected, digest(pin, salt))
        } catch (_: Exception) {
            false
        }
        if (verified) {
            clearFailures()
            return null
        }
        val failures = prefs.getInt("failure_count", 0) + 1
        val delay = if (failures >= 15) 300_000L else if (failures >= 10) 60_000L
            else if (failures % 5 == 0) 30_000L else 0L
        prefs.edit().putInt("failure_count", failures)
            .putLong("locked_until", if (delay > 0) now + delay else 0L).apply()
        return if (delay > 0L) "تلاش‌های زیاد؛ بعداً دوباره امتحان کن"
            else "پین صحیح نیست"
    }

    fun clearFailures() {
        prefs.edit().remove("failure_count").remove("locked_until").apply()
    }

    fun change(oldPin: String, newPin: String): String? {
        if (!isValidPin(newPin)) return "پین جدید باید ۴ تا ۶ رقم باشد"
        val error = verify(oldPin)
        if (error != null) return error
        val oldBiometric = biometricsEnabled()
        enable(newPin)
        setBiometricsEnabled(oldBiometric)
        return null
    }

    fun disable(oldPin: String): String? {
        val error = verify(oldPin)
        if (error != null) return error
        prefs.edit().clear().commit()
        return null
    }
}
