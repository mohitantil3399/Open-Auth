package com.example.authentication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.authentication.data.AccountRepository
import com.example.authentication.data.OtpAccount
import com.example.authentication.engine.TotpEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AccountUiState(
    val account: OtpAccount,
    val currentCode: String
)

class AuthViewModel(private val repository: AccountRepository) : ViewModel() {

    // ──────────────────────────────────────────────────────
    // Master Clock — MutableStateFlow guarantees every
    // subscriber always has the latest value immediately,
    // with no cold-start race or stale-replay issues.
    // ──────────────────────────────────────────────────────
    private val _tick = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (isActive) {
                _tick.value = System.currentTimeMillis()
                delay(100L)
            }
        }
    }

    // ──────────────────────────────────────────────────────
    // Progress: 1.0f (start of window) → 0.0f (end of window)
    //
    // Uses millisecond precision for smooth sub-second updates
    // instead of truncating to whole seconds.
    // ──────────────────────────────────────────────────────
    val remainingProgress = _tick
        .map { millis ->
            val millisIntoWindow = millis % 30_000L
            1f - (millisIntoWindow / 30_000f)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = run {
                val millis = System.currentTimeMillis()
                val millisIntoWindow = millis % 30_000L
                1f - (millisIntoWindow / 30_000f)
            }
        )

    // ──────────────────────────────────────────────────────
    // Live accounts: DB stream × tick → fresh codes
    // ──────────────────────────────────────────────────────
    val liveAccounts = combine(
        repository.getAllAccountsStream(),
        _tick
    ) { accounts, currentTime ->
        accounts.map { acc ->
            AccountUiState(
                account = acc,
                currentCode = TotpEngine.generateCode(acc.secretKey, currentTime)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    // ──────────────────────────────────────────────────────
    // Actions
    // ──────────────────────────────────────────────────────
    fun addAccount(account: OtpAccount) {
        viewModelScope.launch { repository.insert(account) }
    }

    fun deleteAccount(account: OtpAccount) {
        viewModelScope.launch { repository.delete(account) }
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
