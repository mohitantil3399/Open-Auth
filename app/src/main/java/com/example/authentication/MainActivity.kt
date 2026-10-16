package com.example.authentication

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.example.authentication.data.AccountRepository
import com.example.authentication.data.OtpDatabase
import com.example.authentication.ui.navigation.OpenAuthNavGraph
import com.example.authentication.ui.theme.AuthenticationTheme
import com.example.authentication.viewmodel.AuthViewModel
import com.example.authentication.viewmodel.AuthViewModelFactory

/**
 * Single-activity app entry point.
 *
 * Security hardening applied here:
 *  1. FLAG_SECURE  — blocks screenshots and blacks out the Recents thumbnail.
 *  2. BiometricPrompt — gates the UI on launch using fingerprint / face / PIN.
 *
 * Note: FragmentActivity (not ComponentActivity) is required by the biometric library.
 */
class MainActivity : FragmentActivity() {

    private lateinit var viewModel: AuthViewModel
    private val isAuthenticated = mutableStateOf(false)

    // ─────────────────────────────────────────────────────────────────
    // Biometric prompt setup
    // ─────────────────────────────────────────────────────────────────
    private val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("OpenAuth")
        .setSubtitle("Verify your identity to access your codes")
        .setDescription("Use fingerprint, face, or device PIN")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Security Lock 1: Screenshot & Recents thumbnail block ────
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // ── Wire up ViewModel manually (no Hilt) ────────────────────
        val db = OtpDatabase.getInstance(applicationContext)
        val repository = AccountRepository(db.otpDao())
        viewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(repository)
        )[AuthViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            AuthenticationTheme {
                val authed by isAuthenticated
                if (authed) {
                    val navController = rememberNavController()
                    OpenAuthNavGraph(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If not yet authenticated, challenge with biometrics
        if (!isAuthenticated.value) {
            showBiometricPrompt()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Security Lock 2: Biometric Gate
    // ─────────────────────────────────────────────────────────────────
    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Device has no lock screen / biometrics — allow access
            isAuthenticated.value = true
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated.value = true
                }

                override fun onAuthenticationFailed() {
                    // Wrong finger / face — prompt stays visible, do nothing
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled / no hardware — close app
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        isAuthenticated.value = false
                    }
                    finish()
                }
            }
        )

        prompt.authenticate(biometricPromptInfo)
    }
}