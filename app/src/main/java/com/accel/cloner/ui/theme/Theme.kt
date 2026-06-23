package com.accel.cloner.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Colors ────────────────────────────────────────────────────────────────────
val VioletPrimary    = Color(0xFF7B2FFF)
val VioletLight      = Color(0xFF9D5CFF)
val VioletContainer  = Color(0xFF3A1070)
val SurfaceDark      = Color(0xFF0D0D14)
val SurfaceCard      = Color(0xFF14141F)
val SurfaceElevated  = Color(0xFF1C1C2A)
val OnSurfaceLight   = Color(0xFFE8E0FF)
val OnSurfaceMuted   = Color(0xFF9E96B0)
val GreenActive      = Color(0xFF4ADE80)
val OrangeWarning    = Color(0xFFFFB347)
val RedError         = Color(0xFFFF5C5C)

private val DarkColorScheme = darkColorScheme(
    primary            = VioletPrimary,
    onPrimary          = Color.White,
    primaryContainer   = VioletContainer,
    onPrimaryContainer = OnSurfaceLight,
    secondary          = VioletLight,
    onSecondary        = Color.White,
    background         = SurfaceDark,
    onBackground       = OnSurfaceLight,
    surface            = SurfaceCard,
    onSurface          = OnSurfaceLight,
    surfaceVariant     = SurfaceElevated,
    onSurfaceVariant   = OnSurfaceMuted,
    error              = RedError,
    outline            = Color(0xFF3D3550)
)

@Composable
fun AccelClonerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(),
        content     = content
    )
}
