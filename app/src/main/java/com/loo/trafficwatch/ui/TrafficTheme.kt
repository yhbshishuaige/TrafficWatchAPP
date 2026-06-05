package com.loo.trafficwatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7F79),
    onPrimary = Color.White,
    secondary = Color(0xFF6256A7),
    tertiary = Color(0xFFD46A4C),
    background = Color(0xFFF7F7F2),
    onBackground = Color(0xFF1E2326),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2326),
    surfaceVariant = Color(0xFFE5E7DD),
    onSurfaceVariant = Color(0xFF454A4D),
    outline = Color(0xFFB9BDB1),
)

@Composable
fun TrafficTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
