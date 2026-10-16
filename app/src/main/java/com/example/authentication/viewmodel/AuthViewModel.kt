package com.example.authentication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.authentication.data.AccountRepository
import com.example.authentication.data.OtpAccount
import com.example.authentication.engine.TotpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AccountUiState(
    val account: OtpAccount,
    val currentCode: String
)

data class TimerUiState(
    val progress: Float = 1.0f,
    val secondsRemaining: Int = 30,
    val isExpiringSoon: Boolean = false
)

class AuthViewModel(private val repository: AccountRepository) : ViewModel() {

    // ──────────────────────────────────────────────────────
    // Wall clock tick flow:
    // Emits timestamp continuously (every 50ms) to update progress
    // and seconds remaining without drift.
    // ──────────────────────────────────────────────────────
    private val _clockMillis = MutableStateFlow(System.currentTimeMillis())
    val clockMillis: StateFlow<Long> = _clockMillis.asStateFlow()

    init {
        startClock()
    }

    private fun startClock() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _clockMillis.value = System.currentTimeMillis()
                delay(50L)
            }
        }
    }

    // ──────────────────────────────────────────────────────
    // Timer state:
    // - progress: 1.0f (start of window) -> 0.0f (end of window)
    // - secondsRemaining: 30 down to 1
    // - isExpiringSoon: true when secondsRemaining <= 5
    // ──────────────────────────────────────────────────────
    val timerState: StateFlow<TimerUiState> = _clockMillis
        .map { millis ->
            val progress = TotpEngine.progressRemaining(millis)
            val seconds = TotpEngine.secondsRemaining(millis)
            TimerUiState(
                progress = progress,
                secondsRemaining = seconds,
                isExpiringSoon = seconds <= 5
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = run {
                val now = System.currentTimeMillis()
                val progress = TotpEngine.progressRemaining(now)
                val seconds = TotpEngine.secondsRemaining(now)
                TimerUiState(
                    progress = progress,
                    secondsRemaining = seconds,
                    isExpiringSoon = seconds <= 5
                )
            }
        )

    // ──────────────────────────────────────────────────────
    // Remaining progress flow (1.0f -> 0.0f)
    // ──────────────────────────────────────────────────────
    val remainingProgress: StateFlow<Float> = timerState
        .map { it.progress }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TotpEngine.progressRemaining(System.currentTimeMillis())
        )

    // ──────────────────────────────────────────────────────
    // Live accounts:
    // Recomputed ONLY when database accounts change OR
    // when the 30-second window step changes (at :00 and :30 boundaries).
    // ──────────────────────────────────────────────────────
    val liveAccounts: StateFlow<List<AccountUiState>> = combine(
        repository.getAllAccountsStream(),
        _clockMillis.map { it / 1000L / 30L }.distinctUntilChanged()
    ) { accounts, _ ->
        val currentTime = System.currentTimeMillis()
        accounts.map { acc ->
            AccountUiState(
                account = acc,
                currentCode = TotpEngine.generateCode(acc.secretKey, currentTime)
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    // ──────────────────────────────────────────────────────
    // Actions
    // ──────────────────────────────────────────────────────
    fun addAccount(account: OtpAccount) {
        viewModelScope.launch(Dispatchers.IO) { repository.insert(account) }
    }

    fun deleteAccount(account: OtpAccount) {
        viewModelScope.launch(Dispatchers.IO) { repository.delete(account) }
    }
}

// ──────────────────────────────────────────────────────
// Manual factory — no Hilt, keeps the project simple
// ──────────────────────────────────────────────────────
class AuthViewModelFactory(private val repository: AccountRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
