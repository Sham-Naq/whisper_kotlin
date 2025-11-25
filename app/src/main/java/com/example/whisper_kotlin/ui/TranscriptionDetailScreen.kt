package com.example.whisper_kotlin

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import com.example.whisper_kotlin.ModelDownloadViewModel
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.ModelOption
import com.example.whisper_kotlin.ModelSelectorRow
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranscriptionDetailScreen(
    entry: SavedTranscription,
    textColor: Color,
    modifier: Modifier = Modifier,
    viewModel: TranscriptionViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentEntry = uiState.savedTranscriptions.firstOrNull { it.id == entry.id } ?: entry
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showTimestamps by remember(currentEntry.id) { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val selectorButtonBg = MaterialTheme.colorScheme.surfaceVariant
    val dialogBackground = MaterialTheme.colorScheme.surface
    val modelDownloadViewModel: ModelDownloadViewModel = viewModel()
    val modelDownloadState by modelDownloadViewModel.uiState.collectAsState()
    var showReTranscribeDialog by remember { mutableStateOf(false) }
    var reTranscribeSelectedModel by remember { mutableStateOf<ModelOption?>(null) }
    val entryModelOption = remember(currentEntry.modelLabel) {
        val label = currentEntry.modelLabel
        val direct = ModelManager.findModel(label)
        val compact = if (direct == null) ModelManager.findModel(label.substringBefore(' ')) else null
        (direct ?: compact)?.let { spec -> ModelOption(spec.id, spec.fileName, spec.url) }
    }

    LaunchedEffect(currentEntry.id) {
        reTranscribeSelectedModel = entryModelOption
        showReTranscribeDialog = false
    }

    LaunchedEffect(modelDownloadState.selectedModel) {
        modelDownloadState.selectedModel?.let { selected ->
            reTranscribeSelectedModel = selected
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackPositionMs by remember { mutableStateOf(0) }
    var playbackDurationMs by remember { mutableStateOf(0) }

    val audioFile = remember(currentEntry.audioPath) {
        currentEntry.audioPath?.let { File(it) }?.takeIf { it.exists() }
    }

    DisposableEffect(audioFile) {
        val player = audioFile?.let {
            try {
                MediaPlayer().apply {
                    setDataSource(it.absolutePath)
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

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    val timestamp = remember(currentEntry.timestamp) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(currentEntry.timestamp))
    }
    val durationLabel = remember(currentEntry.transcriptionDurationMs) {
        if (currentEntry.transcriptionDurationMs >= 1000L) {
            String.format(Locale.getDefault(), "%.1f s", currentEntry.transcriptionDurationMs / 1000f)
        } else {
            "${currentEntry.transcriptionDurationMs} ms"
        }
    }

    // Define card colors inspired by iOS styling
    val pageBackground = MaterialTheme.colorScheme.background
    val subtleCardBackground = pageBackground
    val transcriptCardBackground = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    val cornerRadius = 16.dp
    val emphasizedCornerRadius = cornerRadius + 12.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Metadata overview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(emphasizedCornerRadius),
                backgroundColor = subtleCardBackground,
                elevation = 0.dp,
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicText(
                        text = currentEntry.fileLabel,
                        style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                    )
                    BasicText(
                        text = "Model: ${currentEntry.modelLabel}",
                        style = TextStyle(color = textColor.copy(alpha = 0.75f))
                    )
                    BasicText(
                        text = timestamp,
                        style = TextStyle(color = textColor.copy(alpha = 0.65f))
                    )
                    BasicText(
                        text = "Transcription time: $durationLabel (${currentEntry.transcriptionDurationMs} ms)",
                        style = TextStyle(color = textColor.copy(alpha = 0.65f))
                    )
                }
            }

            val transcriptBody = when {
                showTimestamps -> currentEntry.timestampedTranscript?.let(::normalizeTimestampTranscript) ?: "Generating timestamps…"
                else -> currentEntry.transcript
            }
            val bodyStyle = TextStyle(color = textColor.copy(alpha = 0.85f))
            val annotatedTranscript = remember(transcriptBody, showTimestamps, textColor) {
                if (!showTimestamps) {
                    AnnotatedString(transcriptBody)
                } else {
                    val timestampStyle = SpanStyle(color = textColor.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    val lines = transcriptBody.split('\n')
                    buildAnnotatedString {
                        lines.forEachIndexed { index, line ->
                            if (line.isNotEmpty() && line.startsWith('(') && line.indexOf(')') > 0) {
                                val endIndex = line.indexOf(')')
                                withStyle(timestampStyle) {
                                    append(line.substring(0, endIndex + 1))
                                }
                                append(line.substring(endIndex + 1))
                            } else {
                                append(line)
                            }
                            if (index < lines.size - 1) {
                                append('\n')
                            }
                        }
                    }
                }
            }
            val timestampAvailable = !currentEntry.timestampedTranscript.isNullOrBlank() || audioFile != null

            // Card 2: Transcription content + actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                shape = RoundedCornerShape(emphasizedCornerRadius),
                backgroundColor = transcriptCardBackground,
                elevation = 4.dp,
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val wordCount = remember(transcriptBody) {
                                transcriptBody.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            }
                            val charCount = remember(transcriptBody) { transcriptBody.length }
                            Column(
                                modifier = Modifier.weight(1f, fill = true)
                            ) {
                                BasicText(
                                    text = "${wordCount} words • ${charCount} chars",
                                    style = TextStyle(color = textColor.copy(alpha = 0.7f), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DetailActionIcon(
                                    icon = Icons.Filled.Refresh,
                                    tint = Color(0xFFFFA726),
                                    background = Color(0xFFFFA726),
                                    contentDescription = "Re-transcribe",
                                    enabled = !uiState.isTranscribing && currentEntry.audioPath != null,
                                    onClick = {
                                        if (reTranscribeSelectedModel == null) {
                                            reTranscribeSelectedModel = modelDownloadState.selectedModel ?: entryModelOption
                                        }
                                        showReTranscribeDialog = true
                                    }
                                )
                                DetailActionIcon(
                                    icon = Icons.Filled.AccessTime,
                                    tint = Color(0xFFBA68C8),
                                    background = Color(0xFFBA68C8),
                                    contentDescription = if (showTimestamps) "Hide timestamps" else "Show with timestamps",
                                    strikeThrough = showTimestamps,
                                    enabled = !uiState.isTranscribing && timestampAvailable,
                                    onClick = {
                                        if (showTimestamps) {
                                            showTimestamps = false
                                        } else {
                                            val existing = currentEntry.timestampedTranscript
                                            if (!existing.isNullOrBlank()) {
                                                showTimestamps = true
                                            } else {
                                                val hasAudio = audioFile != null
                                                if (hasAudio) {
                                                    showTimestamps = true
                                                    viewModel.ensureTimestampedTranscript(context, currentEntry.id)
                                                } else {
                                                    Toast.makeText(context, "Original audio missing", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                                DetailActionIcon(
                                    icon = Icons.Filled.ContentCopy,
                                    tint = Color(0xFF64B5F6),
                                    background = Color(0xFF64B5F6),
                                    contentDescription = "Copy transcript",
                                    onClick = {
                                        val textToCopy = if (showTimestamps) {
                                            currentEntry.timestampedTranscript?.let(::normalizeTimestampTranscript) ?: currentEntry.transcript
                                        } else {
                                            currentEntry.transcript
                                        }
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    item {
                        BasicText(
                            text = annotatedTranscript,
                            style = bodyStyle
                        )
                    }
                }
            }

            // Card 3: Audio seekbar pinned to bottom
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(emphasizedCornerRadius),
                backgroundColor = subtleCardBackground,
                elevation = 0.dp,
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (audioFile != null && mediaPlayer != null && playbackDurationMs > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = {
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
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = textColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f, fill = true)) {
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
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    BasicText(
                                        text = formatTime(playbackPositionMs),
                                        style = TextStyle(color = textColor.copy(alpha = 0.75f))
                                    )
                                    BasicText(
                                        text = formatTime(playbackDurationMs),
                                        style = TextStyle(color = textColor.copy(alpha = 0.75f))
                                    )
                                }
                            }
                        }
                    } else {
                        BasicText(
                            text = "Audio preview unavailable",
                            style = TextStyle(color = textColor.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    
    }


        if (showReTranscribeDialog) {
        AlertDialog(
            onDismissRequest = { showReTranscribeDialog = false },
            backgroundColor = dialogBackground,
            contentColor = textColor,
            title = { Text("Re-transcribe recording", color = textColor, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select a model and confirm to re-run Whisper on this audio.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                    ModelSelectorRow(
                        textColor = textColor,
                        isDark = isDark,
                        buttonBg = selectorButtonBg,
                        selectedModel = reTranscribeSelectedModel ?: modelDownloadState.selectedModel,
                        downloadingId = modelDownloadState.downloadingId,
                        progressPct = modelDownloadState.progressPct,
                        onSelectModel = { opt ->
                            reTranscribeSelectedModel = opt
                            modelDownloadViewModel.selectIfPresent(context, opt)
                        },
                        onRequestDownload = { opt ->
                            modelDownloadViewModel.startDownloadOrSelect(context, opt)
                        },
                        onCancelDownload = { modelDownloadViewModel.cancelDownload() }
                    )
                    modelDownloadState.message?.let { msg ->
                        Text(msg, color = textColor.copy(alpha = 0.75f))
                    }
                }
            },
            confirmButton = {
                val isDownloading = modelDownloadState.downloadingId != null
                TextButton(
                    enabled = !isDownloading && !uiState.isTranscribing,
                    onClick = {
                        val chosen = reTranscribeSelectedModel ?: modelDownloadState.selectedModel
                        showReTranscribeDialog = false
                        viewModel.reTranscribeEntry(context, currentEntry.id, selectedModel = chosen)
                        Toast.makeText(context, "Re-transcribing…", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (modelDownloadState.downloadingId != null) "Downloading…" else "Re-transcribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReTranscribeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
        }
    }


@Composable
private fun DetailActionIcon(
    icon: ImageVector,
    tint: Color,
    background: Color,
    contentDescription: String,
    strikeThrough: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background.copy(alpha = 0.18f))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        if (strikeThrough) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.12f
                drawLine(
                    color = tint,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun normalizeTimestampTranscript(raw: String): String {
    if (!raw.contains('[')) return raw
    val lines = raw.split('\n')
    val converted = lines.map { convertLegacyTimestampLine(it) }
    val joined = converted.joinToString("\n")
    return if (raw.endsWith('\n')) joined + "\n" else joined
}

private fun convertLegacyTimestampLine(line: String): String {
    if (!line.startsWith("[")) return line
    val closingIndex = line.indexOf("]: ")
    if (closingIndex <= 0) return line
    val header = line.substring(1, closingIndex)
    val startToken = header.substringBefore(" -->").trim()
    val concise = conciseTimestampToken(startToken)
    val remainder = line.substring(closingIndex + 3).trimStart()
    return if (remainder.isEmpty()) "(${concise})" else "(${concise}) $remainder"
}

private fun conciseTimestampToken(token: String): String {
    if (token.isBlank()) return token
    val parts = token.split(':')
    if (parts.size < 3) return token
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val seconds = parts.getOrNull(2)?.substringBefore('.')?.toIntOrNull() ?: 0
    val totalSeconds = hours * 3600 + minutes * 60 + seconds
    val outHours = totalSeconds / 3600
    val outMinutes = (totalSeconds % 3600) / 60
    val outSeconds = totalSeconds % 60
    return if (outHours > 0) {
        "${outHours}:${outMinutes.toString().padStart(2, '0')}:${outSeconds.toString().padStart(2, '0')}"
    } else {
        "${outMinutes}:${outSeconds.toString().padStart(2, '0')}"
    }
}
