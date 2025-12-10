package com.example.whisper_kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.Checkbox
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import java.util.Locale



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
    val sectionHorizontalPadding = 12.dp
    val folderDepthResolver: (Long?) -> Int = remember(uiState.folders) {
        val folderMap = uiState.folders.associateBy { it.id }
        val cache = mutableMapOf<Long?, Int>().apply { put(null, 0) }
        fun depthFor(id: Long?): Int {
            return cache.getOrPut(id) {
                val folder = folderMap[id]
                if (folder == null) 0 else depthFor(folder.parentId) + 1
            }
        }
        { id -> depthFor(id) }
    }
    
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

            // Animate folder content changes
            AnimatedContent(
                targetState = uiState.currentFolderId,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val initialDepth = folderDepthResolver(initialState)
                    val targetDepth = folderDepthResolver(targetState)
                    when {
                        targetDepth > initialDepth -> {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> fullWidth }
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -fullWidth }
                            )
                        }
                        targetDepth < initialDepth -> {
                            slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> -fullWidth }
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> fullWidth }
                            )
                        }
                        else -> {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                    }
                },
                label = "folderAnimation"
            ) { currentFolderId ->
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Folders section
                        if (folders.isNotEmpty()) {
                            item(key = "folders_header_$currentFolderId") {
                                SectionHeaderLabel(
                                    text = "Folders",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                            
                            folders.forEachIndexed { index, folder ->
                                item(key = "folder_${folder.id}_in_$currentFolderId") {
                                    FolderItem(
                                        folder = folder,
                                        textColor = textColor,
                                        horizontalPadding = sectionHorizontalPadding,
                                        selectionMode = selectionMode,
                                        isSelected = selectedFolderIds.contains(folder.id),
                                        onFolderClick = {
                                            if (selectionMode) {
                                                selectedFolderIds = if (selectedFolderIds.contains(folder.id))
                                                    selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                            } else {
                                                viewModel.navigateToFolder(folder.id)
                                            }
                                        },
                                        onFolderLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedFolderIds = setOf(folder.id)
                                            }
                                        },
                                        onSelectionChanged = { checked ->
                                            selectedFolderIds = if (checked) selectedFolderIds + folder.id else selectedFolderIds - folder.id
                                        }
                                    )
                                    
                                    // Add divider except after last folder (if no recordings) or before recordings section
                                    if (index < folders.lastIndex || saved.isNotEmpty()) {
                                        androidx.compose.material.Divider(
                                            modifier = Modifier.padding(horizontal = sectionHorizontalPadding),
                                            color = textColor.copy(alpha = 0.1f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Recordings section
                        if (saved.isNotEmpty()) {
                            item(key = "recordings_header_$currentFolderId") {
                                SectionHeaderLabel(
                                    text = if (currentFolderId == null) "Uncategorized Recordings" else "Recordings",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                            
                            saved.forEachIndexed { index, entry ->
                                item(key = "recording_${entry.id}_in_$currentFolderId") {
                                    RecordingItem(
                                        entry = entry,
                                        textColor = textColor,
                                        selectionMode = selectionMode,
                                        isSelected = selectedTranscriptionIds.contains(entry.id),
                                        onRecordingClick = {
                                            if (selectionMode) {
                                                selectedTranscriptionIds = if (selectedTranscriptionIds.contains(entry.id))
                                                    selectedTranscriptionIds - entry.id else selectedTranscriptionIds + entry.id
                                            } else {
                                                onOpenTranscription(entry.id)
                                            }
                                        },
                                        onRecordingLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedTranscriptionIds = setOf(entry.id)
                                            }
                                        },
                                        onSelectionChanged = { checked ->
                                            selectedTranscriptionIds = if (checked) selectedTranscriptionIds + entry.id else selectedTranscriptionIds - entry.id
                                        }
                                    )
                                    
                                    // Add divider except for last item
                                    if (index < saved.lastIndex) {
                                        androidx.compose.material.Divider(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = textColor.copy(alpha = 0.1f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Bottom padding
                        item {
                            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
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
                        // Delete all selected items in a single batch operation
                        viewModel.deleteMultiple(context, selectedFolderIds, selectedTranscriptionIds)
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
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = "Add new folder",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: Folder,
    textColor: Color,
    horizontalPadding: Dp,
    selectionMode: Boolean,
    isSelected: Boolean,
    onFolderClick: () -> Unit,
    onFolderLongClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = textColor.copy(alpha = 0.1f)),
                onClick = onFolderClick,
                onLongClick = onFolderLongClick
            )
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder icon
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        
        // Folder content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = folder.name,
                color = textColor,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
        }
        
        // Selection checkbox or arrow
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    entry: SavedTranscription,
    textColor: Color,
    selectionMode: Boolean,
    isSelected: Boolean,
    onRecordingClick: () -> Unit,
    onRecordingLongClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit
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
                onClick = onRecordingClick,
                onLongClick = onRecordingLongClick
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
            Text(
                text = entry.fileLabel,
                color = textColor,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = firstLine,
                color = textColor.copy(alpha = 0.6f),
                style = TextStyle(fontSize = 12.sp)
            )
        }
        
        // Duration and selection
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!selectionMode) {
                Text(
                    text = durationText,
                    color = textColor.copy(alpha = 0.6f),
                    style = TextStyle(fontSize = 12.sp)
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChanged
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderLabel(
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