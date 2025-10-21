package com.example.whisper_kotlin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1),
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        BasicText("Settings", style = TextStyle(color = textColor))
        Spacer(modifier = Modifier.height(16.dp))

        val labels = remember {
            mapOf(
                ThemePreference.System to "Use system theme",
                ThemePreference.Light to "Light",
                ThemePreference.Dark to "Dark"
            )
        }
        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "Appearance",
                style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                val menuBackground = remember(textColor) {
                    if (textColor.luminance() > 0.5f) Color(0xFF1E1E1E) else Color.White
                }
                Row(
                    modifier = Modifier
                        .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = labels[themePreference] ?: themePreference.name,
                        style = TextStyle(color = textColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Theme options",
                        tint = textColor
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(menuBackground)
                ) {
                    ThemePreference.values().forEach { option ->
                        DropdownMenuItem(
                            onClick = {
                                expanded = false
                                onThemePreferenceChange(option)
                            },
                            modifier = Modifier.background(menuBackground)
                        ) {
                            BasicText(
                                text = labels[option] ?: option.name,
                                style = TextStyle(color = textColor)
                            )
                        }
                    }
                }
            }
        }
    }
}