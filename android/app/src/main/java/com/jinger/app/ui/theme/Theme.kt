package com.jinger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Flame = Color(0xFFFF4458)
val Mint = Color(0xFF2ECC71)
val Sun = Color(0xFFFFC107)
val Ink = Color(0xFF1F1F2E)

private val JingerColors = lightColorScheme(
    primary = Flame,
    onPrimary = Color.White,
    secondary = Mint,
    tertiary = Sun,
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    onSurface = Ink,
    surfaceTint = Color.White,
)

@Composable
fun JingerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JingerColors, content = content)
}
