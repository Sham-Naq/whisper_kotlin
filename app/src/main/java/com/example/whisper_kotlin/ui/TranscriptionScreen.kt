package com.example.whisper_kotlin

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.TextButton
import androidx.compose.material.ripple
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme



@Composable
fun ActionButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = color.copy(alpha = 0.3f)),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = color, fontWeight = FontWeight.Medium))
    }
}
@Composable
fun TranscriptionScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    viewModel: TranscriptionViewModel = viewModel(),
    onOpenTranscription: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<SavedTranscription?>(null) }
    val isDark = isSystemInDarkTheme()
    val cardBackground = MaterialTheme.colorScheme.surfaceContainer
    val cardBorderColor = MaterialTheme.colorScheme.outlineVariant
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    Column(modifier = modifier) {
        val dialogBackground = MaterialTheme.colorScheme.surface
        val progress = uiState.progress
        val statusMessage = uiState.statusMessage

        // Enable Android back button to navigate up the folder hierarchy when inside a folder
        BackHandler(enabled = uiState.currentFolderId != null) {
            val parentId = uiState.folders.firstOrNull { it.id == uiState.currentFolderId }?.parentId
            viewModel.navigateToFolder(parentId)
        }

        if (uiState.isTranscribing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                progress?.let {
                    LinearProgressIndicator(progress = it, modifier = Modifier.fillMaxWidth())
                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                BasicText(
                    text = statusMessage ?: "Transcribing…",
                    style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (statusMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = statusMessage,
                style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val showBack = uiState.currentFolderId != null
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(label = "Add new folder", color = textColor) {
                newFolderName = ""
                showAddFolderDialog = true
            }
            if (showBack) {
                ActionButton(label = "Up one level", color = textColor) {
                    val parentId = uiState.folders.firstOrNull { it.id == uiState.currentFolderId }?.parentId
                    viewModel.navigateToFolder(parentId)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val folders = viewModel.listFoldersInCurrent()
        val saved = viewModel.listTranscriptionsInCurrent()
        Spacer(modifier = Modifier.height(8.dp))
        if (folders.isEmpty() && saved.isEmpty()) {
            BasicText(
                text = "No saved transcriptions yet. Record or select audio from the Recorder tab to create one.",
                style = TextStyle(color = textColor.copy(alpha = 0.8f))
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Folders first (use distinct key namespace to avoid collisions with transcription IDs)
                items(folders, key = { folder -> "folder_" + folder.id }) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = textColor.copy(alpha = 0.2f))
                            ) {
                                viewModel.navigateToFolder(folder.id)
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = 0.dp,
                        backgroundColor = cardBackground,
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = true)) {
                                BasicText(
                                    text = folder.name,
                                    style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = "Folder",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                )
                            }
                            // Simple chevron indicator (using ">>")
                            BasicText(
                                text = ">",
                                style = TextStyle(color = textColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Then transcriptions (separate key namespace)
                items(saved, key = { entry -> "t_" + entry.id }) { entry ->
                    val timestamp = remember(entry.timestamp) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                    }
                    val durationLabel = remember(entry.transcriptionDurationMs) {
                        if (entry.transcriptionDurationMs >= 1000L) {
                            String.format(Locale.getDefault(), "%.1f s", entry.transcriptionDurationMs / 1000f)
                        } else {
                            "${entry.transcriptionDurationMs} ms"
                        }
                    }
                    val summary = remember(entry.transcript) {
                        entry.transcript
                            .lineSequence()
                            .firstOrNull()
                            ?.take(160)
                            ?.let { if (entry.transcript.length > 160) "$it…" else it }
                            ?: "Tap to view transcript"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = textColor.copy(alpha = 0.2f))
                            ) { onOpenTranscription(entry.id) },
                        shape = RoundedCornerShape(12.dp),
                        elevation = 0.dp,
                        backgroundColor = cardBackground,
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = true)) {
                                    BasicText(
                                        text = entry.fileLabel,
                                        style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    BasicText(
                                        text = "Model: ${entry.modelLabel}",
                                        style = TextStyle(color = textColor.copy(alpha = 0.7f))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    BasicText(
                                        text = "$timestamp",
                                        style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    BasicText(
                                        text = "Transcription time: $durationLabel",
                                        style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        pendingDelete = entry
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete transcription",
                                        tint = textColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            BasicText(
                                text = summary,
                                style = TextStyle(color = textColor.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
            }
        }

        val entryToDelete = pendingDelete
        if (entryToDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = {
                    Text(
                        text = "Delete transcription?",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "This will permanently remove ${entryToDelete.fileLabel}.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTranscription(context, entryToDelete.id)
                        pendingDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddFolderDialog) {
            AlertDialog(
                onDismissRequest = { showAddFolderDialog = false },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = { Text(text = "New folder", color = textColor, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            text = "Enter a name for this folder.",
                            color = textColor.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) {
                                viewModel.createFolder(name)
                            }
                            showAddFolderDialog = false
                        }
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}