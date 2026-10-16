package com.example.authentication.engine

import org.apache.commons.codec.binary.Base32
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin implementation of RFC 6238 (TOTP).
 * No state, no Android dependencies — fully unit-testable.
 */
object TotpEngine {

    const val PERIOD_MILLIS = 30_000L

    /**
     * Generates a 6-digit TOTP code for the given Base32 secret and timestamp.
     *
     * @param secretBase32 The raw Base32-encoded secret from the QR code / otpauth:// URI.
     * @param timeMillis   The current Unix time in milliseconds (e.g. System.currentTimeMillis()).
     * @return             A zero-padded 6-digit string, e.g. "034521", or "------" on invalid key.
     */
    fun generateCode(secretBase32: String, timeMillis: Long): String {
        return try {
            // Step 1: Determine the current 30-second time window (RFC 6238)
            val timeStep = timeMillis / 1000L / 30L

            // Step 2: Decode the Base32 secret into raw bytes (sanitize spaces and hyphens)
            val cleanSecret = secretBase32.replace(" ", "").replace("-", "").uppercase().trim()
            val keyBytes = Base32().decode(cleanSecret)
            if (keyBytes.isEmpty()) return "------"

            // Step 3: Pack the time step into an 8-byte big-endian array
            val data = ByteBuffer.allocate(8).putLong(timeStep).array()

            // Step 4: Compute HMAC-SHA1
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            val hash = mac.doFinal(data)

            // Step 5: Dynamic truncation — offset is the low nibble of the last byte
            val offset = hash.last().toInt() and 0x0F
            val binary =
                ((hash[offset].toInt()     and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8)  or
                 (hash[offset + 3].toInt() and 0xFF)

            // Step 6: Reduce to 6 digits, zero-pad if necessary
            (binary % 1_000_000).toString().padStart(6, '0')
        } catch (e: Exception) {
            "------"
        }
    }

    /**
     * Returns how many seconds remain in the current 30-second TOTP window (30 down to 1).
     */
    fun secondsRemaining(timeMillis: Long): Int {
        val millisIntoWindow = timeMillis % PERIOD_MILLIS
        val remainingMillis = PERIOD_MILLIS - millisIntoWindow
        return ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, 30)
    }

    /**
     * Returns progress fraction remaining in the current 30-second TOTP window (1.0f down to 0.0f).
     */
    fun progressRemaining(timeMillis: Long): Float {
        val millisIntoWindow = timeMillis % PERIOD_MILLIS
        val remainingMillis = PERIOD_MILLIS - millisIntoWindow
        return (remainingMillis / 30_000f).coerceIn(0f, 1f)
    }
}
