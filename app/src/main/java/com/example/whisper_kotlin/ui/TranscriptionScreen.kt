package com.example.whisper_kotlin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Project symbols used below
// Not strictly required if in the same package, but safe to keep explicit
// import com.example.whisper_kotlin.ModelManager
// import com.example.whisper_kotlin.WhisperEngine



@Composable
fun ActionButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = color, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun TranscriptionScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1),
    viewModel: TranscriptionViewModel = viewModel(),
    onOpenTranscription: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<SavedTranscription?>(null) }
    Column(modifier = modifier) {
        val progress = uiState.progress
        val statusMessage = uiState.statusMessage

        if (uiState.isTranscribing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                progress?.let {
                    LinearProgressIndicator(progress = it, modifier = Modifier.fillMaxWidth())
                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                BasicText(
                    text = statusMessage ?: "Transcribing…",
                    style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (statusMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = statusMessage,
                style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val saved = uiState.savedTranscriptions
        Spacer(modifier = Modifier.height(8.dp))
        if (saved.isEmpty()) {
            BasicText(
                text = "No saved transcriptions yet. Record or select audio from the Recorder tab to create one.",
                style = TextStyle(color = textColor.copy(alpha = 0.8f))
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(saved, key = { it.id }) { entry ->
                    val timestamp = remember(entry.timestamp) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                    }
                    val durationLabel = remember(entry.transcriptionDurationMs) {
                        if (entry.transcriptionDurationMs >= 1000L) {
                            String.format(Locale.getDefault(), "%.1f s", entry.transcriptionDurationMs / 1000f)
                        } else {
                            "${entry.transcriptionDurationMs} ms"
                        }
                    }
                    val summary = remember(entry.transcript) {
                        entry.transcript
                            .lineSequence()
                            .firstOrNull()
                            ?.take(160)
                            ?.let { if (entry.transcript.length > 160) "$it…" else it }
                            ?: "Tap to view transcript"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { onOpenTranscription(entry.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = true)) {
                                BasicText(
                                    text = entry.fileLabel,
                                    style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                BasicText(
                                    text = "Model: ${entry.modelLabel}",
                                    style = TextStyle(color = textColor.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = "$timestamp",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                BasicText(
                                    text = "Transcription time: $durationLabel",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                )
                            }
                            IconButton(
                                onClick = {
                                    pendingDelete = entry
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete transcription",
                                    tint = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        BasicText(
                            text = summary,
                            style = TextStyle(color = textColor.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }

        val entryToDelete = pendingDelete
        if (entryToDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = {
                    Text(
                        text = "Delete transcription?",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "This will permanently remove ${entryToDelete.fileLabel}.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteTranscription(context, entryToDelete.id)
                        pendingDelete = null
                    }) {
                        Text("Delete", color = Color(0xFFE57373))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}