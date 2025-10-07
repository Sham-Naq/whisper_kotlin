package com.example.whisper_kotlin

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snapshot of the current transcription UI. */
data class TranscriptionUiState(
    val log: String = "No transcriptions made\n",
    val isTranscribing: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float? = null
)

class TranscriptionViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    /** Append a log line, trimming the placeholder message when the first real line arrives. */
    private fun appendLog(line: String) {
        _uiState.update { current ->
            val base = if (current.log == "No transcriptions made\n") "" else current.log
            current.copy(log = base + line + "\n")
        }
    }

    fun startTranscription(
        context: Context,
        selectedModel: ModelOption?,
        audioSource: AudioSource,
        isModelDownloading: Boolean
    ) {
        val appContext = context.applicationContext
        if (isModelDownloading) {
            appendLog("Please wait for the model download to complete before transcribing.")
            return
        }
        if (_uiState.value.isTranscribing) {
            appendLog("A transcription is already running in the background…")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            appendLog("Transcription requires Android 8.0 (API 26) or newer.")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.update { it.copy(isTranscribing = true, progress = 0.1f, statusMessage = "Loading model…") }
                val modelLabel: String
                val chosen = selectedModel
                val localModel = chosen?.let { opt ->
                    ModelManager.getLocalModelFile(appContext, opt.fileName).takeIf { file -> file.exists() && file.length() > 0L }
                }

                if (localModel != null) {
                    WhisperEngine.loadModelFromFile(localModel.absolutePath, force = true)
                    modelLabel = chosen?.id ?: localModel.name
                } else {
                    WhisperEngine.loadModelFromAssets(appContext, "models/ggml-tiny-q5_1.bin", force = true)
                    modelLabel = "ggml-tiny-q5_1 (asset)"
                }

                _uiState.update { it.copy(progress = 0.35f, statusMessage = "Preparing audio…") }

                val startNs = SystemClock.elapsedRealtimeNanos()
                val transcript = when (audioSource) {
                    is AudioSource.Asset -> {
                        _uiState.update { it.copy(progress = null, statusMessage = "Running Whisper…") }
                        WhisperEngine.transcribeWavAsset(appContext, audioSource.assetPath)
                    }
                    is AudioSource.File -> {
                        _uiState.update { it.copy(progress = null, statusMessage = "Running Whisper…") }
                        WhisperEngine.transcribeWavFile(audioSource.path)
                    }
                }

                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
                val timeLine = "Completed in " + String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0) + " s (" + elapsedMs.toLong() + " ms)"
                val fileLabel = when (audioSource) {
                    is AudioSource.Asset -> audioSource.assetPath.substringAfterLast('/')
                    is AudioSource.File -> java.io.File(audioSource.path).name
                }

                appendLog("File: $fileLabel\nModel: $modelLabel\nTranscript:\n$transcript\n$timeLine")
            } catch (t: Throwable) {
                appendLog("Transcribe failed: $t")
            } finally {
                _uiState.update { it.copy(isTranscribing = false, statusMessage = null, progress = null) }
            }
        }
    }

    fun deleteModel(context: Context, selectedModel: ModelOption?) {
        val appContext = context.applicationContext
        if (selectedModel == null) {
            appendLog("No model selected to delete.")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.update { it.copy(statusMessage = if (it.isTranscribing) it.statusMessage else "Deleting model…") }
                val file = ModelManager.getLocalModelFile(appContext, selectedModel.fileName)
                if (file.exists()) {
                    val ok = withContext(ioDispatcher) { file.delete() }
                    WhisperEngine.reset()
                    appendLog(if (ok) "Deleted model: ${file.name}" else "Failed to delete: ${file.name}")
                } else {
                    appendLog("Model not found locally: ${selectedModel.fileName}")
                }
            } catch (t: Throwable) {
                appendLog("Delete failed: $t")
            } finally {
                _uiState.update { current ->
                    if (current.isTranscribing) current else current.copy(statusMessage = null)
                }
            }
        }
    }
}
