package com.example.whisper_kotlin

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var playbackPositionMs by remember { mutableStateOf(0) }
    var playbackDurationMs by remember { mutableStateOf(0) }

    val audioFile = remember(entry.audioPath) {
        entry.audioPath?.let { File(it) }?.takeIf { it.exists() }
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

    val timestamp = remember(entry.timestamp) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    }
    val durationLabel = remember(entry.transcriptionDurationMs) {
        if (entry.transcriptionDurationMs >= 1000L) {
            String.format(Locale.getDefault(), "%.1f s", entry.transcriptionDurationMs / 1000f)
        } else {
            "${entry.transcriptionDurationMs} ms"
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(label = "Back", color = textColor) {
                onBack()
            }
            ActionButton(label = "Delete", color = textColor) {
                onDelete()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        BasicText(
            text = entry.fileLabel,
            style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text = "Model: ${entry.modelLabel}",
            style = TextStyle(color = textColor.copy(alpha = 0.75f))
        )
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text = timestamp,
            style = TextStyle(color = textColor.copy(alpha = 0.65f))
        )
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text = "Transcription time: $durationLabel (${entry.transcriptionDurationMs} ms)",
            style = TextStyle(color = textColor.copy(alpha = 0.65f))
        )

        Spacer(modifier = Modifier.height(20.dp))
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
                        valueRange = 0f..1f
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

        Spacer(modifier = Modifier.height(24.dp))
        BasicText(
            text = "Transcript",
            style = TextStyle(color = textColor, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = entry.transcript,
            style = TextStyle(color = textColor.copy(alpha = 0.85f))
        )
    }
}
