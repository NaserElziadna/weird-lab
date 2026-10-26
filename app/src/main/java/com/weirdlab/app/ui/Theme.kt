package com.weirdlab.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Neon {
    val Bg = Color(0xFF07071A)
    val Surface = Color(0xFF12122B)
    val SurfaceHi = Color(0xFF1E1E44)
    val Text = Color(0xFFF2F2FF)
    val TextDim = Color(0xFF9A9AC8)
    val Ink = Color(0xFF0A0A1A)

    val Cyan = Color(0xFF22E6FF)
    val Pink = Color(0xFFFF3DAE)
    val Purple = Color(0xFF8B5CFF)
    val Violet = Color(0xFFB57BFF)
    val Lime = Color(0xFFB6FF3B)
    val Mint = Color(0xFF2BFFA8)
    val Orange = Color(0xFFFF8A3D)
    val Yellow = Color(0xFFFFE14D)
    val Red = Color(0xFFFF4D6D)
}

@Composable
fun WeirdLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Cyan,
            onPrimary = Neon.Ink,
            secondary = Neon.Pink,
            background = Neon.Bg,
            onBackground = Neon.Text,
            surface = Neon.Surface,
            onSurface = Neon.Text,
            surfaceVariant = Neon.SurfaceHi,
            onSurfaceVariant = Neon.TextDim
        ),
        content = content
    )
}
