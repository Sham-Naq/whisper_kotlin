package com.example.whisper_kotlin.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomTab(
    val route: String,
    val header: String,
    val topBar: String,
    private val filledIcon: ImageVector,
    private val outlinedIcon: ImageVector
) {
    Recents(
        route = "recents",
        header = "Recents",
        topBar = "Recent recordings",
        filledIcon = Icons.Filled.History,
        outlinedIcon = Icons.Outlined.History
    ),
    Transcription(
        route = "transcription",
        header = "Manage",
        topBar = "Manage recordings",
        filledIcon = Icons.Filled.Folder,
        outlinedIcon = Icons.Outlined.Folder
    ),
    Recorder(
        route = "recorder",
        header = "Recorder",
        topBar = "New Recording",
        filledIcon = Icons.Filled.Mic,
        outlinedIcon = Icons.Outlined.MicNone
    ),
    Chats(
        route = "chats",
        header = "Chats",
        topBar = "Chats",
        filledIcon = Icons.Filled.Chat,
        outlinedIcon = Icons.Outlined.Chat
    ),
    Settings(
        route = "settings",
        header = "Account",
        topBar = "Account",
        filledIcon = Icons.Filled.Settings,
        outlinedIcon = Icons.Outlined.Settings
    );

    fun icon(isSelected: Boolean): ImageVector = outlinedIcon
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
