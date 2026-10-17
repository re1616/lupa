package ru.vibecoding.lupa.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Акцент панели камеры: жёлтый на чёрном — максимальный контраст. */
val Accent = Color(0xFFFFD600)
val PanelBackground = Color(0xE6000000)
val ButtonBackground = Color(0x33FFFFFF)

@Composable
fun LupaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
