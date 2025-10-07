package com.example.whisper_kotlin

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whisper_kotlin.data.TranscriptionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong
import java.io.File

/** Snapshot of the current transcription UI. */
data class TranscriptionUiState(
    val log: String = "No transcriptions made\n",
    val isTranscribing: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float? = null,
    val savedTranscriptions: List<SavedTranscription> = emptyList()
)

@Serializable
data class SavedTranscription(
    val id: Long,
    val fileLabel: String,
    val modelLabel: String,
    val transcript: String,
    val timestamp: Long,
    val audioPath: String? = null,
    val transcriptionDurationMs: Long = 0L
)

class TranscriptionViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()
    private val idCounter = AtomicLong(0L)

    init {
        viewModelScope.launch(ioDispatcher) {
            val persisted = TranscriptionRepository.load()
            val ordered = persisted.sortedByDescending { it.timestamp }
            val maxId = ordered.maxOfOrNull { it.id } ?: 0L
            idCounter.set(maxId)
            _uiState.update { current -> current.copy(savedTranscriptions = ordered) }
        }
    }

    /** Append a log line, trimming the placeholder message when the first real line arrives. */
    private fun appendLog(line: String) {
        _uiState.update { current ->
            val base = if (current.log == "No transcriptions made\n") "" else current.log
            current.copy(log = base + line + "\n")
        }
    }

    private suspend fun saveTranscriptionEntry(entry: SavedTranscription) {
        var updated: List<SavedTranscription> = emptyList()
        _uiState.update { current ->
            val next = listOf(entry) + current.savedTranscriptions
            updated = next
            current.copy(savedTranscriptions = next)
        }
        TranscriptionRepository.persist(updated)
    }

    private suspend fun persistAudioFile(context: Context, audioSource: AudioSource): String? {
        return when (audioSource) {
            is AudioSource.Asset -> {
                val dir = TranscriptionRepository.audioDirectory() ?: return null
                val extension = audioSource.assetPath.substringAfterLast('.', "wav")
                val dest = File(dir, "asset_${System.currentTimeMillis()}.$extension")
                runCatching {
                    context.assets.open(audioSource.assetPath).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    dest.absolutePath
                }.getOrNull()
            }
            is AudioSource.File -> {
                val source = File(audioSource.path)
                if (!source.exists()) return null
                val dir = TranscriptionRepository.audioDirectory() ?: return null
                val extension = source.extension.ifEmpty { "wav" }
                val dest = File(dir, "recording_${System.currentTimeMillis()}.$extension")
                runCatching {
                    source.copyTo(dest, overwrite = true)
                    dest.absolutePath
                }.getOrNull()
            }
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

                val startMs = SystemClock.elapsedRealtime()
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

                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                val timeLine = "Completed in " + String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0) + " s (" + elapsedMs + " ms)"
                val fileLabel = when (audioSource) {
                    is AudioSource.Asset -> audioSource.assetPath.substringAfterLast('/')
                    is AudioSource.File -> java.io.File(audioSource.path).name
                }

                appendLog("File: $fileLabel\nModel: $modelLabel\nTranscript:\n$transcript\n$timeLine")
                val audioPath = persistAudioFile(appContext, audioSource)
                val entry = SavedTranscription(
                    id = idCounter.incrementAndGet(),
                    fileLabel = fileLabel,
                    modelLabel = modelLabel,
                    transcript = transcript,
                    timestamp = System.currentTimeMillis(),
                    audioPath = audioPath,
                    transcriptionDurationMs = elapsedMs
                )
                saveTranscriptionEntry(entry)
            } catch (t: Throwable) {
                appendLog("Transcribe failed: $t")
            } finally {
                _uiState.update { it.copy(isTranscribing = false, statusMessage = null, progress = null) }
            }
        }
    }

    fun deleteTranscription(context: Context, entryId: Long) {
        val appContext = context.applicationContext
        viewModelScope.launch(ioDispatcher) {
            val currentList = _uiState.value.savedTranscriptions
            val entry = currentList.firstOrNull { it.id == entryId } ?: return@launch
            entry.audioPath?.let { path ->
                runCatching { File(path).takeIf { it.exists() }?.delete() }
            }
            val updated = currentList.filterNot { it.id == entryId }
            TranscriptionRepository.persist(updated)
            _uiState.update { it.copy(savedTranscriptions = updated) }
        }
    }

    fun getTranscription(entryId: Long): SavedTranscription? {
        return _uiState.value.savedTranscriptions.firstOrNull { it.id == entryId }
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
