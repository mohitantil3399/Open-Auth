package com.example.authentication

import com.example.authentication.engine.TotpEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpEngineTest {

    // RFC 6238 Appendix B test vector key for SHA1 (20 bytes: "12345678901234567890")
    // In Base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
    private val rfcBase32Key = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun testRfc6238Vectors() {
        // T = 59s -> 59000ms
        assertEquals("287082", TotpEngine.generateCode(rfcBase32Key, 59_000L))

        // T = 1111111109s
        assertEquals("081804", TotpEngine.generateCode(rfcBase32Key, 1_111_111_109_000L))

        // T = 1111111111s
        assertEquals("050471", TotpEngine.generateCode(rfcBase32Key, 1_111_111_111_000L))

        // T = 1234567890s
        assertEquals("005924", TotpEngine.generateCode(rfcBase32Key, 1_234_567_890_000L))

        // T = 2000000000s
        assertEquals("279037", TotpEngine.generateCode(rfcBase32Key, 2_000_000_000_000L))
    }

    @Test
    fun testBoundaryTransitions() {
        // Test transition right at :00 (e.g. 0ms, 29999ms, 30000ms)
        // Window 0: [0, 29999ms]
        val codeAt0 = TotpEngine.generateCode(rfcBase32Key, 0L)
        val codeAt29999 = TotpEngine.generateCode(rfcBase32Key, 29_999L)
        assertEquals("Code must remain same throughout window 0", codeAt0, codeAt29999)

        // Window 1: [30000ms, 59999ms]
        val codeAt30000 = TotpEngine.generateCode(rfcBase32Key, 30_000L)
        val codeAt59999 = TotpEngine.generateCode(rfcBase32Key, 59_999L)
        assertEquals("Code must remain same throughout window 1", codeAt30000, codeAt59999)
        assert(codeAt0 != codeAt30000) { "Code must change at :30 boundary" }

        // Window 2: [60000ms, 89999ms]
        val codeAt60000 = TotpEngine.generateCode(rfcBase32Key, 60_000L)
        assert(codeAt30000 != codeAt60000) { "Code must change at :00 boundary (60s)" }
    }

    @Test
    fun testSecondsRemaining() {
        // At start of window (0s), 30 seconds remaining
        assertEquals(30, TotpEngine.secondsRemaining(0L))
        assertEquals(30, TotpEngine.secondsRemaining(30_000L))
        assertEquals(30, TotpEngine.secondsRemaining(60_000L))

        // At 29s, 1 second remaining
        assertEquals(1, TotpEngine.secondsRemaining(29_000L))
        assertEquals(1, TotpEngine.secondsRemaining(59_000L))

        // At 15s, 15 seconds remaining
        assertEquals(15, TotpEngine.secondsRemaining(15_000L))
    }
}
