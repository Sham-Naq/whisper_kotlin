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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.Checkbox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TranscriptionScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    viewModel: TranscriptionViewModel = viewModel(),
    isDetailVisible: Boolean = false,
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedTranscriptionIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val dialogBackground = MaterialTheme.colorScheme.surface
            val progress = uiState.progress
            val statusMessage = uiState.statusMessage

            // Back: if a detail overlay is visible, let the parent handle it; otherwise
            // exit selection first; else navigate up a folder when inside one
            BackHandler(enabled = (selectionMode || uiState.currentFolderId != null) && !isDetailVisible) {
                if (selectionMode) {
                    selectionMode = false
                    selectedFolderIds = emptySet()
                    selectedTranscriptionIds = emptySet()
                } else {
                    val parentId = uiState.folders.firstOrNull { it.id == uiState.currentFolderId }?.parentId
                    viewModel.navigateToFolder(parentId)
                }
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

            // Selection mode controls (only show cancel and delete when in selection mode)
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(label = "Cancel", color = textColor) {
                        selectionMode = false
                        selectedFolderIds = emptySet()
                        selectedTranscriptionIds = emptySet()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (selectedFolderIds.isNotEmpty() || selectedTranscriptionIds.isNotEmpty()) {
                            showDeleteSelectionDialog = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete selected",
                            tint = textColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val folders = viewModel.listFoldersInCurrent()
            val saved = viewModel.listTranscriptionsInCurrent()
            
            if (folders.isEmpty() && saved.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                BasicText(
                    text = "No saved transcriptions yet. Record or select audio from the Recorder tab to create one.",
                    style = TextStyle(color = textColor.copy(alpha = 0.8f))
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                ) {
                // Folders first (use distinct key namespace to avoid collisions with transcription IDs)
                items(folders, key = { folder -> "folder_" + folder.id }) { folder ->
                    val folderIsSelected = selectionMode && selectedFolderIds.contains(folder.id)
                    val folderBg = if (folderIsSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else cardBackground
                    val folderBorder = if (folderIsSelected) MaterialTheme.colorScheme.primary else cardBorderColor
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = textColor.copy(alpha = 0.2f)),
                                onClick = {
                                    if (selectionMode) {
                                        selectedFolderIds = if (selectedFolderIds.contains(folder.id))
                                            selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                    } else {
                                        viewModel.navigateToFolder(folder.id)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedFolderIds = setOf(folder.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = 0.dp,
                        backgroundColor = folderBg,
                        border = BorderStroke(1.dp, folderBorder)
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
                            if (selectionMode) {
                                Checkbox(
                                    checked = selectedFolderIds.contains(folder.id),
                                    onCheckedChange = { checked ->
                                        selectedFolderIds = if (checked) selectedFolderIds + folder.id else selectedFolderIds - folder.id
                                    }
                                )
                            } else {
                                BasicText(
                                    text = ">",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                )
                            }
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
                    val entryIsSelected = selectionMode && selectedTranscriptionIds.contains(entry.id)
                    val entryBg = if (entryIsSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else cardBackground
                    val entryBorder = if (entryIsSelected) MaterialTheme.colorScheme.primary else cardBorderColor
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = textColor.copy(alpha = 0.2f)),
                                onClick = {
                                    if (selectionMode) {
                                        selectedTranscriptionIds = if (selectedTranscriptionIds.contains(entry.id))
                                            selectedTranscriptionIds - entry.id else selectedTranscriptionIds + entry.id
                                    } else {
                                        onOpenTranscription(entry.id)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedTranscriptionIds = setOf(entry.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = 0.dp,
                        backgroundColor = entryBg,
                        border = BorderStroke(1.dp, entryBorder)
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
                                if (selectionMode) {
                                    Checkbox(
                                        checked = selectedTranscriptionIds.contains(entry.id),
                                        onCheckedChange = { checked ->
                                            selectedTranscriptionIds = if (checked) selectedTranscriptionIds + entry.id else selectedTranscriptionIds - entry.id
                                        }
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

        // Legacy per-item delete dialog retained if something still triggers it
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

        // Selection delete dialog
        if (showDeleteSelectionDialog) {
            val foldersCount = selectedFolderIds.size
            val transCount = selectedTranscriptionIds.size
            AlertDialog(
                onDismissRequest = { showDeleteSelectionDialog = false },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = { Text(text = "Delete selected?", color = textColor, fontWeight = FontWeight.SemiBold) },
                text = {
                    Text(
                        text = "This will delete $foldersCount folder(s) and $transCount transcription(s).",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        // Delete folders first (will also remove transcriptions within)
                        selectedFolderIds.forEach { fid -> viewModel.deleteFolder(fid) }
                        // Then delete any remaining specifically-selected transcriptions
                        selectedTranscriptionIds.forEach { tid -> viewModel.deleteTranscription(context, tid) }
                        selectionMode = false
                        selectedFolderIds = emptySet()
                        selectedTranscriptionIds = emptySet()
                        showDeleteSelectionDialog = false
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteSelectionDialog = false }) { Text("Cancel") } }
            )
        }
    }
    
    // Floating Action Button for adding folders (hide in selection mode)
    if (!selectionMode) {
        FloatingActionButton(
            onClick = {
                newFolderName = ""
                showAddFolderDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add new folder",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
  }
}