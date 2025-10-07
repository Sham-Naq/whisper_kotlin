package com.example.whisper_kotlin

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.lifecycle.viewmodel.compose.viewModel

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
    selectedModel: ModelOption? = null,
    audioSource: AudioSource = AudioSource.Asset("samples/samples_jfk.wav"),
    isModelDownloading: Boolean = false,
    viewModel: TranscriptionViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier) {
        // Actions row (no explicit load button; model is loaded on selection or lazily here)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                label = "Transcribe",
                color = textColor,
                enabled = !uiState.isTranscribing && !isModelDownloading
            ) {
                viewModel.startTranscription(
                    context = ctx,
                    selectedModel = selectedModel,
                    audioSource = audioSource,
                    isModelDownloading = isModelDownloading
                )
            }
            ActionButton(
                label = "Delete model",
                color = textColor,
                enabled = !uiState.isTranscribing
            ) {
                viewModel.deleteModel(ctx, selectedModel)
            }
        }
        Spacer(Modifier.height(8.dp))
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

        BasicText(text = uiState.log, style = TextStyle(color = textColor))
    }
}