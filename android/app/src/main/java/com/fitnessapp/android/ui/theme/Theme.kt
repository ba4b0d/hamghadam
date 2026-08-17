package com.fitnessapp.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SoftBlue = Color(0xFF1A73E8)
val SoftBlueLight = Color(0xFFE8F0FE)
val AccentOrange = Color(0xFFF29900)
val SuccessGreen = Color(0xFF34A853)
val ErrorRed = Color(0xFFD93025)
val TextPrimary = Color(0xFF202124)
val TextSecondary = Color(0xFF5F6368)
val SurfaceWhite = Color(0xFFFFFFFF)
val BackgroundGray = Color(0xFFF6F8FC)

private val LightColors = lightColorScheme(
    primary = SoftBlue,
    onPrimary = Color.White,
    primaryContainer = SoftBlueLight,
    onPrimaryContainer = TextPrimary,
    secondary = AccentOrange,
    onSecondary = Color.White,
    background = BackgroundGray,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SoftBlueLight,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
)

@Composable
fun FitnessAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
