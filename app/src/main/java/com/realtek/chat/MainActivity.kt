package com.realtek.chat

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.realtek.chat.storage.AppDb
import com.realtek.chat.storage.PinManager
import com.realtek.chat.ui.*
import com.realtek.chat.worker.ProactiveScheduler

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppDb.get(this).writableDatabase
        ProactiveScheduler.schedule(this)

        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        setContent {
            RealtekTheme {
                val pin = remember { PinManager(this) }
                var configured by remember {
                    mutableStateOf(pin.isConfigured())
                }
                // 不保存解锁状态：任务/进程重启后重新验证。
                var unlocked by remember { mutableStateOf(false) }

                when {
                    !configured -> PinSetupScreen {
                        pin.setPin(it)
                        configured = true
                        unlocked = true
                    }
                    !unlocked -> PinUnlockScreen(
                        verify = pin::verify,
                        onSuccess = { unlocked = true }
                    )
                    else -> RealtekApp()
                }
            }
        }
    }
}
