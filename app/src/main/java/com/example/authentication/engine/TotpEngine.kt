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

    /**
     * Generates a 6-digit TOTP code for the given Base32 secret and timestamp.
     *
     * @param secretBase32 The raw Base32-encoded secret from the QR code / otpauth:// URI.
     * @param timeMillis   The current Unix time in milliseconds (e.g. System.currentTimeMillis()).
     * @return             A zero-padded 6-digit string, e.g. "034521".
     */
    fun generateCode(secretBase32: String, timeMillis: Long): String {
        // Step 1: Determine the current 30-second time window
        val timeStep = timeMillis / 1000L / 30L

        // Step 2: Decode the Base32 secret into raw bytes
        val keyBytes = Base32().decode(secretBase32.uppercase().trim())

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
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    /**
     * Returns how many seconds remain in the current 30-second TOTP window.
     */
    fun secondsRemaining(timeMillis: Long): Int {
        return 30 - ((timeMillis / 1000L) % 30L).toInt()
    }
}
