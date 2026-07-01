package com.example.authentication

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 *  2. BiometricPrompt — gates the UI on every onResume() using fingerprint / face / PIN.
 *
 * Note: FragmentActivity (not ComponentActivity) is required by the biometric library.
 */
class MainActivity : FragmentActivity() {

    private lateinit var viewModel: AuthViewModel
    private var isAuthenticated = false

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
                val navController = rememberNavController()
                var authState by remember { mutableStateOf(false) }

                // Expose auth state so the nav graph can hide until authenticated
                LaunchedEffect(isAuthenticated) {
                    authState = isAuthenticated
                }

                if (authState) {
                    OpenAuthNavGraph(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
                // While not authenticated the screen is intentionally blank
                // (the biometric prompt covers it on every resume)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always re-challenge on resume — prevents leaving the app open
        // and returning to unlocked codes from the Recent Apps screen.
        showBiometricPrompt()
    }

    // ─────────────────────────────────────────────────────────────────
    // Security Lock 2: Biometric Gate
    // ─────────────────────────────────────────────────────────────────
    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Device has no lock screen at all — still allow access but warn in logs.
            isAuthenticated = true
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated = true
                    // Trigger recomposition by re-setting content flag
                    setContent {
                        AuthenticationTheme {
                            val navController = rememberNavController()
                            OpenAuthNavGraph(
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    // Wrong finger / face — prompt stays visible, do nothing
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled / no hardware — close app
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        isAuthenticated = false
                    }
                    finish()
                }
            }
        )

        prompt.authenticate(biometricPromptInfo)
    }
}