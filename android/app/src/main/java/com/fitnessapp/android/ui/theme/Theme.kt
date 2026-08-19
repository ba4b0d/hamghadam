package com.fitnessapp.android.ui.theme

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

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val LightColors = lightColorScheme(
    primary = EmberPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECE5),
    onPrimaryContainer = Color(0xFF4A1200),
    secondary = SunSecondary,
    onSecondary = Color(0xFF2A1C00),
    secondaryContainer = Color(0xFFFFF4D6),
    onSecondaryContainer = Color(0xFF402D00),
    tertiary = EmberLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = EmberLight,
    onPrimary = Color(0xFF501600),
    primaryContainer = Color(0xFF782500),
    onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = SunLight,
    onSecondary = Color(0xFF443000),
    secondaryContainer = Color(0xFF624600),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = EmberPrimary,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRedLight,
    onError = Color(0xFF600004),
)

@Composable
fun FitnessAppTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
