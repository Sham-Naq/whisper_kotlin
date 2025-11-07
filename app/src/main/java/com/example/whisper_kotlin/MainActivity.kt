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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import com.example.compose.AppTheme
import androidx.compose.material3.MaterialTheme

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
                    
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
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

@Composable
private fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    dividerColor: Color,
    activeTab: BottomTab,
    isViewingDetail: Boolean,
    activeIconColor: Color,
    inactiveIconColor: Color,
    navSelectedBg: Color,
    navUnselectedBg: Color,
    onTabClick: (BottomTab) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // Divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )

    // Navigation icons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Recents tab (left-most)
            NavigationIcon(
                tab = BottomTab.Recents,
                isSelected = activeTab == BottomTab.Recents,
                isViewingDetail = isViewingDetail,
                activeIconColor = activeIconColor,
                inactiveIconColor = inactiveIconColor,
                onClick = { onTabClick(BottomTab.Recents) }
            )

            // Transcription tab
            NavigationIcon(
                tab = BottomTab.Transcription,
                isSelected = activeTab == BottomTab.Transcription,
                isViewingDetail = isViewingDetail,
                activeIconColor = activeIconColor,
                inactiveIconColor = inactiveIconColor,
                onClick = { onTabClick(BottomTab.Transcription) }
            )

            // Recorder tab  
            NavigationIcon(
                tab = BottomTab.Recorder,
                isSelected = activeTab == BottomTab.Recorder,
                isViewingDetail = isViewingDetail,
                activeIconColor = activeIconColor,
                inactiveIconColor = inactiveIconColor,
                onClick = { onTabClick(BottomTab.Recorder) }
            )

            // Settings tab
            NavigationIcon(
                tab = BottomTab.Settings,
                isSelected = activeTab == BottomTab.Settings,
                isViewingDetail = isViewingDetail,
                activeIconColor = activeIconColor,
                inactiveIconColor = inactiveIconColor,
                onClick = { onTabClick(BottomTab.Settings) }
            )
        }
    }
}

@Composable
private fun NavigationIcon(
    tab: BottomTab,
    isSelected: Boolean,
    isViewingDetail: Boolean,
    activeIconColor: Color,
    inactiveIconColor: Color,
    onClick: () -> Unit
) {
    val icon = when (tab) {
        BottomTab.Recents -> Icons.Filled.AccessTime
        BottomTab.Transcription -> Icons.Filled.Folder
        BottomTab.Recorder -> Icons.Filled.Mic
        BottomTab.Settings -> Icons.Filled.Settings
    }
    
    // Animate all tabs slightly when selected to keep interaction consistent
    val shouldAnimate = isSelected
    val scale by animateFloatAsState(
        targetValue = if (shouldAnimate) 1.12f else 1f, 
        label = "navIconScale_${tab.route}"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp, color = Color.Gray.copy(alpha = 0.3f)),
                onClick = { 
                    if (!isSelected || isViewingDetail) {
                        onClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tab.header,
            tint = if (isSelected) activeIconColor else inactiveIconColor,
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
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
    val background = MaterialTheme.colorScheme.background
    val headerBg = MaterialTheme.colorScheme.surface
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val bottomBarBg = MaterialTheme.colorScheme.surface
    val bottomBarDivider = MaterialTheme.colorScheme.outlineVariant
    val navSelectedBg = MaterialTheme.colorScheme.primaryContainer
    val navUnselectedBg = MaterialTheme.colorScheme.surfaceVariant
    val activeIconColor = MaterialTheme.colorScheme.primary
    val inactiveIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    var recorderCommand by remember { mutableStateOf<com.example.whisper_kotlin.recorder.RecorderCommand?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecorderPaused by remember { mutableStateOf(false) }

    val transcriptionViewModel: TranscriptionViewModel = viewModel()

    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var audioSource by remember { mutableStateOf<AudioSource>(AudioSource.Asset("samples/samples_jfk.wav")) }
    var isModelDownloading by remember { mutableStateOf(false) }


    val detailEntry = detailEntryId?.let { transcriptionViewModel.getTranscription(it) }
    val currentHeader = if (detailEntry != null) "Transcript" else activeTab.header
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

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        // Main content area with proper insets for status bar only
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            // Back button (only visible when viewing detail)
            if (detailEntry != null) {
                IconButton(
                    onClick = { detailEntryId = null },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
                    )
                }
            }
            
            // Centered title
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = currentHeader,
                    style = TextStyle(color = primaryTextColor, fontWeight = FontWeight.Bold)
                )
            }

            // Delete button on the right when viewing a detail
            if (detailEntry != null) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete transcription",
                        tint = primaryTextColor
                    )
                }
            }
        }

        // Confirm deletion dialog
        if (showDeleteDialog && detailEntry != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = primaryTextColor,
                title = { Text("Delete transcription?", color = primaryTextColor, fontWeight = FontWeight.SemiBold) },
                text = { Text("This will permanently remove ${detailEntry.fileLabel}.", color = primaryTextColor.copy(alpha = 0.85f)) },
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

            // Main content area
            // Swipe left/right across content to navigate between tabs (disabled while viewing a detail)
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 64.dp.toPx() }
            var cumulativeDragX by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
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
                                            val currentIndex = bottomTabOrder.indexOf(activeTab).coerceAtLeast(0)
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
                        val initialIndex = bottomTabOrder.indexOf(initialState).takeIf { it >= 0 } ?: 0
                        val targetIndex = bottomTabOrder.indexOf(targetState).takeIf { it >= 0 } ?: 0
                        val forward = targetIndex > initialIndex
                        val enter = slideInHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) fullWidth else -fullWidth
                        }
                        val exit = slideOutHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) -fullWidth else fullWidth
                        }
                        enter togetherWith exit
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "tabAnimation"
                ) { tab ->
                    when (tab) {
                        BottomTab.Recents -> {
                            RecentsScreen(
                                modifier = Modifier.fillMaxSize(),
                                textColor = primaryTextColor,
                                viewModel = transcriptionViewModel,
                                onOpenTranscription = { id ->
                                    // Stay on Recents; just show the detail overlay
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
                                onModelDownloadingChanged = { downloading -> isModelDownloading = downloading },
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
                                onThemePreferenceChange = { newPref -> themeSelection = newPref.name }
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = detailEntry != null,
                    modifier = Modifier.matchParentSize(),
                    enter = slideInHorizontally(animationSpec = tween(300)) { it },
                    exit = slideOutHorizontally(animationSpec = tween(300)) { it }
                ) {
                    val context = LocalContext.current
                    val entry = detailEntry
                    if (entry != null) {
                        TranscriptionDetailScreen(
                            entry = entry,
                            textColor = primaryTextColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(background),
                            viewModel = transcriptionViewModel,
                            onBack = { detailEntryId = null },
                            onDelete = {
                                transcriptionViewModel.deleteTranscription(context, entry.id)
                                detailEntryId = null
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(background),
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

        // Bottom Navigation Bar - positioned at screen bottom with navigation bar insets
        BottomNavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            backgroundColor = bottomBarBg,
            dividerColor = bottomBarDivider,
            activeTab = if (detailEntry != null) BottomTab.Transcription else activeTab,
            isViewingDetail = detailEntry != null,
            activeIconColor = activeIconColor,
            inactiveIconColor = inactiveIconColor,
            navSelectedBg = navSelectedBg,
            navUnselectedBg = navUnselectedBg,
            onTabClick = { tab ->
                detailEntryId = null
                activeTab = tab
            }
        )
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
