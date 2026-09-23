package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = CinemaPrimary,
    onPrimary = Color.White,
    primaryContainer = CinemaPrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = CinemaSecondary,
    onSecondary = Color.Black,
    secondaryContainer = CinemaSurfaceVariant,
    onSecondaryContainer = CinemaTextPrimary,
    tertiary = CinemaAccent,
    background = CinemaDarkBackground,
    onBackground = CinemaTextPrimary,
    surface = CinemaSurface,
    onSurface = CinemaTextPrimary,
    surfaceVariant = CinemaSurfaceVariant,
    onSurfaceVariant = CinemaTextSecondary,
    outline = CinemaBorder
  )

private val LightColorScheme = DarkColorScheme // Always use cinematic dark on TV for optimal contrast

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
