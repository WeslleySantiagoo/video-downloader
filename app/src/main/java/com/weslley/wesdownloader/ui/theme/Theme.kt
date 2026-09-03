package com.weslley.wesdownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lime = Color(0xFFB5DB32)
val Forest = Color(0xFF76A88F)
val Night = Color(0xFF0B120E)
val Surface = Color(0xFF142019)
val SurfaceHigh = Color(0xFF1B2A21)
val Cream = Color(0xFFF4F0E8)
val Muted = Color(0xFFADB9B1)

private val WesColors = darkColorScheme(
    primary = Lime,
    onPrimary = Night,
    secondary = Forest,
    onSecondary = Night,
    background = Night,
    onBackground = Cream,
    surface = Surface,
    onSurface = Cream,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun WesDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WesColors, content = content)
}
