package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.AlertDialog
import androidx.compose.material.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.Locale

data class TimeGroup(
    val title: String,
    val entries: List<SavedTranscription>
)

private fun getTimeCategory(timestamp: Long, now: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now

    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val weekStart = calendar.apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis

    val monthStart = calendar.apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -30)
    }.timeInMillis

    return when {
        timestamp >= todayStart -> "Today"
        timestamp >= weekStart -> "This Week"
        timestamp >= monthStart -> "This Month"
        else -> "A Long Time Ago"
    }
}

@Composable
fun RecentsScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    viewModel: TranscriptionViewModel,
    onOpenTranscription: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dialogBackground = MaterialTheme.colorScheme.surface
    
    var showRecordingMenu by remember { mutableStateOf<SavedTranscription?>(null) }
    var showEditNameDialog by remember { mutableStateOf<SavedTranscription?>(null) }
    var editedName by remember { mutableStateOf("") }
    var showMoveToFolderDialog by remember { mutableStateOf<SavedTranscription?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedTranscription?>(null) }
    
    val now = System.currentTimeMillis()

    val groupedTranscriptions = remember(uiState.savedTranscriptions) {
        uiState.savedTranscriptions
            .sortedByDescending { it.timestamp }
            .take(20) // Increased from 10 to 20
            .groupBy { getTimeCategory(it.timestamp, now) }
            .map { (title, entries) -> TimeGroup(title, entries) }
            .sortedBy { group ->
                when (group.title) {
                    "Today" -> 0
                    "This Week" -> 1
                    "This Month" -> 2
                    "A Long Time Ago" -> 3
                    else -> 4
                }
            }
    }

    if (groupedTranscriptions.isEmpty() || groupedTranscriptions.all { it.entries.isEmpty() }) {
        Column {
            Text(
                text = "No recent transcriptions.",
                color = textColor.copy(alpha = 0.8f),
                style = TextStyle(fontSize = 16.sp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        groupedTranscriptions.forEachIndexed { groupIndex, group ->
            if (group.entries.isNotEmpty()) {
                // Section header
                item(key = "header_${group.title}") {
                    RecentsSectionHeader(
                        text = group.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Entries in this group
                group.entries.forEachIndexed { entryIndex, entry ->
                    item(key = "entry_${entry.id}") {
                        TranscriptionItem(
                            entry = entry,
                            textColor = textColor,
                            showMenu = showRecordingMenu == entry,
                            dialogBackground = dialogBackground,
                            onClick = { onOpenTranscription(entry.id) },
                            onLongClick = { showRecordingMenu = entry },
                            onDismissMenu = { showRecordingMenu = null },
                            onMenuOpen = { onOpenTranscription(entry.id) },
                            onMenuCopyTranscript = {
                                clipboardManager.setText(AnnotatedString(entry.transcript))
                                Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                            },
                            onMenuEditName = {
                                editedName = entry.fileLabel
                                showEditNameDialog = entry
                            },
                            onMenuMoveToFolder = {
                                showMoveToFolderDialog = entry
                            },
                            onMenuDelete = {
                                pendingDelete = entry
                            }
                        )

                        // Add divider except for the last item in the last group
                        val isLastGroup = groupIndex == groupedTranscriptions.lastIndex
                        val isLastItem = entryIndex == group.entries.lastIndex
                        if (!(isLastGroup && isLastItem)) {
                            Divider(
                                modifier = Modifier.fillMaxWidth(),
                                color = textColor.copy(alpha = 0.1f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        // Add bottom padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Edit name dialog
    val entryToEdit = showEditNameDialog
    if (entryToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = null },
            backgroundColor = dialogBackground,
            contentColor = textColor,
            title = { Text(text = "Rename", color = textColor, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        text = "Enter a new name for this recording.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        placeholder = { Text("New name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editedName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.renameTranscription(entryToEdit.id, name)
                        }
                        showEditNameDialog = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Move to folder dialog
    val entryToMove = showMoveToFolderDialog
    if (entryToMove != null) {
        val availableFolders = uiState.folders.sortedBy { it.name.lowercase() }
        AlertDialog(
            onDismissRequest = { showMoveToFolderDialog = null },
            backgroundColor = dialogBackground,
            contentColor = textColor,
            title = { Text(text = "Move to folder", color = textColor, fontWeight = FontWeight.SemiBold) },
            text = {
                if (availableFolders.isEmpty()) {
                    Text(
                        text = "No folders available. Create a folder first.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = "Select a folder:",
                                color = textColor.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(availableFolders.size) { index ->
                            val folder = availableFolders[index]
                            TextButton(
                                onClick = {
                                    viewModel.moveToFolder(entryToMove.id, folder.id)
                                    showMoveToFolderDialog = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(folder.name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToFolderDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Delete dialog
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptionItem(
    entry: SavedTranscription,
    textColor: Color,
    showMenu: Boolean,
    dialogBackground: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuCopyTranscript: () -> Unit,
    onMenuEditName: () -> Unit,
    onMenuMoveToFolder: () -> Unit,
    onMenuDelete: () -> Unit
) {
    val firstLine = remember(entry.transcript) {
        entry.transcript
            .lines()
            .firstOrNull()
            ?.trim()
            ?.take(80) // Limit to reasonable length
            ?.let { if (entry.transcript.length > 80) "$it..." else it }
            ?: "Tap to view transcript"
    }

    val durationText = remember(entry.audioDurationSec, entry.transcriptionDurationMs) {
        val durationSec = when {
            entry.audioDurationSec > 0 -> entry.audioDurationSec
            entry.transcriptionDurationMs > 0 -> ((entry.transcriptionDurationMs + 500) / 1000).toInt()
            else -> 0
        }
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = textColor.copy(alpha = 0.1f)),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Waveform icon
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color(0xFFd664e5)
        )

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            androidx.compose.material.Text(
                text = entry.fileLabel,
                color = textColor,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))

            val isTranscriptReady = entry.status == TranscriptionStatus.Completed && entry.transcript.isNotBlank()
            val dateText = remember(entry.timestamp) {
                java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                    .format(java.util.Date(entry.timestamp))
                    .lowercase(Locale.ENGLISH)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateText,
                    color = textColor.copy(alpha = 0.6f),
                    style = TextStyle(fontSize = 12.sp)
                )

                if (entry.languageCode != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = textColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = entry.languageCode.uppercase(),
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                if (isTranscriptReady) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = "Transcript ready",
                        tint = Color(0xFF0088fe),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Duration
        androidx.compose.material.Text(
            text = durationText,
            color = textColor.copy(alpha = 0.6f),
            style = TextStyle(fontSize = 12.sp)
        )
        
        // Dropdown menu anchored to this item
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
            offset = DpOffset(x = 150.dp, y = 0.dp),
            modifier = Modifier.background(color = dialogBackground)
        ) {
            DropdownMenuItem(
                onClick = {
                    onDismissMenu()
                    onMenuOpen()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Open", color = textColor)
                }
            }
            DropdownMenuItem(
                onClick = {
                    onMenuCopyTranscript()
                    onDismissMenu()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Copy transcript", color = textColor)
                }
            }
            DropdownMenuItem(
                onClick = {
                    onMenuEditName()
                    onDismissMenu()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Edit name", color = textColor)
                }
            }
            DropdownMenuItem(
                onClick = {
                    onMenuMoveToFolder()
                    onDismissMenu()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Move to folder", color = textColor)
                }
            }
            DropdownMenuItem(
                onClick = {
                    onMenuDelete()
                    onDismissMenu()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RecentsSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = colorScheme.onSurfaceVariant,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}
