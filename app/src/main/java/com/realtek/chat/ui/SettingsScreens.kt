package com.realtek.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.realtek.chat.settings.AppSettings
import com.realtek.chat.storage.*
import com.realtek.chat.worker.ProactiveScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    openAi: () -> Unit,
    openPassword: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    var sleepMode by remember { mutableStateOf(appSettings.sleepMode) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar("设置", onBack = onBack)

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("休眠模式")
                Spacer(Modifier.height(4.dp))
                Text(
                    "开启后只响应你主动发来的消息，不再主动联系",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = sleepMode,
                onCheckedChange = { enabled ->
                    sleepMode = enabled
                    appSettings.sleepMode = enabled

                    if (enabled) {
                        WorkManager.getInstance(context)
                            .cancelAllWorkByTag("realtek_proactive")
                    } else {
                        ProactiveScheduler.schedule(context)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WeChatGreen
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        WhiteActionRow("打开密码", onClick = openPassword)

        Spacer(Modifier.height(10.dp))
        WhiteActionRow("AI 服务", onClick = openAi)

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text("隐私说明", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "聊天记录、长期记忆、头像、聊天图片和背景都保存在 App 私有目录，并使用本机密钥加密。卸载 Realtek 后，Android 会删除这些 App 私有数据。",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember {
        AppSettings(context)
    }

    var haijingKey by remember {
        mutableStateOf(
            settings.haijingApiKey
        )
    }

    var haijingModel by remember {
        mutableStateOf(
            settings.haijingDefaultModel
        )
    }

    var deepSeekKey by remember {
        mutableStateOf(
            settings.deepSeekApiKey
        )
    }

    var deepSeekModel by remember {
        mutableStateOf(
            settings.deepSeekDefaultModel
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar(
            "AI 服务",
            onBack = onBack,
            right = {
                TopTextAction(
                    text = "保存",
                    onClick = {
                        settings.haijingApiKey =
                            haijingKey
                        settings.haijingDefaultModel =
                            haijingModel

                        settings.deepSeekApiKey =
                            deepSeekKey
                        settings.deepSeekDefaultModel =
                            deepSeekModel

                        onBack()
                    }
                )
            }
        )

        Column(
            Modifier.verticalScroll(
                rememberScrollState()
            )
        ) {
            Spacer(
                Modifier.height(10.dp)
            )

            SectionLabel(
                "海鲸AI 路线"
            )

            WhiteField(
                "API Key",
                haijingKey,
                { haijingKey = it },
                secret = true
            )

            WhiteField(
                "默认模型",
                haijingModel,
                { haijingModel = it },
                placeholder =
                    "例如 grok-4.6；也可填海鲸支持的其他模型"
            )

            Text(
                "接口固定走海鲸AI。联系人可单独修改模型 ID，不会写死 Grok 4.6。",
                color = Color.Gray,
                style =
                    MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
            )

            Spacer(
                Modifier.height(10.dp)
            )

            SectionLabel(
                "DeepSeek 官方路线"
            )

            WhiteField(
                "API Key",
                deepSeekKey,
                { deepSeekKey = it },
                secret = true
            )

            WhiteField(
                "默认模型",
                deepSeekModel,
                { deepSeekModel = it }
            )

            Text(
                "Planner、记忆整理、主动聊天默认优先使用 DeepSeek；没配置 DeepSeek 时自动用海鲸。",
                color = Color.Gray,
                style =
                    MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
            )

            Spacer(
                Modifier.height(20.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String
) {
    Text(
        text,
        color = Color.Gray,
        style =
            MaterialTheme.typography.labelMedium,
        modifier =
            Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
    )
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pin = remember { PinManager(context) }

    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar("打开密码", onBack = onBack)
        Spacer(Modifier.height(10.dp))

        WhiteField(
            "当前密码",
            old,
            { old = it.filter(Char::isDigit).take(4) },
            secret = true,
            keyboardType = KeyboardType.NumberPassword
        )
        WhiteField(
            "新密码",
            new,
            { new = it.filter(Char::isDigit).take(4) },
            secret = true,
            keyboardType = KeyboardType.NumberPassword
        )
        WhiteField(
            "确认新密码",
            confirm,
            { confirm = it.filter(Char::isDigit).take(4) },
            secret = true,
            keyboardType = KeyboardType.NumberPassword
        )

        Button(
            onClick = {
                error = when {
                    new.length != 4 -> "新密码必须是 4 位数字"
                    new != confirm -> "两次新密码不一致"
                    !pin.changePin(old, new) -> "当前密码错误"
                    else -> {
                        onBack()
                        ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WeChatGreen
            )
        ) {
            Text("修改密码")
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFD93025),
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStore(context) }
    val media = remember { SecureMediaStore(context) }
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf(profile.nickname) }
    var avatar by remember { mutableStateOf(profile.avatarPath) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val newPath = withContext(Dispatchers.IO) {
                media.importImage(uri, "my_avatar", 600, 90)
            }

            withContext(Dispatchers.IO) {
                media.delete(profile.avatarPath)
                profile.avatarPath = newPath
            }

            avatar = newPath
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar(
            "个人信息",
            onBack = onBack,
            right = {
                TopTextAction(
                    text = "保存",
                    onClick = {
                        profile.nickname = nickname
                        onBack()
                    }
                )
            }
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable { launcher.launch("image/*") }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("头像", modifier = Modifier.weight(1f))
            Avatar(avatar, nickname.take(1), 54)
        }

        WhiteField(
            "名字",
            nickname,
            { nickname = it }
        )
    }
}
