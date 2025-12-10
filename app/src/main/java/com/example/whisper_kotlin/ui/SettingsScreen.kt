package com.example.whisper_kotlin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.navigation.ThemePreference
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import com.example.whisper_kotlin.ui.components.DropdownMenuItem

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1),
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    selectedModel: ModelOption? = null,
    transcriptionViewModel: TranscriptionViewModel? = null,
    onSelectModel: ((ModelOption) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        val labels = remember {
            mapOf(
                ThemePreference.System to "Use system theme",
                ThemePreference.Light to "Light",
                ThemePreference.Dark to "Dark"
            )
        }

        // Theme preference dropdown
        ReusableDropdown(
            label = "Appearance",
            selectedText = labels[themePreference] ?: themePreference.name,
            textColor = textColor,
            isDark = themePreference == ThemePreference.Dark,
            modifier = Modifier.fillMaxWidth(),
            dropdownContent = { onDismiss ->
                ThemePreference.values().forEach { option ->
                    DropdownMenuItem(
                        text = labels[option] ?: option.name,
                        textColor = textColor,
                        onClick = {
                            onThemePreferenceChange(option)
                            onDismiss()
                        }
                    )
                }
            }
        )

        // Delete Model section
        if (selectedModel != null && transcriptionViewModel != null && onSelectModel != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Model Management",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
                
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteModelDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Delete Current Model (${selectedModel.id})",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Delete model dialog
    if (showDeleteModelDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = textColor,
            title = {
                Text("Delete model?", color = textColor, fontWeight = FontWeight.SemiBold)
            },
            text = {
                val modelName = selectedModel?.id ?: "this model"
                Text(
                    "This will remove $modelName from local storage. You'll need to download it again to use it later.",
                    color = textColor.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteModelDialog = false
                    val fallbackSpec = ModelManager.defaultModel()
                    transcriptionViewModel?.deleteModel(context, selectedModel)
                    if (selectedModel != null && fallbackSpec != null && onSelectModel != null) {
                        val fallbackOption = ModelOption(fallbackSpec.id, fallbackSpec.fileName, fallbackSpec.url)
                        onSelectModel(fallbackOption)
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}