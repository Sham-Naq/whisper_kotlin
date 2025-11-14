package com.example.whisper_kotlin.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomTab(val route: String, val header: String) {
    Recents(route = "recents", header = "Recents"),
    Transcription(route = "transcription", header = "Transcription"),
    Recorder(route = "recorder", header = "Recorder"),
    Chats(route = "chats", header = "Chats"),
    Settings(route = "settings", header = "Settings");

    fun getIcon(): ImageVector = when (this) {
        Recents -> Icons.Filled.AccessTime
        Transcription -> Icons.Filled.Folder
        Recorder -> Icons.Filled.Mic
        Chats -> Icons.Filled.Chat
        Settings -> Icons.Filled.Settings
    }
}

enum class ThemePreference {
    System,
    Light,
    Dark
}

val bottomTabOrder: List<BottomTab> = listOf(
    BottomTab.Recents,
    BottomTab.Transcription,
    BottomTab.Recorder,
    BottomTab.Chats,
    BottomTab.Settings
)
