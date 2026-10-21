package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SecurityCyanAccent,
    onPrimary = Color.Black,
    primaryContainer = SecurityNavyMedium,
    onPrimaryContainer = Color.White,
    secondary = SafetyOrange,
    onSecondary = Color.Black,
    secondaryContainer = SafetyOrangeLight.copy(alpha = 0.2f),
    onSecondaryContainer = SafetyOrangeLight,
    error = AlarmRed,
    onError = Color.White,
    background = NeutralDarkSurface,
    onBackground = Color.White,
    surface = NeutralDarkCard,
    onSurface = Color.White,
    surfaceVariant = SecurityNavyMedium,
    onSurfaceVariant = Color(0xFFCFD8DC)
)

private val LightColorScheme = lightColorScheme(
    primary = SecurityNavyMedium,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EBF5),
    onPrimaryContainer = SecurityNavyDark,
    secondary = SafetyOrange,
    onSecondary = Color.White,
    secondaryContainer = SafetyOrangeLight.copy(alpha = 0.2f),
    onSecondaryContainer = Color(0xFFB24100),
    error = AlarmRed,
    onError = Color.White,
    background = NeutralLightSurface,
    onBackground = Color(0xFF1E293B),
    surface = NeutralLightCard,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = NeutralSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Mantener colores de seguridad distintivos
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
