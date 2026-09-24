package com.shalom.slmsys.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFF97316)
private val Bg = Color(0xFF0F0E0D)
private val Surface = Color(0xFF1A1816)
private val Surface2 = Color(0xFF242220)
private val TextPrimary = Color(0xFFF5F0EB)
private val TextMuted = Color(0xFFA39E98)
private val Green = Color(0xFF22C55E)
private val Danger = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Surface2,
    onSecondary = TextPrimary,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    error = Danger,
    onError = Color.White
)

@Composable
fun SlmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}

object SlmColors {
    val orange = Orange
    val bg = Bg
    val surface = Surface
    val surface2 = Surface2
    val text = TextPrimary
    val muted = TextMuted
    val green = Green
    val danger = Danger
}
