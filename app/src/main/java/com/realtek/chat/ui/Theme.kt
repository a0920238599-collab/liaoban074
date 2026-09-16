package com.realtek.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WeChatGreen = Color(0xFF07C160)
val PageGray = Color(0xFFEDEDED)
val DividerGray = Color(0xFFE5E5E5)
val UserBubble = Color(0xFF95EC69)

private val scheme = lightColorScheme(
    primary = WeChatGreen,
    background = PageGray,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111)
)

@Composable
fun RealtekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
