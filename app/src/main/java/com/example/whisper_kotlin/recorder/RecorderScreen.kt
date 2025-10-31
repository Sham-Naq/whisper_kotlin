package com.example.whisper_kotlin.recorder

import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.whisper_kotlin.ActionButton
import com.example.whisper_kotlin.AudioSource
import com.example.whisper_kotlin.ModelOption
import com.example.whisper_kotlin.ModelSelectorRow
import com.example.whisper_kotlin.ModelDownloadViewModel
import com.example.whisper_kotlin.TranscriptionViewModel
import com.example.whisper_kotlin.FileSelectorRow
import com.example.whisper_kotlin.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme

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
    command: RecorderCommand? = null,
    onCommandHandled: () -> Unit = {},
    onRecordingStateChanged: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(scope = scope) }
    val focusManager = LocalFocusManager.current
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var recorderPaused by rememberSaveable { mutableStateOf(false) }
    var rawFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var wavFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var isRecordedAudio by rememberSaveable { mutableStateOf(false) } // Track if audio was recorded vs uploaded

    fun currentRawFile(): File? = rawFilePath?.let { File(it) }?.takeIf { it.exists() }
    fun currentWavFile(): File? = wavFilePath?.let { File(it) }?.takeIf { it.exists() }
    var showDeleteModelDialog by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var lastPlayerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var playbackProgress by rememberSaveable { mutableStateOf(0f) }
    var playbackPositionMs by rememberSaveable { mutableStateOf(0) }
    var playbackDurationMs by rememberSaveable { mutableStateOf(0) }

    val transcriptionUi by transcriptionViewModel.uiState.collectAsState()
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

    val startRecording: () -> Unit = {
        releasePlayer()
        currentWavFile()?.delete()
        wavFilePath = null
        isRecordedAudio = false
        currentRawFile()?.delete()
        val newRaw = File(cacheDir, "rec_${'$'}{System.currentTimeMillis()}.pcm")
        rawFilePath = newRaw.absolutePath
        recorder.start(newRaw)
        isRecording = true
        recorderPaused = false
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
                playbackProgress = (playbackPositionMs.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
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
                                    isRecordedAudio = true // Mark as recorded audio
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
            // Only update wavFilePath if it's NOT from an uploaded file
            // If audioSource changes to a different file, it means user uploaded a new file
            // so we should clear the wavFilePath (recorded audio) and mark as not recorded
            if (normalized != null && wavFilePath != normalized) {
                // This is an uploaded file, not a recorded one
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

    val canPlayRecording = isRecordedAudio && currentWavFile() != null && mediaPlayer != null && playbackDurationMs > 0
    val canTranscribe = !isRecording && !transcriptionUi.isTranscribing && (!isModelDownloading)

    fun generateDefaultTranscriptionName(): String {
        val pattern = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())
        return pattern.format(Date())
    }

    var transcriptionName by rememberSaveable { mutableStateOf(generateDefaultTranscriptionName()) }

    val selectorButtonBg = MaterialTheme.colorScheme.surfaceVariant

    val modelDownloadVm: ModelDownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val modelDownloadState by modelDownloadVm.uiState.collectAsState()

    LaunchedEffect(modelDownloadState.downloadingId) {
        onModelDownloadingChanged(modelDownloadState.downloadingId != null)
    }

    LaunchedEffect(selectedModel) {
        // Keep external selection in sync when coming back
        if (selectedModel != null && modelDownloadState.selectedModel?.id != selectedModel.id) {
            modelDownloadVm.selectIfPresent(context, selectedModel)
        }
    }

    // Update parent's selectedModel when download completes
    LaunchedEffect(modelDownloadState.selectedModel) {
        val downloadedModel = modelDownloadState.selectedModel
        if (downloadedModel != null && downloadedModel.id != selectedModel?.id) {
            onSelectModel(downloadedModel)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(focusManager) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(16.dp)
    ) {
        val dialogBackground = MaterialTheme.colorScheme.surface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(selectorButtonBg, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.Gray.copy(alpha = 0.3f))
                    ) {
                        transcriptionName = generateDefaultTranscriptionName()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = "Name",
                    style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                )
            }
            OutlinedTextField(
                value = transcriptionName,
                onValueChange = { transcriptionName = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = textColor)
            )
        }

        ModelSelectorRow(
            textColor = textColor,
            isDark = isDark,
            buttonBg = selectorButtonBg,
            selectedModel = modelDownloadState.selectedModel ?: selectedModel,
            downloadingId = modelDownloadState.downloadingId,
            progressPct = modelDownloadState.progressPct,
            onSelectModel = {
                onSelectModel(it)
                modelDownloadVm.selectIfPresent(context, it)
            },
            onRequestDownload = { opt ->
                onModelDownloadingChanged(true)
                modelDownloadVm.startDownloadOrSelect(context, opt)
            },
            onCancelDownload = {
                modelDownloadVm.cancelDownload()
                onModelDownloadingChanged(false)
            }
        )

        if (isRecording) {
            Spacer(modifier = Modifier.height(8.dp))
            LineBarWaveform(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                bars = bars,
                color = MaterialTheme.colorScheme.primary,
                backgroundColor = null
            )
        }

        if (!isRecording) {
            Spacer(modifier = Modifier.height(16.dp))
            when {
                canPlayRecording -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, textColor.copy(alpha = 0.2f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .clickable(
                                        enabled = canPlayRecording,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f), bounded = true)
                                    ) {
                                        val player = mediaPlayer
                                        if (player != null) {
                                            if (isPlaying) {
                                                player.pause()
                                                isPlaying = false
                                            } else {
                                                player.start()
                                                isPlaying = true
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause playback" else "Play recording",
                                    tint = Color.White
                                )
                            }
                            //Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = playbackProgress,
                                    onValueChange = { value ->
                                        playbackProgress = value
                                        val player = mediaPlayer
                                        if (player != null && playbackDurationMs > 0) {
                                            val target = (value * playbackDurationMs).toInt().coerceIn(0, playbackDurationMs)
                                            player.seekTo(target)
                                            playbackPositionMs = target
                                        }
                                    },
                                    valueRange = 0f..1f,
                                    enabled = canPlayRecording
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = formatTime(playbackPositionMs),
                                        style = TextStyle(color = textColor.copy(alpha = 0.75f))
                                    )
                                    androidx.compose.foundation.text.BasicText(
                                        text = formatTime(playbackDurationMs),
                                        style = TextStyle(color = textColor.copy(alpha = 0.75f))
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    FileSelectorRow(
                        textColor = textColor,
                        isDark = isDark,
                        buttonBg = MaterialTheme.colorScheme.surfaceVariant,
                        source = audioSource,
                        onSourceChanged = onAudioSourceChanged
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val recordedFile = currentWavFile()

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                label = if (recordedFile != null) "Transcribe recording" else "Transcribe file",
                color = textColor,
                enabled = canTranscribe
            ) {
                val source = if (recordedFile != null) {
                    AudioSource.File(recordedFile.absolutePath)
                } else {
                    audioSource
                }
                // Use the downloaded model if available, otherwise fall back to selectedModel
                val modelToUse = modelDownloadState.selectedModel ?: selectedModel
                transcriptionViewModel.startTranscription(
                    context = context,
                    selectedModel = modelToUse,
                    audioSource = source,
                    isModelDownloading = isModelDownloading,
                    transcriptionName = transcriptionName
                )
            }

            if (recordedFile != null) {
                ActionButton(
                    label = "Delete recording",
                    color = textColor
                ) {
                    isPlaying = false
                    releasePlayer()
                    recordedFile.delete()
                    currentRawFile()?.delete()
                    wavFilePath = null
                    rawFilePath = null
                    isRecordedAudio = false // Reset the flag when deleting recording
                    onAudioSourceChanged(AudioSource.Asset("samples/samples_jfk.wav"))
                }
            }

            ActionButton(
                label = "Delete model",
                color = textColor,
                enabled = !transcriptionUi.isTranscribing && selectedModel != null
            ) {
                showDeleteModelDialog = true
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recording buttons - fixed position
        // Shows red record button when not recording, pause/stop buttons when recording
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                // Pause/Resume button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
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
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    androidx.compose.foundation.text.BasicText(
                        text = if (recorderPaused) "Resume" else "Pause",
                        style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Stop button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        recorder.stop()
                                        val rf = currentRawFile()
                                        if (rf != null && rf.exists() && rf.length() > 0) {
                                            val wf = File(cacheDir, rf.nameWithoutExtension + ".wav")
                                            WavWriter16kMonoPcm16.wrapRawPcmToWav(rf, wf)
                                            withContext(Dispatchers.Main) {
                                                wavFilePath = wf.absolutePath
                                                isRecordedAudio = true // Mark as recorded audio
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
                                .size(20.dp)
                                .background(Color.White, RoundedCornerShape(3.dp))
                        )
                    }
                    androidx.compose.foundation.text.BasicText(
                        text = "Stop",
                        style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                    )
                }
            } else {
                // Record button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFDC143C), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    startRecording()
                                } else {
                                    requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Empty content - button is just a colored circle
                    }
                    androidx.compose.foundation.text.BasicText(
                        text = "Record",
                        style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showDeleteModelDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteModelDialog = false },
                backgroundColor = dialogBackground,
                contentColor = textColor,
                title = {
                    Text("Delete model?", color = textColor, fontWeight = FontWeight.SemiBold)
                },
                text = {
                    val modelName = selectedModel?.id ?: "this model"
                    Text(
                        "This will remove $modelName from local storage. You'll need to download it again to use it later.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteModelDialog = false
                        val fallbackSpec = ModelManager.defaultModel()
                        transcriptionViewModel.deleteModel(context, selectedModel)
                        if (selectedModel != null && fallbackSpec != null) {
                            val fallbackOption = ModelOption(fallbackSpec.id, fallbackSpec.fileName, fallbackSpec.url)
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

        transcriptionUi.statusMessage?.let { message ->
            androidx.compose.foundation.text.BasicText(
                text = message,
                style = TextStyle(color = textColor.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
            )
        }
    }
}


