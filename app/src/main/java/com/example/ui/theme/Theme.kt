package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
  darkColorScheme(
    primary = Scarlet,
    onPrimary = Mist,
    primaryContainer = ScarletSoft,
    onPrimaryContainer = Mist,
    secondary = Muted,
    background = Ink,
    onBackground = Mist,
    surface = Graphite,
    onSurface = Mist,
    surfaceContainer = ElevatedGraphite,
    onSurfaceVariant = Muted,
    outline = Muted
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
