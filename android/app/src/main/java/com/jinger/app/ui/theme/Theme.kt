package com.jinger.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

val Violet = Color(0xFF7B2FF7)

// Soft, pill-like corners everywhere (text fields, cards, sheets, buttons).
private val JingerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun JingerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JingerColors, shapes = JingerShapes, content = content)
}
