package com.realtek.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.realtek.chat.ai.ChatEngine
import com.realtek.chat.settings.AppSettings
import com.realtek.chat.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddContactScreen(
    onBack: () -> Unit,
    onCreated: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val engine = remember { ChatEngine(context) }
    val settings = remember { AppSettings(context) }
    val media = remember { SecureMediaStore(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var provider by remember {
        mutableStateOf("haijing")
    }
    var model by remember {
        mutableStateOf(
            settings.defaultModelFor(provider)
        )
    }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                avatarPath?.let(media::delete)
                avatarPath = withContext(Dispatchers.IO) {
                    media.importImage(uri, "avatar", 600, 90)
                }
            }.onFailure {
                error = it.message ?: "头像读取失败"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar(
            "添加朋友",
            onBack = onBack,
            right = {
                TopTextAction(
                    text = "完成",
                    enabled = name.isNotBlank() && persona.isNotBlank(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val id = db.addContact(
                                name = name,
                                subtitle = subtitle.ifBlank {
                                    description.take(20)
                                },
                                provider = provider,
                                model = model.ifBlank {
                                    settings.defaultModelFor(
                                        provider
                                    )
                                },
                                persona = persona,
                                style = style,
                                avatarPath = avatarPath
                            )

                            withContext(Dispatchers.Main) {
                                onCreated(id)
                            }
                        }
                    }
                )
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.clickable {
                        avatarLauncher.launch("image/*")
                    }
                ) {
                    Avatar(
                        avatarPath,
                        name.take(1).ifBlank { "+" },
                        76
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "设置头像",
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(10.dp))

            WhiteField(
                "名字",
                name,
                { name = it }
            )

            WhiteField(
                "简单描述",
                description,
                { description = it },
                minLines = 3,
                placeholder = "例如：话不多，熟了以后很会开玩笑，偶尔毒舌"
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    generating = true
                    error = ""

                    scope.launch {
                        runCatching {
                            engine.generatePersona(
                                name.ifBlank { "这个联系人" },
                                description
                            )
                        }.onSuccess {
                            subtitle = it.subtitle
                            persona = it.persona
                            style = it.style
                        }.onFailure {
                            error = it.message ?: "生成失败"
                        }
                        generating = false
                    }
                },
                enabled = description.isNotBlank() && !generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WeChatGreen
                ),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(
                    if (generating) "正在生成…"
                    else "AI 帮我完善人物设定"
                )
            }

            if (persona.isNotBlank()) {
                Text(
                    "人物设定已生成",
                    color = WeChatGreen,
                    modifier = Modifier.padding(18.dp)
                )
            }

            TextButton(
                onClick = { advanced = !advanced },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    if (advanced) "收起更多设置" else "更多设置",
                    color = Color.Gray
                )
            }

            if (advanced) {
                ProviderSelector(
                    provider = provider,
                    onProviderChange = { selected ->
                        provider = selected
                        model = settings.defaultModelFor(
                            selected
                        )
                    }
                )
                WhiteField("模型 ID", model, { model = it }, placeholder = "海鲸可填 grok-4.6、其他可用模型；不写死")
                WhiteField("人物简介", subtitle, { subtitle = it })
                WhiteField(
                    "核心性格",
                    persona,
                    { persona = it },
                    minLines = 5
                )
                WhiteField(
                    "说话方式",
                    style,
                    { style = it },
                    minLines = 5
                )
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = Color(0xFFD93025),
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}

@Composable
fun ContactInfoScreen(
    contactId: Int,
    onBack: () -> Unit,
    openChat: () -> Unit,
    openAdvanced: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val media = remember { SecureMediaStore(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        contact = withContext(Dispatchers.IO) {
            db.getContact(contactId)
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val old = contact ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val newPath = withContext(Dispatchers.IO) {
                media.importImage(uri, "avatar", 600, 90)
            }
            val updated = old.copy(avatarPath = newPath)

            withContext(Dispatchers.IO) {
                db.updateContact(updated)
                media.delete(old.avatarPath)
            }
            contact = updated
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val old = contact ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val newPath = withContext(Dispatchers.IO) {
                media.importImage(uri, "background", 1800, 88)
            }
            val updated = old.copy(backgroundPath = newPath)

            withContext(Dispatchers.IO) {
                db.updateContact(updated)
                media.delete(old.backgroundPath)
            }
            contact = updated
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar(
            "",
            onBack = onBack,
            right = {
                Box {
                    TopIconButton(
                        onClick = { menu = true },
                        contentDescription = "更多",
                        icon = Icons.Outlined.MoreHoriz
                    )

                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("更多设置") },
                            onClick = {
                                menu = false
                                openAdvanced()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "删除联系人",
                                    color = Color(0xFFD93025)
                                )
                            },
                            onClick = {
                                menu = false
                                scope.launch(Dispatchers.IO) {
                                    val paths = db.deleteContact(contactId)
                                    paths.forEach(media::delete)

                                    withContext(Dispatchers.Main) {
                                        onDeleted()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.clickable {
                    avatarLauncher.launch("image/*")
                }
            ) {
                Avatar(
                    contact?.avatarPath,
                    contact?.name?.take(1) ?: "?",
                    70
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    contact?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    contact?.subtitle.orEmpty(),
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        WhiteActionRow("设置当前聊天背景") {
            backgroundLauncher.launch("image/*")
        }

        WhiteActionRow("恢复默认聊天背景") {
            val old = contact ?: return@WhiteActionRow
            scope.launch(Dispatchers.IO) {
                media.delete(old.backgroundPath)
                val updated = old.copy(backgroundPath = null)
                db.updateContact(updated)

                withContext(Dispatchers.Main) {
                    contact = updated
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = openChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WeChatGreen
            ),
            shape = RoundedCornerShape(5.dp)
        ) {
            Text("发消息")
        }
    }
}

@Composable
fun AdvancedContactScreen(
    contactId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val media = remember { SecureMediaStore(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var name by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("haijing") }
    var model by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var proactive by remember { mutableStateOf(true) }

    LaunchedEffect(contactId) {
        val c = withContext(Dispatchers.IO) {
            db.getContact(contactId)
        } ?: return@LaunchedEffect

        contact = c
        name = c.name
        subtitle = c.subtitle
        provider = c.provider
        model = c.model
        persona = c.corePersona
        style = c.styleRules
        proactive = c.proactiveEnabled
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar(
            "更多设置",
            onBack = onBack,
            right = {
                TopTextAction(
                    text = "保存",
                    onClick = {
                        val old = contact ?: return@TopTextAction

                        scope.launch(Dispatchers.IO) {
                            db.updateContact(
                                old.copy(
                                    name = name,
                                    subtitle = subtitle,
                                    provider = provider,
                                    model = model,
                                    corePersona = persona,
                                    styleRules = style,
                                    proactiveEnabled = proactive
                                )
                            )
                            withContext(Dispatchers.Main) {
                                onBack()
                            }
                        }
                    }
                )
            }
        )

        Column(
            Modifier.verticalScroll(rememberScrollState())
        ) {
            WhiteField("名字", name, { name = it })
            WhiteField("简介", subtitle, { subtitle = it })
            ProviderSelector(
                provider = provider,
                onProviderChange = { selected ->
                    provider = selected
                    model = settings.defaultModelFor(
                        selected
                    )
                }
            )
            WhiteField("模型 ID", model, { model = it }, placeholder = "海鲸可填 grok-4.6、其他可用模型；不写死")
            WhiteField(
                "核心性格",
                persona,
                { persona = it },
                minLines = 6
            )
            WhiteField(
                "说话方式",
                style,
                { style = it },
                minLines = 6
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "允许主动发消息",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = proactive,
                    onCheckedChange = { proactive = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = WeChatGreen
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            WhiteActionRow(
                "清空聊天记录",
                danger = true
            ) {
                scope.launch(Dispatchers.IO) {
                    db.clearMessages(contactId).forEach(media::delete)
                }
            }

            WhiteActionRow(
                "清空长期记忆",
                danger = true
            ) {
                scope.launch(Dispatchers.IO) {
                    db.clearMemories(contactId)
                }
            }
        }
    }
}


@Composable
private fun ProviderSelector(
    provider: String,
    onProviderChange: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
    ) {
        Text(
            "AI 路线",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(
            Modifier.height(10.dp)
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected =
                    provider == "haijing",
                onClick = {
                    onProviderChange(
                        "haijing"
                    )
                },
                label = {
                    Text("海鲸AI")
                }
            )

            FilterChip(
                selected =
                    provider == "deepseek",
                onClick = {
                    onProviderChange(
                        "deepseek"
                    )
                },
                label = {
                    Text("DeepSeek 官方")
                }
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = DividerGray
    )
}
