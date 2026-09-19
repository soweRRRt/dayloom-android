package com.sowerrrt.dayloom.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ThemeMode

private data class Palette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private val palettes =
    mapOf(
        AccentPalette.VIOLET to Palette(Color(0xFF7357D3), Color(0xFFE56D9F), Color(0xFFFFA94D)),
        AccentPalette.OCEAN to Palette(Color(0xFF007C91), Color(0xFF4776E6), Color(0xFF00A896)),
        AccentPalette.CORAL to Palette(Color(0xFFC4475D), Color(0xFFFF7A59), Color(0xFF8963BA)),
        AccentPalette.FOREST to Palette(Color(0xFF357A4F), Color(0xFF7A6E35), Color(0xFF00796B)),
    )

@Composable
fun DayloomTheme(
    themeMode: ThemeMode,
    accentPalette: AccentPalette,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val colors = dayloomColorScheme(accentPalette, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = DayloomTypography,
        shapes = DayloomShapes,
        content = content,
    )
}

private fun dayloomColorScheme(
    accent: AccentPalette,
    dark: Boolean,
): ColorScheme {
    val palette = palettes.getValue(accent)
    return if (dark) {
        darkColorScheme(
            primary = palette.primary.lighten(0.28f),
            onPrimary = Color(0xFF17121F),
            primaryContainer = palette.primary.darken(0.32f),
            onPrimaryContainer = Color(0xFFF0E9FF),
            secondary = palette.secondary.lighten(0.22f),
            tertiary = palette.tertiary.lighten(0.16f),
            background = Color(0xFF121116),
            surface = Color(0xFF19181E),
            surfaceVariant = Color(0xFF25232B),
            onBackground = Color(0xFFF1EEF5),
            onSurface = Color(0xFFF1EEF5),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primary.lighten(0.78f),
            onPrimaryContainer = palette.primary.darken(0.45f),
            secondary = palette.secondary.darken(0.12f),
            tertiary = palette.tertiary.darken(0.18f),
            background = Color(0xFFFFFBFF),
            surface = Color(0xFFFFFBFF),
            surfaceVariant = Color(0xFFF1EDF5),
            onBackground = Color(0xFF1D1B20),
            onSurface = Color(0xFF1D1B20),
        )
    }
}

private fun Color.lighten(amount: Float): Color =
    Color(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
        alpha = alpha,
    )

private fun Color.darken(amount: Float): Color =
    Color(red = red * (1f - amount), green = green * (1f - amount), blue = blue * (1f - amount), alpha = alpha)
