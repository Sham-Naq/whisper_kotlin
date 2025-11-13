package com.example.whisper_kotlin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whisper_kotlin.AudioSource
import com.example.whisper_kotlin.ModelOption
import com.example.whisper_kotlin.ActionButton
import com.example.whisper_kotlin.RecentsScreen
import com.example.whisper_kotlin.SavedTranscription
import com.example.whisper_kotlin.SettingsScreen
import com.example.whisper_kotlin.TranscriptionDetailScreen
import com.example.whisper_kotlin.TranscriptionScreen
import com.example.whisper_kotlin.TranscriptionViewModel
import com.example.whisper_kotlin.Folder
import com.example.whisper_kotlin.navigation.BottomTab
import com.example.whisper_kotlin.navigation.ThemePreference
import com.example.whisper_kotlin.navigation.bottomTabOrder
import com.example.whisper_kotlin.recorder.RecorderCommand
import kotlin.math.abs

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun HomeScreen() {
    var activeTab by rememberSaveable { mutableStateOf(BottomTab.Transcription) }
    var detailEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var themeSelection by rememberSaveable { mutableStateOf(ThemePreference.System.name) }
    val themePreference = remember(themeSelection) { ThemePreference.valueOf(themeSelection) }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val primaryTextColor = MaterialTheme.colorScheme.onBackground

    var recorderCommand by remember { mutableStateOf<RecorderCommand?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecorderPaused by remember { mutableStateOf(false) }

    val transcriptionViewModel: TranscriptionViewModel = viewModel()

    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var audioSource by remember { mutableStateOf<AudioSource>(AudioSource.Asset("samples/samples_jfk.wav")) }
    var isModelDownloading by remember { mutableStateOf(false) }

    val detailEntry = detailEntryId?.let { transcriptionViewModel.getTranscription(it) }
    val uiState by transcriptionViewModel.uiState.collectAsState()
    val currentFolder = uiState.currentFolderId?.let { folderId ->
        uiState.folders.firstOrNull { it.id == folderId }
    }
    val isInFolder = uiState.currentFolderId != null

    val currentHeader = when {
        detailEntry != null -> "Transcript"
        currentFolder != null -> currentFolder.name
        else -> activeTab.header
    }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = detailEntry != null) {
        detailEntryId = null
    }

    val shouldReturnToTranscriptions = detailEntry == null && activeTab != BottomTab.Transcription
    if (shouldReturnToTranscriptions) {
        BackHandler {
            activeTab = BottomTab.Transcription
        }
    }

    LaunchedEffect(detailEntryId, detailEntry) {
        if (detailEntryId != null && detailEntry == null) {
            detailEntryId = null
        }
    }

    // Confirm deletion dialog
    if (showDeleteDialog && detailEntry != null) {
        DeleteConfirmationDialog(
            entry = detailEntry,
            primaryTextColor = primaryTextColor,
            onConfirm = {
                transcriptionViewModel.deleteTranscription(context, detailEntry.id)
                showDeleteDialog = false
                detailEntryId = null
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Swipe gesture for tab navigation (disabled while viewing a detail)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var cumulativeDragX by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    currentHeader = currentHeader,
                    detailEntry = detailEntry,
                    isInFolder = isInFolder,
                    activeTab = activeTab,
                    currentFolder = currentFolder,
                    onBackClick = { detailEntryId = null },
                    onFolderBackClick = {
                        val parentId = currentFolder?.parentId
                        transcriptionViewModel.navigateToFolder(parentId)
                    },
                    onDeleteClick = { showDeleteDialog = true }
                )
            },
            bottomBar = {
                // Only show bottom bar when not viewing detail
                if (detailEntry == null) {
                    HomeBottomBar(
                        activeTab = activeTab,
                        isInFolder = isInFolder,
                        onTabSelected = { tab ->
                            detailEntryId = null
                            activeTab = tab
                            // When clicking Transcription tab while in a folder, go to root
                            if (tab == BottomTab.Transcription && isInFolder) {
                                transcriptionViewModel.navigateToFolder(null)
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .let { base ->
                        if (detailEntry == null) {
                            base.pointerInput(activeTab) {
                                detectHorizontalDragGestures(
                                    onDragStart = { cumulativeDragX = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        cumulativeDragX += dragAmount
                                    },
                                    onDragEnd = {
                                        if (abs(cumulativeDragX) >= swipeThresholdPx) {
                                            val currentIndex =
                                                bottomTabOrder.indexOf(activeTab).coerceAtLeast(0)
                                            val target = if (cumulativeDragX > 0f) {
                                                bottomTabOrder.getOrNull(currentIndex - 1)
                                            } else {
                                                bottomTabOrder.getOrNull(currentIndex + 1)
                                            }
                                            if (target != null) {
                                                detailEntryId = null
                                                activeTab = target
                                            }
                                        }
                                    }
                                )
                            }
                        } else base
                    }
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val initialIndex =
                            bottomTabOrder.indexOf(initialState).takeIf { it >= 0 } ?: 0
                        val targetIndex =
                            bottomTabOrder.indexOf(targetState).takeIf { it >= 0 } ?: 0
                        val forward = targetIndex > initialIndex
                        val enter = slideInHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) fullWidth else -fullWidth
                        }
                        val exit = slideOutHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) -fullWidth else fullWidth
                        }
                        enter togetherWith exit
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    label = "tabAnimation"
                ) { tab ->
                    TabContent(
                        tab = tab,
                        primaryTextColor = primaryTextColor,
                        isDark = isDark,
                        transcriptionViewModel = transcriptionViewModel,
                        selectedModel = selectedModel,
                        onSelectModel = { selectedModel = it },
                        isModelDownloading = isModelDownloading,
                        onModelDownloadingChanged = { isModelDownloading = it },
                        audioSource = audioSource,
                        onAudioSourceChanged = { audioSource = it },
                        recorderCommand = recorderCommand,
                        onCommandHandled = { recorderCommand = null },
                        onRecordingStateChanged = { recording ->
                            isRecording = recording
                            if (!recording) {
                                isRecorderPaused = false
                            }
                        },
                        themePreference = themePreference,
                        onThemePreferenceChange = { themeSelection = it.name },
                        onOpenTranscription = { id ->
                            activeTab = BottomTab.Transcription
                            detailEntryId = id
                        }
                    )
                }
            }

            // Detail overlay - covers entire screen including bottom navigation
            AnimatedVisibility(
                visible = detailEntry != null,
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(animationSpec = tween(300)) { it },
                exit = slideOutHorizontally(animationSpec = tween(300)) { it }
            ) {
                DetailOverlay(
                    detailEntry = detailEntry,
                    primaryTextColor = primaryTextColor,
                    transcriptionViewModel = transcriptionViewModel,
                    context = context,
                    onBack = { detailEntryId = null },
                    onDelete = {
                        transcriptionViewModel.deleteTranscription(context, detailEntry!!.id)
                        detailEntryId = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    currentHeader: String,
    detailEntry: SavedTranscription?,
    isInFolder: Boolean,
    activeTab: BottomTab,
    currentFolder: Folder?,
    onBackClick: () -> Unit,
    onFolderBackClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = currentHeader,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = Modifier.height(64.dp),
        navigationIcon = {
            if (detailEntry != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else if (isInFolder && activeTab == BottomTab.Transcription) {
                IconButton(onClick = onFolderBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back to parent folder"
                    )
                }
            }
        },
        actions = {
            if (detailEntry != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete transcription"
                    )
                }
            }
        },
    )
}

@Composable
private fun HomeBottomBar(
    activeTab: BottomTab,
    isInFolder: Boolean,
    onTabSelected: (BottomTab) -> Unit
) {
    NavigationBar {
        bottomTabOrder.forEach { tab ->
            NavigationBarItem(
                icon = {
                    Icon(
                        tab.getIcon(),
                        contentDescription = tab.header,
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = null,
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: BottomTab,
    primaryTextColor: androidx.compose.ui.graphics.Color,
    isDark: Boolean,
    transcriptionViewModel: TranscriptionViewModel,
    selectedModel: ModelOption?,
    onSelectModel: (ModelOption?) -> Unit,
    isModelDownloading: Boolean,
    onModelDownloadingChanged: (Boolean) -> Unit,
    audioSource: AudioSource,
    onAudioSourceChanged: (AudioSource) -> Unit,
    recorderCommand: RecorderCommand?,
    onCommandHandled: () -> Unit,
    onRecordingStateChanged: (Boolean) -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onOpenTranscription: (Long) -> Unit
) {
    when (tab) {
        BottomTab.Recents -> {
            RecentsScreen(
                modifier = Modifier.fillMaxSize(),
                textColor = primaryTextColor,
                viewModel = transcriptionViewModel,
                onOpenTranscription = onOpenTranscription
            )
        }

        BottomTab.Transcription -> {
            TranscriptionScreen(
                modifier = Modifier.fillMaxSize(),
                textColor = primaryTextColor,
                viewModel = transcriptionViewModel,
                isDetailVisible = false,
                onOpenTranscription = onOpenTranscription
            )
        }

        BottomTab.Recorder -> {
            com.example.whisper_kotlin.recorder.RecorderScreen(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark,
                textColor = primaryTextColor,
                selectedModel = selectedModel,
                onSelectModel = onSelectModel,
                isModelDownloading = isModelDownloading,
                onModelDownloadingChanged = onModelDownloadingChanged,
                audioSource = audioSource,
                onAudioSourceChanged = onAudioSourceChanged,
                transcriptionViewModel = transcriptionViewModel,
                command = recorderCommand,
                onCommandHandled = onCommandHandled,
                onRecordingStateChanged = onRecordingStateChanged
            )
        }

        BottomTab.Settings -> {
            SettingsScreen(
                modifier = Modifier.fillMaxSize(),
                textColor = primaryTextColor,
                themePreference = themePreference,
                onThemePreferenceChange = onThemePreferenceChange
            )
        }
    }
}

@Composable
private fun DetailOverlay(
    detailEntry: SavedTranscription?,
    primaryTextColor: androidx.compose.ui.graphics.Color,
    transcriptionViewModel: TranscriptionViewModel,
    context: Context,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val entry = detailEntry
    if (entry != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Respect the top bar height
            Spacer(modifier = Modifier.height(54.dp))

            TranscriptionDetailScreen(
                entry = entry,
                textColor = primaryTextColor,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                viewModel = transcriptionViewModel,
                onBack = onBack,
                onDelete = onDelete
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Respect the top bar height
            Spacer(modifier = Modifier.height(54.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BasicText(
                        text = "Transcription not found.",
                        style = TextStyle(color = primaryTextColor)
                    )
                    ActionButton(label = "Back", color = primaryTextColor) {
                        onBack()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    entry: SavedTranscription,
    primaryTextColor: androidx.compose.ui.graphics.Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = primaryTextColor,
        title = {
            androidx.compose.material.Text(
                "Delete transcription?",
                color = primaryTextColor,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            androidx.compose.material.Text(
                "This will permanently remove ${entry.fileLabel}.",
                color = primaryTextColor.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                androidx.compose.material.Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material.Text("Cancel")
            }
        }
    )
}
