package com.grabix.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF5B3DF5)
val VioletDeep = Color(0xFF3A22C9)
val Saffron = Color(0xFFFFC83D)
val Ink = Color(0xFF0F0B1F)
val InkSurface = Color(0xFF1A1530)
val InkSurfaceHigh = Color(0xFF241D40)
val Mist = Color(0xFFF6F4FF)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = VioletDeep,
    secondary = Color(0xFFB7860B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDB8),
    onSecondaryContainer = Color(0xFF3D2C00),
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE8FA),
    onSurfaceVariant = Color(0xFF4A4560),
    surfaceContainer = Color(0xFFF0EDFC),
    surfaceContainerHigh = Color(0xFFE9E5F8),
    outline = Color(0xFFCBC5E0),
    error = Color(0xFFD32F45),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D8CFF),
    onPrimary = Ink,
    primaryContainer = VioletDeep,
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Saffron,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3A10),
    onSecondaryContainer = Color(0xFFFFEDB8),
    background = Ink,
    onBackground = Color(0xFFEDEAF7),
    surface = InkSurface,
    onSurface = Color(0xFFEDEAF7),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9B3D1),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    outline = Color(0xFF3B3359),
    error = Color(0xFFFF6B7D),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/** True when the app should draw dark, following the Theme setting. */
@Composable
fun isAppDark(theme: String): Boolean = when (theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

/**
 * Material 3 theme. [theme] is "system", "light" or "dark". With [dynamic] on Android 12+,
 * colours come from the wallpaper (Material You); otherwise the Grabix palette is used.
 * Status / navigation bar icons follow the chosen mode unless [controlBars] is false.
 */
@Composable
fun GrabixTheme(
    theme: String = "system",
    dynamic: Boolean = true,
    controlBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isAppDark(theme)
    val context = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (controlBars && !view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
