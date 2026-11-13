package com.onixgarage.phone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun OnixGarageTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = PrimaryAccent,
        secondary = SecondaryAccent,
        background = BackgroundDark,
        surface = SurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onBackground = OnSurfaceDark,
        onSurface = OnSurfaceDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = StatusRed,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
