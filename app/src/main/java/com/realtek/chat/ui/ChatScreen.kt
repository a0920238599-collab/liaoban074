package com.realtek.chat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realtek.chat.ai.ChatEngine
import com.realtek.chat.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    contactId: Int,
    onBack: () -> Unit,
    openInfo: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val engine = remember { ChatEngine(context) }
    val media = remember { SecureMediaStore(context) }
    val profile = remember { ProfileStore(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var contact by remember {
        mutableStateOf<Contact?>(null)
    }

    var messages by remember {
        mutableStateOf(emptyList<ChatMessage>())
    }

    var input by remember {
        mutableStateOf("")
    }

    var speaking by remember {
        mutableStateOf(false)
    }

    var streamingText by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    var activeJob by remember {
        mutableStateOf<Job?>(null)
    }

    var sessionId by remember {
        mutableLongStateOf(0L)
    }

    fun reload() {
        contact = db.getContact(contactId)
        messages = db.getMessages(contactId)
    }

    LaunchedEffect(contactId) {
        val c = withContext(Dispatchers.IO) {
            db.getContact(contactId)
        }

        val m = withContext(Dispatchers.IO) {
            db.getMessages(contactId)
        }

        contact = c
        messages = m
    }

    LaunchedEffect(
        messages.size,
        streamingText
    ) {
        val extra = if (
            streamingText.isNotBlank() ||
            speaking
        ) 1 else 0

        val count = messages.size + extra

        if (count > 0) {
            runCatching {
                listState.animateScrollToItem(
                    count - 1
                )
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        activeJob?.cancel()
        sessionId += 1L
        val mySession = sessionId

        activeJob = scope.launch {
            speaking = true
            streamingText = ""
            error = ""

            runCatching {
                val path = withContext(
                    Dispatchers.IO
                ) {
                    media.importImage(
                        uri,
                        "chat",
                        1600,
                        88
                    )
                }

                engine.sendImage(
                    contactId = contactId,
                    mediaPath = path,
                    onChanged = {
                        if (mySession == sessionId) {
                            reload()
                        }
                    }
                )
            }.onFailure {
                if (mySession == sessionId) {
                    error = it.message ?: "图片发送失败"
                }
            }

            if (mySession == sessionId) {
                speaking = false
                streamingText = ""
                reload()

                launch(Dispatchers.IO) {
                    runCatching {
                        engine.processMemory(contactId)
                    }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        contact?.backgroundPath?.let {
            PrivateImage(
                path = it,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.72f),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            Modifier.fillMaxSize()
        ) {
            TopBar(
                title = contact?.name.orEmpty(),
                onBack = onBack,
                right = {
                    TopIconButton(
                        onClick = openInfo,
                        contentDescription = "更多",
                        icon = Icons.Outlined.MoreHoriz
                    )
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                items(
                    messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(
                        message = message,
                        contact = contact,
                        myAvatarPath = profile.avatarPath
                    )
                }

                if (streamingText.isNotBlank()) {
                    item(
                        key = "streaming"
                    ) {
                        StreamingAssistantBubble(
                            text = streamingText,
                            contact = contact
                        )
                    }
                } else if (speaking) {
                    item(
                        key = "typing"
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Avatar(
                                contact?.avatarPath,
                                contact?.name?.take(1)
                                    ?: "?",
                                40
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(
                                    5.dp
                                )
                            ) {
                                Text(
                                    "正在输入…",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(
                                        horizontal = 11.dp,
                                        vertical = 9.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = Color(0xFFD93025),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(
                            horizontal = 12.dp,
                            vertical = 5.dp
                        )
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F7F7))
                    .padding(
                        horizontal = 8.dp,
                        vertical = 7.dp
                    ),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = {}
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        "语音"
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor =
                                Color.White,
                            unfocusedContainerColor =
                                Color.White,
                            focusedBorderColor =
                                Color.Transparent,
                            unfocusedBorderColor =
                                Color.Transparent
                        ),
                    shape = RoundedCornerShape(5.dp)
                )

                if (input.isBlank()) {
                    IconButton(
                        onClick = {
                            imageLauncher.launch(
                                "image/*"
                            )
                        }
                    ) {
                        Icon(
                            Icons.Outlined.AddCircleOutline,
                            "图片"
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val text = input
                            input = ""

                            // 用户可以在 AI 连续说话过程中直接插话。
                            // 新消息会取消当前 UI 会话，旧 ChatEngine
                            // 也会在数据库层检测到插话并停止后续阶段。
                            activeJob?.cancel()

                            sessionId += 1L
                            val mySession =
                                sessionId

                            activeJob = scope.launch {
                                speaking = true
                                streamingText = ""
                                error = ""

                                runCatching {
                                    engine.sendText(
                                        contactId =
                                            contactId,
                                        text = text,
                                        onChanged = {
                                            if (
                                                mySession ==
                                                sessionId
                                            ) {
                                                reload()
                                            }
                                        },
                                        onStreaming = {
                                            partial ->
                                            if (
                                                mySession ==
                                                sessionId
                                            ) {
                                                streamingText =
                                                    partial
                                            }
                                        }
                                    )
                                }.onFailure {
                                    if (
                                        mySession ==
                                        sessionId
                                    ) {
                                        error =
                                            it.message
                                                ?: "发送失败"
                                    }
                                }

                                if (
                                    mySession ==
                                    sessionId
                                ) {
                                    speaking = false
                                    streamingText = ""
                                    reload()

                                    launch(
                                        Dispatchers.IO
                                    ) {
                                        runCatching {
                                            engine.processMemory(
                                                contactId
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        enabled =
                            input.isNotBlank(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    WeChatGreen
                            ),
                        shape =
                            RoundedCornerShape(5.dp),
                        contentPadding =
                            PaddingValues(
                                horizontal = 13.dp,
                                vertical = 10.dp
                            )
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    contact: Contact?
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Avatar(
            contact?.avatarPath,
            contact?.name?.take(1) ?: "?",
            40
        )

        Spacer(
            Modifier.width(8.dp)
        )

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(5.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 9.dp
                ),
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    contact: Contact?,
    myAvatarPath: String?
) {
    val mine = message.role == "user"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (mine)
                Arrangement.End
            else
                Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            Avatar(
                contact?.avatarPath,
                contact?.name?.take(1)
                    ?: "?",
                40
            )

            Spacer(
                Modifier.width(8.dp)
            )
        }

        Box(
            Modifier.widthIn(
                max = 270.dp
            )
        ) {
            if (
                message.type == "image" &&
                message.mediaPath != null
            ) {
                PrivateImage(
                    path = message.mediaPath,
                    modifier = Modifier
                        .widthIn(
                            max = 210.dp
                        )
                        .heightIn(
                            max = 260.dp
                        ),
                    contentScale =
                        ContentScale.Crop
                )
            } else {
                Surface(
                    color =
                        if (mine)
                            UserBubble
                        else
                            Color.White,
                    shape =
                        RoundedCornerShape(5.dp)
                ) {
                    Text(
                        message.text,
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    11.dp,
                                vertical =
                                    9.dp
                            ),
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (mine) {
            Spacer(
                Modifier.width(8.dp)
            )

            Avatar(
                myAvatarPath,
                "我",
                40
            )
        }
    }
}
