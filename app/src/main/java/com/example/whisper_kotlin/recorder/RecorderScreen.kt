package com.example.whisper_kotlin.recorder

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.whisper_kotlin.ActionButton
import com.example.whisper_kotlin.AudioSource
import com.example.whisper_kotlin.ModelOption
import com.example.whisper_kotlin.ModelSelectorRow
import com.example.whisper_kotlin.ModelDownloadViewModel
import com.example.whisper_kotlin.TranscriptionViewModel
import com.example.whisper_kotlin.FileSelectorRow
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.FolderSelectorRow
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
    var isRecordedAudio by rememberSaveable { mutableStateOf(false) }

    fun currentRawFile(): File? = rawFilePath?.let { File(it) }?.takeIf { it.exists() }
    fun currentWavFile(): File? = wavFilePath?.let { File(it) }?.takeIf { it.exists() }
    var showDeleteModelDialog by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var lastPlayerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var playbackProgress by rememberSaveable { mutableStateOf(0f) }
    var playbackPositionMs by rememberSaveable { mutableStateOf(0) }
    var playbackDurationMs by rememberSaveable { mutableStateOf(0) }
    var selectedFolderId by rememberSaveable { mutableStateOf<Long?>(null) }

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

    // Track transcription state
    var showTranscriptionComplete by remember { mutableStateOf(false) }

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
                    targetFolderId = selectedFolderId
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

    // Show transcription complete when transcription finishes
    LaunchedEffect(transcriptionUi.isTranscribing) {
        if (!transcriptionUi.isTranscribing && wavFilePath != null && !isRecording) {
            showTranscriptionComplete = true
        }
    }

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
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show different content based on state
            when {
                // Recording state: Show visualizer centered vertically
                isRecording -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (recorderPaused) {
                                Text(
                                    text = "Recording Paused",
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                LineBarWaveform(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    bars = bars,
                                    color = MaterialTheme.colorScheme.primary,
                                    backgroundColor = null
                                )
                            }
                        }
                    }
                }
                // Transcribing state: Centered Material3 indicator
                transcriptionUi.isTranscribing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(72.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "Transcribing...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    transcriptionViewModel.cancelTranscription()
                                    showTranscriptionComplete = false
                                    isRecordedAudio = false
                                    currentWavFile()?.delete()
                                    wavFilePath = null
                                    rawFilePath = null
                                    transcriptionName = generateDefaultTranscriptionName()
                                    releasePlayer()
                                },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                // Transcription complete state: Show success message
                showTranscriptionComplete -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = "Transcription Saved",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            androidx.compose.material3.Button(
                                onClick = {
                                    // Reset to default state
                                    showTranscriptionComplete = false
                                    wavFilePath = null
                                    rawFilePath = null
                                    isRecordedAudio = false
                                    transcriptionName = generateDefaultTranscriptionName()
                                    releasePlayer()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Finish",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                // Default state: Show logo, instructions, and options
                else -> {
                    // Logo and instructions
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Blue circular logo with mic icon
                        Box(
                            modifier = Modifier
                                .size(70.dp)
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

                    Spacer(modifier = Modifier.height(16.dp))

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
                        androidx.compose.material3.Divider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                            ReusableDropdown(
                                label = "",
                                selectedText = when {
                                    modelDownloadState.downloadingId != null -> "Downloading ${modelDownloadState.downloadingId}… ${modelDownloadState.progressPct}%"
                                    modelDownloadState.selectedModel != null -> "${modelDownloadState.selectedModel!!.id}"
                                    selectedModel != null -> "${selectedModel.id}"
                                    else -> "tiny"
                                },
                                textColor = textColor,
                                isDark = isDark,
                                modifier = Modifier.weight(1f),
                                enabled = modelDownloadState.downloadingId == null
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
                                                    imageVector = androidx.compose.material.icons.Icons.Filled.AccessTime,
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

                        // Divider
                        androidx.compose.material3.Divider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Home,
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
                                .size(70.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                )
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
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Stop button
                        Box(
                            modifier = Modifier
                                .size(70.dp)
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = Color(0xFFFF3B30),
                                    shape = CircleShape
                                )
                                .clickable {
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
                            // Empty - just red circle without icon
                        }

                        Text(
                            text = "Start Recording",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }

                // Status message
                transcriptionUi.statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
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
        }
    }
}

