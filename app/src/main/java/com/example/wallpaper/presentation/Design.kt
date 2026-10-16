package com.example.wallpaper.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.wallpaper.ui.theme.WalperTypography

object WalperTokens {
    val Lavender = Color(0xFF8978FF)
    val Purple = Color(0xFF654CFF)
    val Ink = Color(0xFF292E3D)
    val Night = Color(0xFF111827)
    val Pink = Color(0xFFFF7D99)
    val Page = 24.dp
    val Gap = 12.dp
    val Card = RoundedCornerShape(18.dp)
    val Bar = RoundedCornerShape(28.dp)
    val pastel = listOf(Color(0xFFD1F5FF), Color(0xFFDDD5FF), Color(0xFFF5D2FF), Color(0xFFFFD6D6), Color(0xFFD5FFE8))
}

@Composable
fun WalperTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(
        primary = WalperTokens.Lavender, background = WalperTokens.Night,
        surface = WalperTokens.Night, surfaceVariant = Color(0xFF292F3C), onBackground = Color(0xFFF4F4F8),
        onSurface = Color(0xFFF4F4F8), secondary = WalperTokens.Pink
    )
    else lightColorScheme(
        primary = WalperTokens.Lavender,
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF4F4F7),
        onBackground = WalperTokens.Ink,
        onSurface = WalperTokens.Ink,
        secondary = WalperTokens.Pink
    )
    MaterialTheme(
        colorScheme = colors,
        typography = WalperTypography,
        content = content
    )
}
