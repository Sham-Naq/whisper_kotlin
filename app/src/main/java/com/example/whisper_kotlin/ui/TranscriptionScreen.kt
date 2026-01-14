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
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpOffset
import android.widget.Toast
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    var showRecordingMenu by remember { mutableStateOf<SavedTranscription?>(null) }
    var showEditNameDialog by remember { mutableStateOf<SavedTranscription?>(null) }
    var editedName by remember { mutableStateOf("") }
    var showMoveToFolderDialog by remember { mutableStateOf<SavedTranscription?>(null) }
    var showFolderMenu by remember { mutableStateOf<Folder?>(null) }
    var showEditFolderNameDialog by remember { mutableStateOf<Folder?>(null) }
    var editedFolderName by remember { mutableStateOf("") }
    var showMoveFolderDialog by remember { mutableStateOf<Folder?>(null) }
    var pendingDeleteFolder by remember { mutableStateOf<Folder?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val dialogBackground = MaterialTheme.colorScheme.surface
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
            val progress = uiState.progress

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

            // Selection mode top bar
            if (selectionMode) {
                CenterAlignedTopAppBar(
                    title = {
                        val totalSelected = selectedFolderIds.size + selectedTranscriptionIds.size
                        androidx.compose.material3.Text(
                            text = "$totalSelected selected",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedFolderIds = emptySet()
                            selectedTranscriptionIds = emptySet()
                        }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel selection"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedFolderIds.isNotEmpty() || selectedTranscriptionIds.isNotEmpty()) {
                                    showDeleteSelectionDialog = true
                                }
                            },
                            enabled = selectedFolderIds.isNotEmpty() || selectedTranscriptionIds.isNotEmpty()
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete selected"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            if (uiState.isTranscribing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    progress?.let {
                        LinearProgressIndicator(progress = it, modifier = Modifier.fillMaxWidth())
                    } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                                        showMenu = showFolderMenu == folder,
                                        dialogBackground = dialogBackground,
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
                                                showFolderMenu = folder
                                            }
                                        },
                                        onSelectionChanged = { checked ->
                                            selectedFolderIds = if (checked) selectedFolderIds + folder.id else selectedFolderIds - folder.id
                                        },
                                        onDismissMenu = { showFolderMenu = null },
                                        onMenuEditName = {
                                            editedFolderName = folder.name
                                            showEditFolderNameDialog = folder
                                        },
                                        onMenuMoveToFolder = {
                                            showMoveFolderDialog = folder
                                        },
                                        onMenuDelete = {
                                            pendingDeleteFolder = folder
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
                                        showMenu = showRecordingMenu == entry,
                                        dialogBackground = dialogBackground,
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
                                                showRecordingMenu = entry
                                            }
                                        },
                                        onSelectionChanged = { checked ->
                                            selectedTranscriptionIds = if (checked) selectedTranscriptionIds + entry.id else selectedTranscriptionIds - entry.id
                                        },
                                        onDismissMenu = { showRecordingMenu = null },
                                        onMenuOpen = { onOpenTranscription(entry.id) },
                                        onMenuCopyTranscript = {
                                            clipboardManager.setText(AnnotatedString(entry.transcript))

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
                        LazyColumn(
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
                                            imageVector = Icons.Outlined.FolderOpen,
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

        // Edit folder name dialog
        val folderToEdit = showEditFolderNameDialog
        if (folderToEdit != null) {
            AlertDialog(
                onDismissRequest = { showEditFolderNameDialog = null },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = { Text(text = "Rename folder", color = textColor, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            text = "Enter a new name for this folder.",
                            color = textColor.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedFolderName,
                            onValueChange = { editedFolderName = it },
                            singleLine = true,
                            placeholder = { Text("New name") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = editedFolderName.trim()
                            if (name.isNotEmpty()) {
                                viewModel.renameFolder(folderToEdit.id, name)
                            }
                            showEditFolderNameDialog = null
                        }
                    ) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditFolderNameDialog = null }) { Text("Cancel") }
                }
            )
        }

        // Move folder dialog
        val folderToMove = showMoveFolderDialog
        if (folderToMove != null) {
            // Get all folder IDs that are descendants of this folder (including itself)
            fun getDescendantIds(folderId: Long): Set<Long> {
                val result = mutableSetOf(folderId)
                val queue = mutableListOf(folderId)
                while (queue.isNotEmpty()) {
                    val current = queue.removeAt(0)
                    uiState.folders.filter { it.parentId == current }.forEach {
                        if (result.add(it.id)) {
                            queue.add(it.id)
                        }
                    }
                }
                return result
            }
            
            val invalidFolderIds = getDescendantIds(folderToMove.id)
            val availableFolders = uiState.folders
                .filter { it.id !in invalidFolderIds }
                .sortedBy { it.name.lowercase() }
            
            AlertDialog(
                onDismissRequest = { showMoveFolderDialog = null },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = { Text(text = "Move folder", color = textColor, fontWeight = FontWeight.SemiBold) },
                text = {
                    if (availableFolders.isEmpty()) {
                        Text(
                            text = "No valid folders available. Create another folder first.",
                            color = textColor.copy(alpha = 0.85f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Text(
                                    text = "Select a destination folder:",
                                    color = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(availableFolders.size) { index ->
                                val folder = availableFolders[index]
                                TextButton(
                                    onClick = {
                                        viewModel.moveFolderToFolder(folderToMove.id, folder.id)
                                        showMoveFolderDialog = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FolderOpen,
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
                    TextButton(onClick = { showMoveFolderDialog = null }) { Text("Cancel") }
                }
            )
        }

        // Delete folder dialog
        val folderToDelete = pendingDeleteFolder
        if (folderToDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteFolder = null },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = {
                    Text(
                        text = "Delete folder?",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "This will permanently remove \"${folderToDelete.name}\" and all its contents.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteFolder(folderToDelete.id)
                        pendingDeleteFolder = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteFolder = null }) {
                        Text("Cancel")
                    }
                }
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
    showMenu: Boolean,
    dialogBackground: Color,
    onFolderClick: () -> Unit,
    onFolderLongClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit,
    onDismissMenu: () -> Unit,
    onMenuEditName: () -> Unit,
    onMenuMoveToFolder: () -> Unit,
    onMenuDelete: () -> Unit
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
                onCheckedChange = onSelectionChanged,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Dropdown menu anchored to this item
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
            offset = DpOffset(x = 150.dp, y = 0.dp),
            modifier = Modifier.background(color = dialogBackground)
        ) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItem(
    entry: SavedTranscription,
    textColor: Color,
    selectionMode: Boolean,
    isSelected: Boolean,
    showMenu: Boolean,
    dialogBackground: Color,
    onRecordingClick: () -> Unit,
    onRecordingLongClick: () -> Unit,
    onSelectionChanged: (Boolean) -> Unit,
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

        // Duration and selection
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!selectionMode) {
                Text(
                    text = durationText,
                    color = textColor.copy(alpha = 0.6f),
                    style = TextStyle(fontSize = 12.sp)
                )
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChanged,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
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