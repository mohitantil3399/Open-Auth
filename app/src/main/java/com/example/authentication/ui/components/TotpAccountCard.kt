package com.example.authentication.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authentication.viewmodel.AccountUiState
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * A premium TOTP card that shows:
 * - Issuer avatar (first letter, colored by hash of issuer name)
 * - Issuer label + account name
 * - 6-digit code formatted as "123 456"
 * - Animated circular countdown progress indicator
 * - Tap to copy to clipboard (with haptic feedback)
 * - Swipe-to-delete button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpAccountCard(
    uiState: AccountUiState,
    progress: Float,
    onDeleteClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Controls whether the delete-confirmation dialog is visible
    var showDeleteDialog by remember { mutableStateOf(false) }

    // < 5 seconds left in the 30-second window → warn user
    val isExpiringSoon = progress < (5f / 30f)

    val codeColor by animateColorAsState(
        targetValue = if (isExpiringSoon)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 400),
        label = "codeColor"
    )

    val progressColor by animateColorAsState(
        targetValue = if (isExpiringSoon)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.secondary,
        animationSpec = tween(durationMillis = 400),
        label = "progressColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "progressAnim"
    )

    // Deterministic color from issuer string hash (stays consistent across recompositions)
    val avatarColor = remember(uiState.account.issuer) {
        val hue = (uiState.account.issuer.hashCode().absoluteValue % 360).toFloat()
        Color.hsl(hue, 0.55f, 0.45f)
    }

    // Format "123456" → "123 456"
    val formattedCode = buildString {
        append(uiState.currentCode.take(3))
        append("  ")
        append(uiState.currentCode.takeLast(3))
    }

    // ── Delete Confirmation Dialog ───────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "WARNING",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Deleting this account only removes the code generator from your device.")
                        }
                        append(" Two-factor authentication (2FA) will remain active on your ")
                        append(uiState.account.issuer)
                        append(" account. If you proceed without disabling 2FA first, ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) {
                            append("you may permanently lose access to your account.")
                        }
                        append("\n\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Before deleting, follow these steps:\n\n")
                        }
                        append("1. Log in to your ")
                        append(uiState.account.issuer)
                        append(" account while you still have access to your 2FA codes.\n\n")
                        append("2. Navigate to your account's Security or Privacy settings.\n\n")
                        append("3. Find the Two-Factor Authentication (2FA) section and disable it, or switch to a different 2FA method (e.g., SMS).\n\n")
                        append("4. Confirm that 2FA has been fully removed or updated.\n\n")
                        append("5. Only then come back here and delete this entry.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClicked()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Delete Anyway", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        onClick = {
            scope.launch { clipboard.setText(AnnotatedString(uiState.currentCode)) }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.account.issuer.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Default,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // ── Labels + Code ─────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.account.issuer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.account.accountName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    ),
                    color = codeColor
                )
            }

            // ── Right column: progress + actions ─────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(40.dp),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.outline,
                        strokeWidth = 4.dp
                    )
                    // Seconds remaining inside the circle
                    val secondsLeft = (progress * 30f).toInt().coerceIn(0, 30)
                    Text(
                        text = "$secondsLeft",
                        style = MaterialTheme.typography.labelSmall,
                        color = progressColor
                    )
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
