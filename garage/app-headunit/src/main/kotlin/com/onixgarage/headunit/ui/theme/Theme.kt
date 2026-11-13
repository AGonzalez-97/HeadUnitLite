package com.onixgarage.headunit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun OnixGarageHuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PrimaryAccent,
            background = BackgroundDark,
            surface = SurfaceVariantDark,
            surfaceVariant = SurfaceVariantDark,
            onBackground = androidx.compose.ui.graphics.Color(0xFFE6E8EB),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE6E8EB),
            onSurfaceVariant = OnSurfaceVariantDark,
            error = StatusRed,
        ),
        content = content,
    )
}
