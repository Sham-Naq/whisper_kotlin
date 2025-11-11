package com.example.whisper_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.activity.compose.BackHandler

import android.content.res.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whisper_kotlin.TranscriptionViewModel
import com.example.whisper_kotlin.data.TranscriptionRepository
// Material icons for bottom navigation
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import com.example.compose.AppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        TranscriptionRepository.initialize(applicationContext)
        setContent {
            AppTheme {
                HomeScreen()

                // Set status bar color based on theme
                val statusBarColor = MaterialTheme.colorScheme.surface
                val isDark = isSystemInDarkTheme()

                SideEffect {
                    window.statusBarColor = statusBarColor.toArgb()
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT

                    val insetsController =
                        WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }
}

private enum class BottomTab(val route: String, val header: String) {
    Recents(route = "recents", header = "Recents"),
    Transcription(route = "transcription", header = "Transcription"),
    Recorder(route = "recorder", header = "Recorder"),
    Settings(route = "settings", header = "Settings")
}

enum class ThemePreference {
    System,
    Light,
    Dark
}

private val bottomTabOrder: List<BottomTab> = listOf(
    BottomTab.Recents,
    BottomTab.Transcription,
    BottomTab.Recorder,
    BottomTab.Settings
)

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
private fun HomeScreen() {
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

    var recorderCommand by remember {
        mutableStateOf<com.example.whisper_kotlin.recorder.RecorderCommand?>(
            null
        )
    }
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = primaryTextColor,
            title = {
                Text(
                    "Delete transcription?",
                    color = primaryTextColor,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "This will permanently remove ${detailEntry.fileLabel}.",
                    color = primaryTextColor.copy(alpha = 0.85f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    transcriptionViewModel.deleteTranscription(context, detailEntry.id)
                    showDeleteDialog = false
                    detailEntryId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Swipe gesture for tab navigation (disabled while viewing a detail)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var cumulativeDragX by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        androidx.compose.material3.Text(
                            text = currentHeader,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    modifier = Modifier.height(64.dp),
                    navigationIcon = {
                        if (detailEntry != null) {
                            IconButton(onClick = { detailEntryId = null }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else if (isInFolder && activeTab == BottomTab.Transcription) {
                            IconButton(onClick = {
                                val parentId = currentFolder?.parentId
                                transcriptionViewModel.navigateToFolder(parentId)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back to parent folder"
                                )
                            }
                        }
                    },
                    actions = {
                        if (detailEntry != null) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete transcription"
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                // Only show bottom bar when not viewing detail
                if (detailEntry == null) {
                    NavigationBar{
                        bottomTabOrder.forEach { tab ->
                            val icon = when (tab) {
                                BottomTab.Recents -> Icons.Filled.AccessTime
                                BottomTab.Transcription -> Icons.Filled.Folder
                                BottomTab.Recorder -> Icons.Filled.Mic
                                BottomTab.Settings -> Icons.Filled.Settings
                            }

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = tab.header,
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                label = null,
                                selected = activeTab == tab,
                                onClick = {
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
                    when (tab) {
                        BottomTab.Recents -> {
                            RecentsScreen(
                                modifier = Modifier.fillMaxSize(),
                                textColor = primaryTextColor,
                                viewModel = transcriptionViewModel,
                                onOpenTranscription = { id ->
                                    detailEntryId = id
                                }
                            )
                        }

                        BottomTab.Transcription -> {
                            TranscriptionScreen(
                                modifier = Modifier.fillMaxSize(),
                                textColor = primaryTextColor,
                                viewModel = transcriptionViewModel,
                                isDetailVisible = detailEntry != null,
                                onOpenTranscription = { id ->
                                    activeTab = BottomTab.Transcription
                                    detailEntryId = id
                                }
                            )
                        }

                        BottomTab.Recorder -> {
                            com.example.whisper_kotlin.recorder.RecorderScreen(
                                modifier = Modifier.fillMaxSize(),
                                isDark = isDark,
                                textColor = primaryTextColor,
                                selectedModel = selectedModel,
                                onSelectModel = { selectedModel = it },
                                isModelDownloading = isModelDownloading,
                                onModelDownloadingChanged = { downloading ->
                                    isModelDownloading = downloading
                                },
                                audioSource = audioSource,
                                onAudioSourceChanged = { audioSource = it },
                                transcriptionViewModel = transcriptionViewModel,
                                command = recorderCommand,
                                onCommandHandled = { recorderCommand = null },
                                onRecordingStateChanged = { recording ->
                                    isRecording = recording
                                    if (!recording) {
                                        isRecorderPaused = false
                                    }
                                }
                            )
                        }

                        BottomTab.Settings -> {
                            SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                textColor = primaryTextColor,
                                themePreference = themePreference,
                                onThemePreferenceChange = { newPref ->
                                    themeSelection = newPref.name
                                }
                            )
                        }
                    }
                }
            }

            // Detail overlay - covers entire screen including bottom navigation
            androidx.compose.animation.AnimatedVisibility(
                visible = detailEntry != null,
                modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally(animationSpec = tween(300)) { it },
                exit = slideOutHorizontally(animationSpec = tween(300)) { it }
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
                            onBack = { detailEntryId = null },
                            onDelete = {
                                transcriptionViewModel.deleteTranscription(context, entry.id)
                                detailEntryId = null
                            }
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
                                    detailEntryId = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedBgColor: Color = Color(0xFFE0E0E0),
    unselectedBgColor: Color = Color(0xFFF5F5F5),
    selectedBorderColor: Color = Color(0xFF9E9E9E),
    unselectedBorderColor: Color = Color(0xFFE0E0E0),
    drawContainer: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg = if (selected) selectedBgColor else unselectedBgColor
    val borderColor = if (selected) selectedBorderColor else unselectedBorderColor
    val shape = RoundedCornerShape(10.dp)
    val base = if (drawContainer) {
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
    } else {
        modifier
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = base
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = drawContainer,
                    color = Color.Gray.copy(alpha = 0.3f)
                ),
                onClick = onClick
            )
            .padding(vertical = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
