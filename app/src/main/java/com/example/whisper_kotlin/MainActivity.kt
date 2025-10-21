package com.example.whisper_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TranscriptionRepository.initialize(applicationContext)
        setContent { HomeScreen() }
    }
}

private enum class BottomTab(val route: String, val header: String) {
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
    BottomTab.Transcription,
    BottomTab.Recorder,
    BottomTab.Settings
)

@OptIn(ExperimentalAnimationApi::class)
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
    val background = if (isDark) Color(0xFF121212) else Color(0xFFF7F7F7)
    val headerBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFE0E0E0)
    val primaryTextColor = if (isDark) Color.White else Color.Black
    val bottomBarBg = if (isDark) Color(0xFF1A1A1A) else Color.White
    val bottomBarDivider = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE6E6E6)
    val navSelectedBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
    val navUnselectedBg = if (isDark) Color(0xFF222222) else Color(0xFFF5F5F5)
    val activeIconColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF1E88E5)
    val inactiveIconColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF888888)

    var recorderCommand by remember { mutableStateOf<com.example.whisper_kotlin.recorder.RecorderCommand?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecorderPaused by remember { mutableStateOf(false) }

    val transcriptionViewModel: TranscriptionViewModel = viewModel()

    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var audioSource by remember { mutableStateOf<AudioSource>(AudioSource.Asset("samples/samples_jfk.wav")) }
    var isModelDownloading by remember { mutableStateOf(false) }


    val detailEntry = detailEntryId?.let { transcriptionViewModel.getTranscription(it) }
    val currentHeader = if (detailEntry != null) "Transcript" else activeTab.header

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

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = currentHeader,
                    style = TextStyle(color = primaryTextColor, fontWeight = FontWeight.Bold)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        BottomTab.Transcription -> {
                            TranscriptionScreen(
                                modifier = Modifier.fillMaxSize(),
                                textColor = primaryTextColor,
                                viewModel = transcriptionViewModel,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bottomBarBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(bottomBarDivider)
            )

            val highlightedTab = if (detailEntry != null) BottomTab.Transcription else activeTab
            val isViewingDetail = detailEntry != null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = highlightedTab == BottomTab.Transcription
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleT")
                    BottomNavItem(
                        selected = isSelected,
                        onClick = {
                            detailEntryId = null
                            activeTab = BottomTab.Transcription
                        },
                        modifier = Modifier,
                        selectedBgColor = navSelectedBg,
                        unselectedBgColor = navUnselectedBg,
                        selectedBorderColor = Color.Transparent,
                        unselectedBorderColor = Color.Transparent,
                        drawContainer = false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Transcriptions",
                            tint = if (isSelected) activeIconColor else inactiveIconColor,
                            modifier = Modifier.size(42.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isRecorder = activeTab == BottomTab.Recorder
                    val offsetY by animateDpAsState(
                        targetValue = if (isRecorder) (-24).dp else 0.dp,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "micOffset"
                    )
                    val circleSize by animateDpAsState(
                        targetValue = if (isRecorder) 64.dp else 56.dp,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "micSize"
                    )
                    val circleColor by animateColorAsState(
                        targetValue = if (isRecorder) activeIconColor else Color.Transparent,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "micBg"
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY)
                            .size(circleSize)
                            .background(circleColor, CircleShape)
                            .clickable {
                                if (!isRecorder) {
                                    detailEntryId = null
                                    activeTab = BottomTab.Recorder
                                    isRecorderPaused = false
                                } else {
                                    if (isRecording) {
                                        isRecorderPaused = false
                                        recorderCommand = com.example.whisper_kotlin.recorder.RecorderCommand.Stop
                                    } else {
                                        isRecorderPaused = false
                                        recorderCommand = com.example.whisper_kotlin.recorder.RecorderCommand.Start
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Recorder",
                            tint = if (isRecorder) Color.White else inactiveIconColor,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    if (isRecorder && isRecording) {
                        val pauseIcon = if (isRecorderPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause
                        val pauseDescription = if (isRecorderPaused) "Resume" else "Pause"
                        Row(
                            modifier = Modifier.offset(y = (-210).dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF424242), CircleShape)
                                    .clickable {
                                        if (isRecorderPaused) {
                                            isRecorderPaused = false
                                            recorderCommand = com.example.whisper_kotlin.recorder.RecorderCommand.Resume
                                        } else {
                                            isRecorderPaused = true
                                            recorderCommand = com.example.whisper_kotlin.recorder.RecorderCommand.Pause
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material.Icon(
                                    imageVector = pauseIcon,
                                    contentDescription = pauseDescription,
                                    tint = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                                    .clickable {
                                        isRecorderPaused = false
                                        recorderCommand = com.example.whisper_kotlin.recorder.RecorderCommand.Stop
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = highlightedTab == BottomTab.Settings
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleS")
                    BottomNavItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected || isViewingDetail) {
                                detailEntryId = null
                                activeTab = BottomTab.Settings
                            }
                        },
                        modifier = Modifier,
                        selectedBgColor = navSelectedBg,
                        unselectedBgColor = navUnselectedBg,
                        selectedBorderColor = Color.Transparent,
                        unselectedBorderColor = Color.Transparent,
                        drawContainer = false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = if (isSelected) activeIconColor else inactiveIconColor,
                            modifier = Modifier.size(42.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
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
    val base = if (drawContainer) {
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    } else {
        modifier
    }
    Box(
        modifier = base
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
