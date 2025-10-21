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
import androidx.compose.runtime.*
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
    var isRecording by remember { mutableStateOf(false) }
    var rawFile by remember { mutableStateOf<File?>(null) }
    var wavFile by remember { mutableStateOf<File?>(null) }
    var showDeleteModelDialog by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackPositionMs by remember { mutableStateOf(0) }
    var playbackDurationMs by remember { mutableStateOf(0) }

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
        wavFile?.let { file -> file.delete() }
        val rf = File(cacheDir, "rec_${'$'}{System.currentTimeMillis()}.pcm")
        rawFile = rf
        recorder.start(rf)
        isRecording = true
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

    DisposableEffect(wavFile) {
        val file = wavFile
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
        playbackDurationMs = player?.duration ?: 0
        playbackProgress = 0f
        playbackPositionMs = 0
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
            RecorderCommand.Start -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    startRecording()
                } else {
                    requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    return@LaunchedEffect
                }
                onCommandHandled()
            }
            RecorderCommand.Pause -> {
                if (isRecording) {
                    recorder.pause()
                }
                onCommandHandled()
            }
            RecorderCommand.Resume -> {
                if (isRecording) {
                    recorder.resume()
                }
                onCommandHandled()
            }
            RecorderCommand.Stop -> {
                if (isRecording) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            recorder.stop()
                            val rf = rawFile
                            if (rf != null && rf.exists() && rf.length() > 0) {
                                val wf = File(cacheDir, rf.nameWithoutExtension + ".wav")
                                WavWriter16kMonoPcm16.wrapRawPcmToWav(rf, wf)
                                withContext(Dispatchers.Main) {
                                    wavFile = wf
                                    onAudioSourceChanged(AudioSource.File(wf.absolutePath))
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    wavFile = null
                                }
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                isRecording = false
                                onRecordingStateChanged(false)
                            }
                        }
                    }
                }
                onCommandHandled()
            }
            null -> Unit
        }
    }

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val canPlayRecording = wavFile != null && mediaPlayer != null && playbackDurationMs > 0
    val canTranscribe = !isRecording && !transcriptionUi.isTranscribing && (!isModelDownloading)

    fun generateDefaultTranscriptionName(): String {
        val pattern = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())
        return pattern.format(Date())
    }

    var transcriptionName by rememberSaveable { mutableStateOf(generateDefaultTranscriptionName()) }

    val selectorButtonBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8EAF6)

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(focusManager) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(16.dp)
    ) {
        val dialogBackground = remember(textColor) {
            if (textColor.luminance() > 0.5f) Color(0xFF1E1E1E) else Color.White
        }
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
                    .clickable {
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

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = isRecording,
            modifier = Modifier.fillMaxWidth(),
            enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                expandVertically(
                    expandFrom = Alignment.CenterVertically,
                    animationSpec = tween(durationMillis = 320)
                ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.CenterVertically,
                animationSpec = tween(durationMillis = 240)
            ) + fadeOut(animationSpec = tween(durationMillis = 180))
        ) {
            LineBarWaveform(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(if (isDark) Color(0xFF181818) else Color(0xFFF0F0F0)),
                bars = bars,
                color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1E88E5),
                backgroundColor = null
            )
        }

        if (!isRecording) {
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
                                    .background(Color(0xFF424242), CircleShape)
                                    .clickable(enabled = canPlayRecording) {
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
                            Spacer(modifier = Modifier.width(16.dp))
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
                        buttonBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8EAF6),
                        source = audioSource,
                        onSourceChanged = onAudioSourceChanged
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                label = if (wavFile != null) "Transcribe recording" else "Transcribe file",
                color = textColor,
                enabled = canTranscribe
            ) {
                val source = if (wavFile != null) {
                    AudioSource.File(wavFile!!.absolutePath)
                } else {
                    audioSource
                }
                transcriptionViewModel.startTranscription(
                    context = context,
                    selectedModel = selectedModel,
                    audioSource = source,
                    isModelDownloading = isModelDownloading,
                    transcriptionName = transcriptionName
                )
            }

            if (wavFile != null) {
                ActionButton(
                    label = "Delete recording",
                    color = textColor
                ) {
                    isPlaying = false
                    releasePlayer()
                    wavFile?.delete()
                    rawFile?.delete()
                    wavFile = null
                    rawFile = null
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

        Spacer(modifier = Modifier.height(12.dp))

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
                        Text("Delete", color = Color(0xFFE57373))
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
