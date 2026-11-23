package com.hive.agents.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFC53D)
val Teal = Color(0xFF4FD1C5)
val Ink = Color(0xFF0E1116)
val Panel = Color(0xFF161B22)
val PanelHigh = Color(0xFF1F2630)
val TextMain = Color(0xFFE6EAF0)
val TextDim = Color(0xFF8B97A8)
val Danger = Color(0xFFFF6B6B)
val Good = Color(0xFF57D9A3)

private val HiveScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1300),
    secondary = Teal,
    onSecondary = Color(0xFF00201D),
    background = Ink,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextDim,
    error = Danger,
    onError = Color(0xFF2A0000)
)

@Composable
fun HiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HiveScheme, content = content)
}
