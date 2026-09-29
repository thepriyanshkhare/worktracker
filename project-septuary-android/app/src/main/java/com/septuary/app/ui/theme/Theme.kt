package com.septuary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0F1115)
val Panel = Color(0xFF171A21)
val Panel2 = Color(0xFF1E222B)
val Border = Color(0xFF2A2F3A)
val TextMain = Color(0xFFE8EAED)
val TextMuted = Color(0xFF9AA2B1)
val Accent = Color(0xFF4ADE80)
val Danger = Color(0xFFF16565)

private val SeptuaryColors = darkColorScheme(
    background = Bg,
    surface = Panel,
    primary = Accent,
    onPrimary = Color(0xFF0B1710),
    onBackground = TextMain,
    onSurface = TextMain,
    error = Danger
)

@Composable
fun SeptuaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeptuaryColors, content = content)
}
