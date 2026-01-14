package com.example.whisper_kotlin.recorder

import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.ripple
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.whisper_kotlin.ui.AudioSource
import com.example.whisper_kotlin.ui.ModelOption
import com.example.whisper_kotlin.ui.ModelDownloadViewModel
import com.example.whisper_kotlin.ui.TranscriptionViewModel
import com.example.whisper_kotlin.ui.EmbeddedTranscriptionDetail
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import com.example.whisper_kotlin.ui.components.DropdownMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator

private enum class RecorderVisualState {
    Idle,
    Recording,
    Transcribing,
    Complete
}

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    textColor: Color,
    selectedModel: ModelOption?,
    onSelectModel: (ModelOption) -> Unit,
    isModelDownloading: Boolean,
    onModelDownloadingChanged: (Boolean) -> Unit,
    audioSource: AudioSource,
    onAudioSourceChanged: (AudioSource) -> Unit,
    transcriptionViewModel: TranscriptionViewModel,
    command: RecorderCommand?,
    onCommandHandled: () -> Unit,
    onRecordingStateChanged: (Boolean) -> Unit,
    onViewTranscription: (Long) -> Unit,
    initialFolderId: Long? = null,
    selectedLanguageCode: String = "en",
    onLanguageSelectionRequested: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(scope = scope) }
    val focusManager = LocalFocusManager.current
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var recorderPaused by rememberSaveable { mutableStateOf(false) }
    var rawFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var wavFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var isRecordedAudio by rememberSaveable { mutableStateOf(false) }
    var recordingElapsedSeconds by rememberSaveable { mutableStateOf(0) }

    fun currentRawFile(): File? = rawFilePath?.let { File(it) }?.takeIf { it.exists() }
    fun currentWavFile(): File? = wavFilePath?.let { File(it) }?.takeIf { it.exists() }
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var showCancelTranscriptionDialog by remember { mutableStateOf(false) }
    var showCancelDownloadDialog by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var lastPlayerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var playbackProgress by rememberSaveable { mutableStateOf(0f) }
    var playbackPositionMs by rememberSaveable { mutableStateOf(0) }
    var playbackDurationMs by rememberSaveable { mutableStateOf(0) }
    var selectedFolderId by rememberSaveable { mutableStateOf<Long?>(initialFolderId) }

    val transcriptionUi by transcriptionViewModel.uiState.collectAsState()
    val allFolders = transcriptionUi.folders
    val bars by recorder.bars.collectAsState()

    val cacheDir = context.cacheDir

    fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        playbackProgress = 0f
        playbackPositionMs = 0
        playbackDurationMs = 0
    }

    fun togglePlayback() {
        val player = mediaPlayer ?: return
        try {
            if (isPlaying || player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        } catch (_: Throwable) {
            isPlaying = false
        }
    }

    val startRecording: () -> Unit = {
        releasePlayer()
        currentWavFile()?.delete()
        wavFilePath = null
        isRecordedAudio = false
        currentRawFile()?.delete()
        val newRaw = File(cacheDir, "rec_${System.currentTimeMillis()}.pcm")
        rawFilePath = newRaw.absolutePath
        recorder.start(newRaw)
        isRecording = true
        recorderPaused = false
        recordingElapsedSeconds = 0
        onRecordingStateChanged(true)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        }
        onCommandHandled()
    }

    DisposableEffect(wavFilePath) {
        val file = currentWavFile()
        val player = if (file != null && file.exists()) {
            try {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        playbackProgress = 0f
                        playbackPositionMs = 0
                    }
                }
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        mediaPlayer = player
        if (player != null && file != null) {
            playbackDurationMs = player.duration
            if (wavFilePath != lastPlayerPath) {
                playbackPositionMs = 0
                playbackProgress = 0f
            } else if (playbackPositionMs in 1 until player.duration) {
                player.seekTo(playbackPositionMs)
                playbackProgress =
                    (playbackPositionMs.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
            }
            lastPlayerPath = wavFilePath
        } else {
            playbackDurationMs = 0
            playbackProgress = 0f
            playbackPositionMs = 0
            lastPlayerPath = null
        }
        isPlaying = false

        onDispose {
            player?.release()
            if (mediaPlayer === player) {
                mediaPlayer = null
            }
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        val player = mediaPlayer
        if (isPlaying && player != null) {
            while (isPlaying && player.isPlaying) {
                playbackPositionMs = player.currentPosition
                playbackDurationMs = player.duration
                playbackProgress = if (player.duration > 0) {
                    (player.currentPosition.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                delay(200)
            }
        }
    }

    LaunchedEffect(command) {
        when (command) {
            RecorderCommand.Pause -> {
                if (isRecording) {
                    recorder.pause()
                    recorderPaused = true
                }
                onCommandHandled()
            }

            RecorderCommand.Resume -> {
                if (isRecording) {
                    recorder.resume()
                    recorderPaused = false
                }
                onCommandHandled()
            }

            RecorderCommand.Stop -> {
                if (isRecording) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            recorder.stop()
                            val rf = currentRawFile()
                            if (rf != null && rf.exists() && rf.length() > 0) {
                                val wf = File(cacheDir, rf.nameWithoutExtension + ".wav")
                                WavWriter16kMonoPcm16.wrapRawPcmToWav(rf, wf)
                                withContext(Dispatchers.Main) {
                                    wavFilePath = wf.absolutePath
                                    isRecordedAudio = true
                                    onAudioSourceChanged(AudioSource.File(wf.absolutePath))
                                }
                                rf.delete()
                                rawFilePath = null
                            } else {
                                withContext(Dispatchers.Main) {
                                    wavFilePath = null
                                    isRecordedAudio = false
                                    rawFilePath = null
                                }
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isRecording = false
                                recorderPaused = false
                                onRecordingStateChanged(false)
                            }
                        }
                    }
                }
                onCommandHandled()
            }

            RecorderCommand.Start, null -> {
                onCommandHandled()
            }
        }
    }

    LaunchedEffect(audioSource) {
        val filePath = (audioSource as? AudioSource.File)?.path
        val normalized = filePath?.takeIf { File(it).exists() }
        if (normalized != wavFilePath) {
            if (normalized != null && wavFilePath != normalized) {
                wavFilePath = null
                isRecordedAudio = false
            } else if (normalized == null) {
                playbackProgress = 0f
                playbackPositionMs = 0
                playbackDurationMs = 0
            }
        }
    }

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val canPlayRecording =
        isRecordedAudio && currentWavFile() != null && mediaPlayer != null && playbackDurationMs > 0
    val hasAudioSource =
        currentWavFile() != null || (audioSource is AudioSource.File && File((audioSource as AudioSource.File).path).exists())
    val canTranscribe =
        !isRecording && !transcriptionUi.isTranscribing && (!isModelDownloading) && hasAudioSource

    fun generateDefaultTranscriptionName(): String {
        val pattern = SimpleDateFormat("yyyyMMdd HH:mm:ss", Locale.getDefault())
        return "Meeting-${pattern.format(Date())}"
    }

    var transcriptionName by rememberSaveable { mutableStateOf(generateDefaultTranscriptionName()) }

    val selectorButtonBg = MaterialTheme.colorScheme.surfaceVariant

    // Track transcription state
    var showTranscriptionComplete by remember { mutableStateOf(false) }

    var displayedState by rememberSaveable { mutableStateOf(RecorderVisualState.Idle) }
    var lastNonIdleState by rememberSaveable { mutableStateOf<RecorderVisualState?>(null) }

    fun clearVisualState() {
        lastNonIdleState = null
        displayedState = RecorderVisualState.Idle
    }

    fun resetAfterTranscription() {
        showTranscriptionComplete = false
        wavFilePath = null
        rawFilePath = null
        isRecordedAudio = false
        transcriptionName = generateDefaultTranscriptionName()
        releasePlayer()
        clearVisualState()
    }

    val modelDownloadVm: ModelDownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val modelDownloadState by modelDownloadVm.uiState.collectAsState()

    LaunchedEffect(modelDownloadState.downloadingId, modelDownloadState.isLoadingModel) {
        onModelDownloadingChanged(modelDownloadState.downloadingId != null || modelDownloadState.isLoadingModel)
    }

    LaunchedEffect(selectedModel) {
        if (selectedModel != null && modelDownloadState.selectedModel?.id != selectedModel.id) {
            modelDownloadVm.selectIfPresent(context, selectedModel)
        }
    }

    LaunchedEffect(modelDownloadState.selectedModel) {
        val downloadedModel = modelDownloadState.selectedModel
        if (downloadedModel != null && downloadedModel.id != selectedModel?.id) {
            onSelectModel(downloadedModel)
        }
    }

    // Auto-start transcription when recording stops
    LaunchedEffect(isRecording, wavFilePath) {
        if (!isRecording && wavFilePath != null && !showTranscriptionComplete && !transcriptionUi.isTranscribing) {
            // Start transcription automatically when recording stops
            val recordedFile = currentWavFile()
            if (recordedFile != null) {
                val modelToUse = modelDownloadState.selectedModel ?: selectedModel
                transcriptionViewModel.startTranscription(
                    context = context,
                    selectedModel = modelToUse,
                    audioSource = AudioSource.File(recordedFile.absolutePath),
                    isModelDownloading = isModelDownloading,
                    transcriptionName = transcriptionName,
                    targetFolderId = selectedFolderId,
                    languageCode = selectedLanguageCode
                )
            }
        }
    }

    // Reset transcription complete state when starting a new recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            showTranscriptionComplete = false
        }
    }

    LaunchedEffect(isRecording, recorderPaused) {
        if (isRecording && !recorderPaused) {
            while (true) {
                delay(1000)
                recordingElapsedSeconds += 1
            }
        }
    }

    // Show transcription complete when transcription finishes
    LaunchedEffect(transcriptionUi.isTranscribing) {
        if (!transcriptionUi.isTranscribing && wavFilePath != null && !isRecording) {
            showTranscriptionComplete = true
        }
    }

    val targetVisualState = when {
        isRecording -> RecorderVisualState.Recording
        transcriptionUi.isTranscribing -> RecorderVisualState.Transcribing
        showTranscriptionComplete -> RecorderVisualState.Complete
        else -> RecorderVisualState.Idle
    }

    LaunchedEffect(
        targetVisualState,
        wavFilePath,
        isRecording,
        transcriptionUi.isTranscribing,
        showTranscriptionComplete
    ) {
        when {
            targetVisualState == RecorderVisualState.Idle &&
                wavFilePath != null &&
                !isRecording &&
                !showTranscriptionComplete -> {
                displayedState = RecorderVisualState.Transcribing
                lastNonIdleState = RecorderVisualState.Transcribing
            }

            targetVisualState == RecorderVisualState.Idle -> {
                displayedState = lastNonIdleState ?: RecorderVisualState.Idle
            }

            else -> {
                displayedState = targetVisualState
                lastNonIdleState = targetVisualState
            }
        }
    }

    val topSectionPadding = if (displayedState != RecorderVisualState.Idle) 0.dp else 16.dp
    val audioSectionHeight = 150.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(focusManager) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = topSectionPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show different content based on state
            when (displayedState) {
                // Transcribing state: use EmbeddedTranscriptionDetail
                RecorderVisualState.Transcribing -> {
                    val latestEntry = transcriptionUi.savedTranscriptions.firstOrNull()
                    val activeModelLabel = modelDownloadState.selectedModel?.id ?: selectedModel?.id ?: "tiny"
                    EmbeddedTranscriptionDetail(
                        entry = latestEntry,
                        textColor = textColor,
                        isTranscribing = true,
                        modifier = Modifier.weight(1f),
                        selectedModelLabel = activeModelLabel,
                        mediaPlayer = mediaPlayer,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        playbackPositionMs = playbackPositionMs,
                        playbackDurationMs = playbackDurationMs,
                        onTogglePlayback = { togglePlayback() },
                        onSeek = { value ->
                            if (mediaPlayer != null && playbackDurationMs > 0) {
                                val newPos = (value * playbackDurationMs).toInt()
                                mediaPlayer?.seekTo(newPos)
                                playbackPositionMs = newPos
                                playbackProgress = value
                            }
                        },
                        onCancel = { showCancelTranscriptionDialog = true }
                    )
                }
                // Recording state layout - same card-based look as TranscriptionDetailScreen
                RecorderVisualState.Recording -> {
                    val timerText = formatTime(recordingElapsedSeconds * 1000)
                    val activeModelLabel =
                        modelDownloadState.selectedModel?.id ?: selectedModel?.id ?: "tiny"
                    val pulseTransition = rememberInfiniteTransition(label = "recordingPulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "recordingPulseAlpha"
                    )

                    val recordingStatusColor = Color(0xFFFF3B30)
                    val recordingStatusText = if (recorderPaused) "Paused" else "Recording"
                    
                    // Card colors - same as TranscriptionDetailScreen
                    val pageBackground = MaterialTheme.colorScheme.background
                    val subtleCardBackground = pageBackground
                    val transcriptCardBackground = MaterialTheme.colorScheme.surface
                    val cardBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                    val cornerRadius = 16.dp
                    val emphasizedCornerRadius = cornerRadius + 12.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .background(pageBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 0.dp, vertical = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header: Title centered - same as TranscriptionDetailScreen
                            androidx.compose.foundation.text.BasicText(
                                text = transcriptionName.ifBlank { "Recording" },
                                style = TextStyle(
                                    color = textColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Info row: Duration | Model | Recording indicator - same layout as TranscriptionDetailScreen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Duration with clock icon
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "Duration",
                                        modifier = Modifier.size(16.dp),
                                        tint = textColor.copy(alpha = 0.65f)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    androidx.compose.foundation.text.BasicText(
                                        text = timerText,
                                        style = TextStyle(color = textColor.copy(alpha = 0.65f), fontSize = 14.sp)
                                    )
                                }
                                // Model with globe icon
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = "Model",
                                        modifier = Modifier.size(16.dp),
                                        tint = textColor.copy(alpha = 0.65f)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    androidx.compose.foundation.text.BasicText(
                                        text = activeModelLabel,
                                        style = TextStyle(color = textColor.copy(alpha = 0.65f), fontSize = 14.sp)
                                    )
                                }
                                // Recording status indicator with pulsing dot
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = recordingStatusColor.copy(alpha = pulseAlpha),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    androidx.compose.foundation.text.BasicText(
                                        text = recordingStatusText,
                                        style = TextStyle(color = recordingStatusColor, fontSize = 14.sp)
                                    )
                                }
                            }

                            // Cancel button row - aligned to the right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (isRecording) {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    recorder.stop()
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isRecording = false
                                                        recorderPaused = false
                                                        recordingElapsedSeconds = 0
                                                        currentRawFile()?.delete()
                                                        rawFilePath = null
                                                        wavFilePath = null
                                                        isRecordedAudio = false
                                                        onRecordingStateChanged(false)
                                                        clearVisualState()
                                                    }
                                                }
                                            }
                                        } else {
                                            clearVisualState()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Card: Transcription content placeholder - same styling as TranscriptionDetailScreen
                            androidx.compose.material.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true),
                                shape = RoundedCornerShape(emphasizedCornerRadius),
                                backgroundColor = transcriptCardBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.padding(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = textColor.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "Transcription will appear here once recording is stopped and processed.",
                                            textAlign = TextAlign.Center,
                                            color = textColor.copy(alpha = 0.7f),
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp
                                        )
                                        Text(
                                            text = "Continue recording to capture your audio content.",
                                            textAlign = TextAlign.Center,
                                            color = textColor.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Card: Audio waveform - same styling as TranscriptionDetailScreen audio card
                            androidx.compose.material.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(emphasizedCornerRadius),
                                backgroundColor = subtleCardBackground,
                                elevation = 0.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    if (recorderPaused) {
                                        Text(
                                            text = "Recording Paused",
                                            color = textColor.copy(alpha = 0.6f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    } else {
                                        LineBarWaveform(
                                            modifier = Modifier.fillMaxSize(),
                                            bars = bars,
                                            color = MaterialTheme.colorScheme.primary,
                                            backgroundColor = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Transcription complete state: use EmbeddedTranscriptionDetail
                RecorderVisualState.Complete -> {
                    val latestEntry = transcriptionUi.savedTranscriptions.firstOrNull()
                    val activeModelLabel = modelDownloadState.selectedModel?.id ?: selectedModel?.id ?: "tiny"
                    EmbeddedTranscriptionDetail(
                        entry = latestEntry,
                        textColor = textColor,
                        isTranscribing = false,
                        modifier = Modifier.weight(1f),
                        selectedModelLabel = activeModelLabel,
                        mediaPlayer = mediaPlayer,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        playbackPositionMs = playbackPositionMs,
                        playbackDurationMs = playbackDurationMs,
                        onTogglePlayback = { togglePlayback() },
                        onSeek = { value ->
                            if (mediaPlayer != null && playbackDurationMs > 0) {
                                val newPos = (value * playbackDurationMs).toInt()
                                mediaPlayer?.seekTo(newPos)
                                playbackPositionMs = newPos
                                playbackProgress = value
                            }
                        },
                        onCancel = { resetAfterTranscription() }
                    )
                }
                // Default state: Show logo, instructions, and options
                RecorderVisualState.Idle -> {
                    // Logo and instructions
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Blue circular logo with mic icon
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // "Ready to Record" title
                        Text(
                            text = "Ready to Record",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        // Instructions
                        Text(
                            text = "Configure your recording settings below",
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }

                    //Spacer(modifier = Modifier.height(16.dp))

                    // Options card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(16.dp),
                                clip = false
                            )
                            .background(
                                if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        // Recording Name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(y = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = transcriptionName,
                                onValueChange = { transcriptionName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(color = textColor, fontSize = 15.sp),
                                placeholder = {
                                    Text(
                                        text = "Recording Name",
                                        color = textColor.copy(alpha = 0.4f),
                                        fontSize = 15.sp
                                    )
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    backgroundColor = Color.Transparent
                                )
                            )
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    transcriptionName = generateDefaultTranscriptionName()
                                }
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.AccessTime,
                                    contentDescription = "Generate timestamp",
                                    tint = textColor.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Divider
                        androidx.compose.material.Divider(
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor.copy(alpha = 0.1f),
                            thickness = 0.5.dp
                        )

                        // Speech Model
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = modelDownloadState.downloadingId == null) {
                                    // Expand dropdown
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Language,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(y = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (modelDownloadState.isLoadingModel) {
                                // Show loading model message
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Loading model…",
                                        color = textColor,
                                        fontSize = 14.sp
                                    )
                                }
                            } else if (modelDownloadState.downloadingId != null) {
                                // Show download progress with cancel option
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Downloading ${modelDownloadState.downloadingId}…",
                                            color = textColor,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${modelDownloadState.progressPct}%",
                                            color = textColor.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = "Cancel",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clickable { showCancelDownloadDialog = true }
                                            .padding(8.dp)
                                    )
                                }
                            } else {
                                ReusableDropdown(
                                    label = "",
                                    selectedText = when {
                                        modelDownloadState.selectedModel != null -> "${modelDownloadState.selectedModel!!.id}"
                                        selectedModel != null -> "${selectedModel.id}"
                                        else -> "tiny"
                                    },
                                    textColor = textColor,
                                    isDark = isDark,
                                    modifier = Modifier.weight(1f),
                                    enabled = true
                                ) { closeMenu ->
                                    val options = remember {
                                        ModelManager.availableModels().map { spec ->
                                            ModelOption(
                                                id = spec.id,
                                                fileName = spec.fileName,
                                                url = spec.url
                                            )
                                        }
                                    }

                                    options.forEach { opt ->
                                        val isDownloaded =
                                            ModelManager.isModelPresent(context, opt.fileName)
                                        DropdownMenuItem(
                                            text = opt.id,
                                            textColor = textColor,
                                            onClick = {
                                                if (isDownloaded) {
                                                    onSelectModel(opt)
                                                    modelDownloadVm.selectIfPresent(context, opt)
                                                    closeMenu()
                                                } else {
                                                    onModelDownloadingChanged(true)
                                                    modelDownloadVm.startDownloadOrSelect(context, opt)
                                                    closeMenu()
                                                }
                                            },
                                            enabled = modelDownloadState.downloadingId == null,
                                            trailingIcon = {
                                                if (isDownloaded) {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                                                        contentDescription = "Downloaded",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Filled.Download,
                                                        contentDescription = "Not downloaded",
                                                        tint = textColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Divider
                        androidx.compose.material.Divider(
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor.copy(alpha = 0.1f),
                            thickness = 0.5.dp
                        )

                        // Language selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Public,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(y = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = textColor.copy(alpha = 0.1f))
                                    ) { onLanguageSelectionRequested() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material.Text(
                                    text = when (selectedLanguageCode) {
                                        "es" -> "Spanish"
                                        "fr" -> "French"
                                        "de" -> "German"
                                        "ru" -> "Russian"
                                        "ur" -> "Urdu"
                                        "zh" -> "Chinese"
                                        "ja" -> "Japanese"
                                        "ko" -> "Korean"
                                        "pt" -> "Portuguese"
                                        "ar" -> "Arabic"
                                        "hi" -> "Hindi"
                                        else -> "English"
                                    },
                                    color = textColor,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.ArrowForwardIos,
                                    contentDescription = "Select Language",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Divider
                        androidx.compose.material.Divider(
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor.copy(alpha = 0.1f),
                            thickness = 0.5.dp
                        )

                        // Save to Folder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedFolderId == null) {
                                    androidx.compose.material.icons.Icons.Outlined.Home
                                } else {
                                    androidx.compose.material.icons.Icons.Outlined.Folder
                                },
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(y = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ReusableDropdown(
                                label = "",
                                selectedText = selectedFolderId?.let { id ->
                                    allFolders.firstOrNull { it.id == id }?.name
                                } ?: "All Recordings",
                                textColor = textColor,
                                isDark = isDark,
                                modifier = Modifier.weight(1f),
                                enabled = true
                            ) { closeMenu ->
                                val childrenByParent = allFolders.groupBy { it.parentId }

                                @Composable
                                fun renderItem(indent: Int, id: Long?, name: String) {
                                    val prefix =
                                        if (indent > 0) ("  ".repeat(indent) + "• ") else ""
                                    DropdownMenuItem(
                                        text = prefix + name,
                                        textColor = textColor,
                                        onClick = {
                                            selectedFolderId = id
                                            closeMenu()
                                        },
                                        enabled = true
                                    )
                                }

                                @Composable
                                fun traverse(parentId: Long?, indent: Int) {
                                    val children = childrenByParent[parentId].orEmpty()
                                        .sortedBy { it.name.lowercase() }
                                    for (child in children) {
                                        renderItem(indent, child.id, child.name)
                                        traverse(child.id, indent + 1)
                                    }
                                }

                                renderItem(0, null, "All Recordings")
                                traverse(parentId = null, indent = 0)
                            }
                        }

                        /* Commented out: Upload Audio File button
                        Spacer(modifier = Modifier.height(24.dp))

                        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                            if (uri != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val cachedDir = File(context.cacheDir, "uploads").apply { mkdirs() }
                                        val name = runCatching {
                                            val c = context.contentResolver.query(
                                                uri,
                                                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                                                null, null, null
                                            )
                                            c?.use { if (it.moveToFirst()) it.getString(0) else null }
                                        }.getOrNull() ?: ("upload_" + System.currentTimeMillis() + ".wav")

                                        val target = File(cachedDir, name)
                                        context.contentResolver.openInputStream(uri)?.use { ins ->
                                            target.outputStream().use { outs -> ins.copyTo(outs) }
                                        }

                                        withContext(Dispatchers.Main) {
                                            onAudioSourceChanged(AudioSource.File(target.absolutePath))
                                        }
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = { launcher.launch("audio") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                                    contentDescription = "Upload",
                                    modifier = Modifier.size(24.dp),
                                    tint = textColor
                                )
                                Text(
                                    text = "Upload Audio File",
                                    fontSize = 15.sp,
                                    color = textColor
                                )
                            }
                        } */

                    }
                }
            }

            // Bottom section - Record button
            Column(
                modifier = Modifier
                    .padding(bottom = 24.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    // Show Pause and Stop buttons when recording
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause/Resume button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    color = Color(0xFFFFF4E6),
                                    shape = CircleShape
                                )
                                .border(1.dp, Color(0xFFFFDDB8), CircleShape)
                                .clickable {
                                    if (recorderPaused) {
                                        recorder.resume()
                                        recorderPaused = false
                                    } else {
                                        recorder.pause()
                                        recorderPaused = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (recorderPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (recorderPaused) "Resume" else "Pause",
                                    tint = if (recorderPaused) Color(0xFFF98A2D) else Color(0xFFF98A2D),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Stop button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    color = Color(0xFFD32F2F),
                                    shape = CircleShape
                                )
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            recorder.stop()
                                            val rf = currentRawFile()
                                            if (rf != null && rf.exists() && rf.length() > 0) {
                                                val wf =
                                                    File(cacheDir, rf.nameWithoutExtension + ".wav")
                                                WavWriter16kMonoPcm16.wrapRawPcmToWav(rf, wf)
                                                withContext(Dispatchers.Main) {
                                                    wavFilePath = wf.absolutePath
                                                    isRecordedAudio = true
                                                    onAudioSourceChanged(AudioSource.File(wf.absolutePath))
                                                }
                                                rf.delete()
                                                rawFilePath = null
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    wavFilePath = null
                                                    isRecordedAudio = false
                                                    rawFilePath = null
                                                }
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) {
                                                isRecording = false
                                                recorderPaused = false
                                                onRecordingStateChanged(false)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                } else if (!showTranscriptionComplete && !transcriptionUi.isTranscribing) {
                    // Show red record button when not recording and not transcribing
                    val isDownloading = modelDownloadState.downloadingId != null || modelDownloadState.isLoadingModel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    color = if (isDownloading) Color.Gray else Color(0xFFFF3B30),
                                    shape = CircleShape
                                )
                                .then(
                                    if (!isDownloading) {
                                        Modifier.clickable {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPermission) {
                                                startRecording()
                                            } else {
                                                requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Empty - just red circle without icon
                        }

                        Text(
                            text = if (isDownloading) "Downloading model…" else "Start Recording",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDownloading) textColor.copy(alpha = 0.5f) else textColor
                        )
                    }
                }
            }

            /* Commented out: Old bottom section with separate transcribe/delete/playback controls
            This has been replaced with the new iOS-inspired workflow:
            - Auto-transcription on recording stop
            - State-based UI transitions
            - Simplified record button design
            */

            // Delete model dialog (keep existing)
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
                            "This will remove $modelName.",
                            color = textColor.copy(alpha = 0.85f)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteModelDialog = false
                            val fallbackSpec = ModelManager.defaultModel()
                            transcriptionViewModel.deleteModel(context, selectedModel)
                            if (selectedModel != null && fallbackSpec != null) {
                                val fallbackOption = ModelOption(
                                    fallbackSpec.id,
                                    fallbackSpec.fileName,
                                    fallbackSpec.url
                                )
                                onSelectModel(fallbackOption)
                                modelDownloadVm.setSelectedModel(fallbackOption)
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

            // Cancel transcription confirmation dialog
            if (showCancelTranscriptionDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelTranscriptionDialog = false },
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = textColor,
                    title = {
                        Text("Cancel transcription?", color = textColor, fontWeight = FontWeight.SemiBold)
                    },
                    text = {
                        Text(
                            "Are you sure you want to cancel the current transcription? The recording will be saved but not transcribed.",
                            color = textColor.copy(alpha = 0.85f)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showCancelTranscriptionDialog = false
                            if (transcriptionUi.isTranscribing) {
                                transcriptionViewModel.cancelTranscription()
                            }
                            showTranscriptionComplete = false
                            isRecordedAudio = false
                            currentWavFile()?.delete()
                            wavFilePath = null
                            rawFilePath = null
                            transcriptionName = generateDefaultTranscriptionName()
                            releasePlayer()
                            clearVisualState()
                        }) {
                            Text("Cancel Transcription", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelTranscriptionDialog = false }) {
                            Text("Continue")
                        }
                    }
                )
            }

            // Cancel download confirmation dialog
            if (showCancelDownloadDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelDownloadDialog = false },
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = textColor,
                    title = {
                        Text("Cancel download?", color = textColor, fontWeight = FontWeight.SemiBold)
                    },
                    text = {
                        Text(
                            "Are you sure you want to cancel the model download?",
                            color = textColor.copy(alpha = 0.85f)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showCancelDownloadDialog = false
                            modelDownloadVm.cancelDownload()
                        }) {
                            Text("Cancel Download", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelDownloadDialog = false }) {
                            Text("Continue")
                        }
                    }
                )
            }
        }
    }
}