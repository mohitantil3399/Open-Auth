package com.example.authentication.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.authentication.data.OtpAccount
import com.example.authentication.ui.components.QrScannerView
import com.example.authentication.ui.components.parseOtpAuthUri
import com.example.authentication.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // ── Tab state ───────────────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Form field state ────────────────────────────────────
    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var issuerError by remember { mutableStateOf(false) }
    var secretError by remember { mutableStateOf(false) }

    // ── Camera permission ───────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // ── Save helper ─────────────────────────────────────────
    fun trySave() {
        issuerError = issuer.isBlank()
        secretError = secret.isBlank()
        if (!issuerError && !secretError) {
            viewModel.addAccount(
                OtpAccount(
                    issuer = issuer.trim(),
                    accountName = accountName.trim().ifEmpty { issuer.trim() },
                    secretKey = secret.trim().uppercase()
                )
            )
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tabs ─────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    text = { Text("Scan QR Code") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Enter Manually") }
                )
            }

            when (selectedTab) {
                // ── QR Scanner tab ─────────────────────────────
                0 -> {
                    if (hasCameraPermission) {
                        QrScannerView(
                            onQrCodeScanned = { raw ->
                                parseOtpAuthUri(raw)?.let { (parsedIssuer, parsedAccount, parsedSecret) ->
                                    viewModel.addAccount(
                                        OtpAccount(
                                            issuer = parsedIssuer,
                                            accountName = parsedAccount,
                                            secretKey = parsedSecret.uppercase()
                                        )
                                    )
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    "Camera permission is required to scan QR codes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                }

                // ── Manual entry tab ───────────────────────────
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Account Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Issuer
                        OutlinedTextField(
                            value = issuer,
                            onValueChange = { issuer = it; issuerError = false },
                            label = { Text("Issuer (e.g. GitHub)") },
                            singleLine = true,
                            isError = issuerError,
                            supportingText = if (issuerError) {
                                { Text("Issuer cannot be empty") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        // Account name
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { accountName = it },
                            label = { Text("Account (e.g. alex@example.com)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        // Secret
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it; secretError = false },
                            label = { Text("Secret Key (Base32)") },
                            singleLine = true,
                            isError = secretError,
                            supportingText = if (secretError) {
                                { Text("Secret key cannot be empty") }
                            } else null,
                            visualTransformation = if (secretVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { secretVisible = !secretVisible }) {
                                    Icon(
                                        imageVector = if (secretVisible)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,
                                        contentDescription = if (secretVisible)
                                            "Hide secret" else "Show secret"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { trySave() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Add Account",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
