package com.example.authentication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// OpenAuth always uses a dark theme — no dynamic colour, no light variant.
private val OpenAuthDarkColorScheme = darkColorScheme(
    primary            = IndigoPrimary,
    onPrimary          = OnIndigoPrimary,
    primaryContainer   = IndigoContainer,
    onPrimaryContainer = OnIndigoPrimary,

    secondary            = TealSecondary,
    onSecondary          = OnTealSecondary,
    secondaryContainer   = TealContainer,
    onSecondaryContainer = OnIndigoPrimary,

    background    = BackgroundDeep,
    onBackground  = OnSurface,

    surface         = SurfaceDark,
    surfaceVariant  = SurfaceVariant,
    onSurface       = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,

    error           = ErrorRed,
    onError         = OnIndigoPrimary,
    errorContainer  = ErrorContainer,
    onErrorContainer = ErrorRed,

    outline = Outline
)

@Composable
fun AuthenticationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenAuthDarkColorScheme,
        typography  = Typography,
        content     = content
    )
}