package com.iosync.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonYellow,
    onPrimary = OnPrimary,
    primaryContainer = NeonYellowDim,
    onPrimaryContainer = Background,
    secondary = NeonYellowVariant,
    onSecondary = OnPrimary,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = OnBackground,
    tertiary = ColorBoolean,
    onTertiary = OnPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = SurfaceDark,
    onSurface = OnBackground,
    surfaceVariant = SurfaceMid,
    onSurfaceVariant = OnBackgroundSecondary,
    surfaceTint = NeonYellow,
    outline = SurfaceHighest,
    outlineVariant = SurfaceElevated,
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = StatusError,
    inverseSurface = OnBackground,
    inverseOnSurface = Background,
    inversePrimary = PrimaryLight,
    scrim = Color(0xCC000000)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = NeonYellow,
    onPrimaryContainer = OnPrimary,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    error = StatusError,
    onError = Color.White
)

@Composable
fun IoSyncTheme(
    darkTheme: Boolean = true, // Default is always dark
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IoSyncTypography,
        content = content
    )
}
