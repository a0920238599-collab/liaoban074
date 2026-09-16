package com.realtek.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinSetupScreen(onComplete: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var stage by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    LockShell(
        title = if (stage == 0) "设置打开密码" else "再次输入密码",
        subtitle = if (stage == 0)
            "首次使用需要设置 4 位数字密码"
        else
            "再次输入刚才的密码"
    ) {
        PinDots(if (stage == 0) first else confirm)

        NumericPad(
            onDigit = { d ->
                if (stage == 0 && first.length < 4) {
                    first += d
                    if (first.length == 4) stage = 1
                } else if (stage == 1 && confirm.length < 4) {
                    confirm += d
                    if (confirm.length == 4) {
                        if (confirm == first) {
                            onComplete(confirm)
                        } else {
                            error = "两次密码不一致"
                            first = ""
                            confirm = ""
                            stage = 0
                        }
                    }
                }
            },
            onBackspace = {
                if (stage == 0) first = first.dropLast(1)
                else confirm = confirm.dropLast(1)
            }
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(error, color = Color(0xFFD93025))
        }
    }
}

@Composable
fun PinUnlockScreen(
    verify: (String) -> Boolean,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LockShell(
        title = "Realtek",
        subtitle = "请输入打开密码"
    ) {
        PinDots(pin)

        NumericPad(
            onDigit = { d ->
                if (pin.length < 4) {
                    pin += d
                    if (pin.length == 4) {
                        if (verify(pin)) onSuccess()
                        else {
                            error = "密码错误"
                            pin = ""
                        }
                    }
                }
            },
            onBackspace = {
                pin = pin.dropLast(1)
                error = ""
            }
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(error, color = Color(0xFFD93025))
        }
    }
}

@Composable
private fun LockShell(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(84.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WeChatGreen,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color.Gray)
        Spacer(Modifier.height(34.dp))

        content()
    }
}
