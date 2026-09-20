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
        AccentPalette.VIOLET to Palette(Color(0xFF6654C7), Color(0xFFB9567F), Color(0xFFC9772D)),
        AccentPalette.OCEAN to Palette(Color(0xFF08798A), Color(0xFF476FC4), Color(0xFF168874)),
        AccentPalette.CORAL to Palette(Color(0xFFB64B60), Color(0xFFD96548), Color(0xFF785AA8)),
        AccentPalette.FOREST to Palette(Color(0xFF36764F), Color(0xFF716B32), Color(0xFF08786D)),
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
            onSecondary = Color(0xFF1C1015),
            tertiary = palette.tertiary.lighten(0.16f),
            background = Color(0xFF111015),
            surface = Color(0xFF19181D),
            surfaceVariant = Color(0xFF29272F),
            onBackground = Color(0xFFF2EFF5),
            onSurface = Color(0xFFF2EFF5),
            onSurfaceVariant = Color(0xFFC9C3CE),
            outline = Color(0xFF918A97),
            outlineVariant = Color(0xFF454149),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primary.lighten(0.84f),
            onPrimaryContainer = palette.primary.darken(0.45f),
            secondary = palette.secondary.darken(0.12f),
            tertiary = palette.tertiary.darken(0.18f),
            background = Color(0xFFF8F7FB),
            surface = Color(0xFFFFFBFF),
            surfaceVariant = Color(0xFFEDEAF1),
            onBackground = Color(0xFF1C1A20),
            onSurface = Color(0xFF1C1A20),
            onSurfaceVariant = Color(0xFF625D68),
            outline = Color(0xFF7D7783),
            outlineVariant = Color(0xFFD9D3DE),
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
