package com.example.whisper_kotlin

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, textColor: Color = Color(0xFF0D47A1)) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        BasicText("Settings", style = TextStyle(color = textColor))
        // Future: app settings
    }
}