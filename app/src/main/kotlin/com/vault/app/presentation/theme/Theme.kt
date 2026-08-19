package com.vault.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultBlue = Color(0xFF3B5BDB)
private val VaultBlueDark = Color(0xFF8CA8FF)

private val LightColors = lightColorScheme(
    primary = VaultBlue,
    secondary = Color(0xFF1B1F3B),
)

private val DarkColors = darkColorScheme(
    primary = VaultBlueDark,
    secondary = Color(0xFFB7C2FF),
)

@Composable
fun VaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
