package com.example.authentication

import com.example.authentication.data.AccountRepository
import com.example.authentication.data.OtpAccount
import com.example.authentication.data.OtpDao
import com.example.authentication.engine.TotpEngine
import com.example.authentication.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeOtpDao : OtpDao {
    private val _accountsFlow = MutableStateFlow<List<OtpAccount>>(
        listOf(
            OtpAccount(
                id = 1,
                issuer = "Google",
                accountName = "test@gmail.com",
                secretKey = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            )
        )
    )

    override fun getAllAccountsStream(): Flow<List<OtpAccount>> = _accountsFlow.asStateFlow()

    override suspend fun insert(account: OtpAccount) {
        val current = _accountsFlow.value.toMutableList()
        current.add(account)
        _accountsFlow.value = current
    }

    override suspend fun delete(account: OtpAccount) {
        val current = _accountsFlow.value.toMutableList()
        current.removeAll { it.id == account.id }
        _accountsFlow.value = current
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAuthViewModelInitialState() = runTest(testDispatcher) {
        val repo = AccountRepository(FakeOtpDao())
        val vm = AuthViewModel(repo)

        // Advance dispatcher slightly for initialization
        advanceTimeBy(100)

        val timer = vm.timerState.value
        assertNotNull(timer)
        assertTrue("Seconds remaining should be between 1 and 30", timer.secondsRemaining in 1..30)
        assertTrue("Progress should be between 0.0 and 1.0", timer.progress in 0f..1f)
    }

    @Test
    fun testExpiryDetection() {
        // At 26s into window (4s remaining) -> isExpiringSoon is true
        val timeAt26s = 26_000L
        val seconds = TotpEngine.secondsRemaining(timeAt26s)
        assertEquals(4, seconds)
        assertTrue(seconds <= 5)

        // At 10s into window (20s remaining) -> isExpiringSoon is false
        val timeAt10s = 10_000L
        val secondsNormal = TotpEngine.secondsRemaining(timeAt10s)
        assertEquals(20, secondsNormal)
        assertFalse(secondsNormal <= 5)
    }

    @Test
    fun testTotpEngineSafeWithInvalidSecret() {
        // Empty or pure non-base32 secrets should safely return fallback
        val codeEmpty = TotpEngine.generateCode("", 1000L)
        assertEquals("------", codeEmpty)

        val codeInvalid = TotpEngine.generateCode("!@#$%^&*()", 1000L)
        assertEquals("------", codeInvalid)

        // Secret with spaces should be sanitized
        val codeWithSpaces = TotpEngine.generateCode("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ", 59_000L)
        assertEquals("287082", codeWithSpaces)
    }

    @Test
    fun testBoundaryCodeAccuracyAgainstRfc() {
        val key = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        // 59s is right before :00 boundary (60s)
        val codeAt59 = TotpEngine.generateCode(key, 59_000L)
        assertEquals("287082", codeAt59)

        // 60s is exactly at :00 boundary -> code must change
        val codeAt60 = TotpEngine.generateCode(key, 60_000L)
        assertTrue("Code at 60s must differ from code at 59s", codeAt59 != codeAt60)

        // 89s is right before :30 boundary (90s)
        val codeAt89 = TotpEngine.generateCode(key, 89_000L)
        assertEquals("Code must remain stable throughout the 30s window", codeAt60, codeAt89)

        // 90s is exactly at :30 boundary
        val codeAt90 = TotpEngine.generateCode(key, 90_000L)
        assertTrue("Code at 90s must differ from code at 89s", codeAt89 != codeAt90)
    }
}
