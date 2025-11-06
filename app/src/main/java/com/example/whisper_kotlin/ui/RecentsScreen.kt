package com.example.whisper_kotlin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentsScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    viewModel: TranscriptionViewModel,
    onOpenTranscription: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val now = System.currentTimeMillis()
    val fiveDaysMs = 5L * 24 * 60 * 60 * 1000

    val recent = remember(uiState.savedTranscriptions) {
        uiState.savedTranscriptions
            .asSequence()
            .filter { it.timestamp >= now - fiveDaysMs }
            .sortedByDescending { it.timestamp }
            .take(10)
            .toList()
    }

    val cardBackground = MaterialTheme.colorScheme.surfaceContainer
    val cardBorderColor = MaterialTheme.colorScheme.outlineVariant

    if (recent.isEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = "No recent transcriptions in the last 5 days.",
                color = textColor.copy(alpha = 0.8f)
            )
        }
        return
    }

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recent, key = { it.id }) { entry ->
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = textColor.copy(alpha = 0.2f))
                        ) { onOpenTranscription(entry.id) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    elevation = 0.dp,
                    backgroundColor = cardBackground,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = true)) {
                                androidx.compose.foundation.text.BasicText(
                                    text = entry.fileLabel,
                                    style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = "Model: ${entry.modelLabel}",
                                    style = TextStyle(color = textColor.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = "$timestamp",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                androidx.compose.foundation.text.BasicText(
                                    text = "Transcription time: $durationLabel",
                                    style = TextStyle(color = textColor.copy(alpha = 0.6f))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.text.BasicText(
                            text = summary,
                            style = TextStyle(color = textColor.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }
    }
}
