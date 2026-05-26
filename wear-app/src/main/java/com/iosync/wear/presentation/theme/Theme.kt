package com.iosync.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val WearColors = Colors(
    primary = NeonYellow,
    primaryVariant = NeonYellowDim,
    secondary = NeonYellow,
    secondaryVariant = NeonYellowDim,
    background = WearBackground,
    surface = WearSurface,
    error = StatusError,
    onPrimary = WearOnPrimary,
    onSecondary = WearOnPrimary,
    onBackground = WearOnBackground,
    onSurface = WearOnBackground,
    onSurfaceVariant = WearOnBackgroundSecondary,
    onError = WearOnBackground
)

private val WearTypography = Typography(
    display1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp
    ),
    display2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp
    ),
    display3 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 34.sp
    ),
    title1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    title2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    title3 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    body2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    caption1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    caption2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun IoSyncWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearColors,
        typography = WearTypography,
        content = content
    )
}
