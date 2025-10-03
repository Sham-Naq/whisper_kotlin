package com.example.whisper_kotlin

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

// Project symbols used below
// Not strictly required if in the same package, but safe to keep explicit
// import com.example.whisper_kotlin.ModelManager
// import com.example.whisper_kotlin.WhisperEngine



@Composable
fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
    isModelDownloading: Boolean = false
) {
    val ctx = LocalContext.current
    var log by remember { mutableStateOf("No transcriptions made\n") }
    val scope = rememberCoroutineScope()

    fun append(s: String) { log += s + "\n" }

    Column(modifier = modifier) {
        // Actions row (no explicit load button; model is loaded on selection or lazily here)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Transcribe", textColor) {
                scope.launch(Dispatchers.IO) {
                    try {
                        if (isModelDownloading) {
                            append("Please wait for model download to finish…")
                            return@launch
                        }
                        // Ensure a model is loaded: prefer selected downloaded model, otherwise fallback to bundled asset
                        val chosen = selectedModel
                        val local = chosen?.let { opt ->
                            val f = java.io.File(java.io.File(ctx.filesDir, "models"), opt.fileName)
                            if (f.exists() && f.length() > 0L) f else null
                        }
                        val modelLabel = if (local != null) {
                            // Force reload in case a previous context is active for a different model
                            WhisperEngine.loadModelFromFile(local.absolutePath, force = true)
                            chosen?.id ?: local.name
                        } else {
                            WhisperEngine.loadModelFromAssets(ctx, "models/ggml-tiny-q5_1.bin", force = true)
                            "ggml-tiny-q5_1 (asset)"
                        }
                        val startNs = android.os.SystemClock.elapsedRealtimeNanos()
                        val text = when (val src = audioSource) {
                            is AudioSource.Asset -> WhisperEngine.transcribeWavAsset(ctx, src.assetPath)
                            is AudioSource.File -> WhisperEngine.transcribeWavFile(src.path)
                        }
                        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
                        val timeLine = "Completed in " + String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0) + " s (" + elapsedMs.toLong() + " ms)"
                        val fileLabel = when (val src = audioSource) {
                            is AudioSource.Asset -> src.assetPath.substringAfterLast('/')
                            is AudioSource.File -> java.io.File(src.path).name
                        }
                        append("File: $fileLabel\nModel: $modelLabel\nTranscript:\n$text\n$timeLine")
                    } catch (t: Throwable) {
                        append("Transcribe failed: ${'$'}t")
                    }
                }
            }
            ActionButton("Delete model", textColor) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val chosen = selectedModel
                        if (chosen == null) {
                            append("No model selected to delete.")
                            return@launch
                        }
                        val file = ModelManager.getLocalModelFile(ctx, chosen.fileName)
                        if (file.exists()) {
                            val ok = file.delete()
                            WhisperEngine.reset()
                            append(if (ok) "Deleted model: ${'$'}{file.name}" else "Failed to delete: ${'$'}{file.name}")
                        } else {
                            append("Model not found locally: ${'$'}{chosen.fileName}")
                        }
                    } catch (t: Throwable) {
                        append("Delete failed: ${'$'}t")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(text = log, style = TextStyle(color = textColor))
    }
}