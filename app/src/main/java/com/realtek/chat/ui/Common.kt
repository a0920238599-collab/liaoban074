package com.realtek.chat.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realtek.chat.storage.SecureMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    right: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(PageGray)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    "返回",
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }

        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        right()
    }
}

@Composable
fun TopIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(58.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(29.dp)
        )
    }
}

@Composable
fun TopTextAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 72.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Avatar(
    path: String?,
    fallback: String,
    size: Int
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.13f).dp))
            .background(Color(0xFFE2E2E2)),
        contentAlignment = Alignment.Center
    ) {
        if (!path.isNullOrBlank()) {
            PrivateImage(
                path,
                Modifier.fillMaxSize(),
                ContentScale.Crop
            )
        } else {
            Text(
                fallback.take(1).ifBlank { "R" },
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF555555)
            )
        }
    }
}

@Composable
fun PrivateImage(
    path: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    val store = remember { SecureMediaStore(context) }

    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = path
    ) {
        value = withContext(Dispatchers.IO) {
            val bytes = store.loadBytes(path) ?: return@withContext null
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size
            )?.asImageBitmap()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(Color(0xFFE5E5E5)))
    }
}

@Composable
fun WhiteActionRow(
    title: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = if (danger) Color(0xFFD93025) else Color(0xFF111111),
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint = Color(0xFFB5B5B5)
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = DividerGray,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
fun WhiteField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    minLines: Int = 1,
    placeholder: String = "",
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier
                .width(94.dp)
                .padding(top = 20.dp),
            fontSize = 15.sp
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            minLines = minLines,
            placeholder = {
                if (placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                }
            },
            visualTransformation =
                if (secret) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = DividerGray,
        modifier = Modifier.padding(start = 110.dp)
    )
}

@Composable
fun PinDots(pin: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < pin.length)
                            Color(0xFF222222)
                        else
                            Color(0xFFD2D2D2)
                    )
            )
        }
    }
    Spacer(Modifier.height(36.dp))
}

@Composable
fun NumericPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { label ->
                    if (label.isBlank()) {
                        Spacer(Modifier.size(68.dp))
                    } else {
                        Surface(
                            onClick = {
                                if (label == "⌫") onBackspace()
                                else onDigit(label)
                            },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, DividerGray),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    fontSize = if (label == "⌫") 22.sp else 26.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
