package com.realtek.chat.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.realtek.chat.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RealtekApp() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeTabs(
                openChat = { nav.navigate("chat/$it") },
                openAdd = { nav.navigate("add") },
                openContact = { nav.navigate("contact/$it") },
                openSettings = { nav.navigate("settings") },
                openProfile = { nav.navigate("profile") }
            )
        }

        composable("add") {
            AddContactScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate("chat/$id") {
                        popUpTo("home")
                    }
                }
            )
        }

        composable(
            "chat/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: return@composable
            ChatScreen(
                contactId = id,
                onBack = { nav.popBackStack() },
                openInfo = { nav.navigate("contact/$id") }
            )
        }

        composable(
            "contact/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: return@composable
            ContactInfoScreen(
                contactId = id,
                onBack = { nav.popBackStack() },
                openChat = { nav.navigate("chat/$id") },
                openAdvanced = { nav.navigate("advanced/$id") },
                onDeleted = {
                    nav.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable(
            "advanced/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            AdvancedContactScreen(
                contactId = entry.arguments?.getInt("id") ?: 0,
                onBack = { nav.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                openAi = { nav.navigate("ai_settings") },
                openPassword = { nav.navigate("password") }
            )
        }

        composable("ai_settings") {
            AiSettingsScreen(onBack = { nav.popBackStack() })
        }

        composable("password") {
            ChangePasswordScreen(onBack = { nav.popBackStack() })
        }

        composable("profile") {
            ProfileScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun HomeTabs(
    openChat: (Int) -> Unit,
    openAdd: () -> Unit,
    openContact: (Int) -> Unit,
    openSettings: () -> Unit,
    openProfile: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = PageGray,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val entries = listOf(
                    Triple(Icons.Outlined.ChatBubbleOutline, "微信", 0),
                    Triple(Icons.Outlined.PeopleOutline, "通讯录", 1),
                    Triple(Icons.Outlined.Explore, "发现", 2),
                    Triple(Icons.Outlined.PersonOutline, "我", 3)
                )

                entries.forEach { (icon, label, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = WeChatGreen,
                            selectedTextColor = WeChatGreen,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> MessageListScreen(openChat, openAdd)
                1 -> ContactsScreen(openContact, openAdd)
                2 -> DiscoverScreen()
                3 -> MeScreen(openSettings, openProfile)
            }
        }
    }
}

@Composable
private fun MessageListScreen(
    openChat: (Int) -> Unit,
    openAdd: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDb.get(context) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) { db.getContacts() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "微信",
            right = {
                TopIconButton(
                    onClick = openAdd,
                    contentDescription = "添加",
                    icon = Icons.Outlined.Add
                )
            }
        )

        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(contacts, key = { it.id }) { contact ->
                val last = remember(contact.id) { db.getLastMessage(contact.id) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openChat(contact.id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(contact.avatarPath, contact.name.take(1), 48)
                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(contact.name, fontSize = 16.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            when {
                                last == null -> contact.subtitle
                                last.type == "image" -> "[图片]"
                                else -> last.text
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF8A8A8A),
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = DividerGray,
                    modifier = Modifier.padding(start = 74.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactsScreen(
    openContact: (Int) -> Unit,
    openAdd: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDb.get(context) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) {
            db.getContacts().sortedBy { it.name }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "通讯录",
            right = {
                TopIconButton(
                    onClick = openAdd,
                    contentDescription = "添加联系人",
                    icon = Icons.Outlined.PersonAddAlt
                )
            }
        )

        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(contacts, key = { it.id }) { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openContact(contact.id) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(contact.avatarPath, contact.name.take(1), 42)
                    Spacer(Modifier.width(12.dp))
                    Text(contact.name, fontSize = 16.sp)
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = DividerGray,
                    modifier = Modifier.padding(start = 68.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscoverScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        TopBar("发现")
        Spacer(Modifier.height(10.dp))
        WhiteActionRow("朋友圈") {}
        Spacer(Modifier.height(10.dp))
        WhiteActionRow("扫一扫") {}
        WhiteActionRow("搜一搜") {}
    }
}

@Composable
private fun MeScreen(
    openSettings: () -> Unit,
    openProfile: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = remember { ProfileStore(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageGray)
    ) {
        Spacer(Modifier.height(22.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable(onClick = openProfile)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(profile.avatarPath, profile.nickname.take(1), 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.nickname, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("Realtek", color = Color.Gray, fontSize = 13.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Color.Gray)
        }

        Spacer(Modifier.height(10.dp))
        WhiteActionRow("收藏") {}
        WhiteActionRow("设置", onClick = openSettings)
    }
}
