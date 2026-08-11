package com.example.octopusreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TransitCardColors = lightColorScheme(
    primary = Color(0xFF047D95),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0F1FA),
    onPrimaryContainer = Color(0xFF00363F),
    secondary = Color(0xFF3E6370),
    background = Color(0xFFF5FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2F0F4),
    onSurface = Color(0xFF102A33),
    onSurfaceVariant = Color(0xFF405B64),
    error = Color(0xFFBA1A1A),
)

@Composable
fun MultiTransitCardReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TransitCardColors,
        content = content,
    )
}
