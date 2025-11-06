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
import kotlin.math.roundToInt

/** Snapshot of the current transcription UI. */
data class TranscriptionUiState(
    val log: String = "No transcriptions made\n",
    val isTranscribing: Boolean = false,
    val statusMessage: String? = null,
    val progress: Float? = null,
    val savedTranscriptions: List<SavedTranscription> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val currentFolderId: Long? = null
)

@Serializable
data class SavedTranscription(
    val id: Long,
    val fileLabel: String,
    val modelLabel: String,
    val transcript: String,
    val timestampedTranscript: String? = null,
    val timestamp: Long,
    val audioPath: String? = null,
    val transcriptionDurationMs: Long = 0L,
    // Optional folder containment; null means it appears at the root level
    val folderId: Long? = null
)

@Serializable
data class Folder(
    val id: Long,
    val name: String,
    val parentId: Long? = null
)

class TranscriptionViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()
    private val idCounter = AtomicLong(0L)
    private val folderIdCounter = AtomicLong(0L)

    init {
        viewModelScope.launch(ioDispatcher) {
            val persisted = TranscriptionRepository.load()
            val ordered = persisted.sortedByDescending { it.timestamp }
            val maxId = ordered.maxOfOrNull { it.id } ?: 0L
            idCounter.set(maxId)

            val loadedFolders = TranscriptionRepository.loadFolders()
            val maxFolderId = loadedFolders.maxOfOrNull { it.id } ?: 0L
            folderIdCounter.set(maxFolderId)

            _uiState.update { current ->
                current.copy(
                    savedTranscriptions = ordered,
                    folders = loadedFolders
                )
            }
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

    fun navigateToFolder(folderId: Long?) {
        _uiState.update { it.copy(currentFolderId = folderId) }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            var updated: List<Folder> = emptyList()
            _uiState.update { current ->
                val newFolder = Folder(
                    id = folderIdCounter.incrementAndGet(),
                    name = trimmed,
                    parentId = current.currentFolderId
                )
                val next = current.folders + newFolder
                updated = next
                current.copy(folders = next)
            }
            TranscriptionRepository.persistFolders(updated)
        }
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

    private data class TranscriptionRun(
        val plainTranscript: String,
        val timestampedTranscript: String,
        val elapsedMs: Long,
        val modelLabel: String
    )

    private fun modelOptionFromLabel(label: String): ModelOption? {
        val spec = ModelManager.findModel(label) ?: return null
        return ModelOption(id = spec.id, fileName = spec.fileName, url = spec.url)
    }

    private fun updateProgress(prefix: String, processedChunks: Int, totalChunks: Int) {
        if (totalChunks <= 0) {
            _uiState.update { it.copy(progress = null, statusMessage = prefix) }
            return
        }
        val clamped = processedChunks.coerceIn(0, totalChunks)
        val fraction = clamped.toFloat() / totalChunks
        val percent = (fraction * 100f).roundToInt().coerceIn(0, 100)
        _uiState.update { current ->
            current.copy(
                progress = fraction,
                statusMessage = "$prefix $percent%"
            )
        }
    }

    private suspend fun performTranscription(
        appContext: Context,
        selectedModel: ModelOption?,
        audioSource: AudioSource,
        onProgress: ((processedChunks: Int, totalChunks: Int) -> Unit)? = null
    ): TranscriptionRun {
        val chosen = selectedModel
        val localModel = chosen?.let { opt ->
            ModelManager.getLocalModelFile(appContext, opt.fileName).takeIf { file -> file.exists() && file.length() > 0L }
        }

        val modelLabel: String
        if (localModel != null) {
            WhisperEngine.loadModelFromFile(localModel.absolutePath, force = true)
            modelLabel = chosen?.id ?: localModel.name
        } else {
            WhisperEngine.loadModelFromAssets(appContext, "models/ggml-tiny-q5_1.bin", force = true)
            modelLabel = "ggml-tiny-q5_1 (asset)"
        }

        val startMs = SystemClock.elapsedRealtime()
        val transcription = when (audioSource) {
            is AudioSource.Asset -> WhisperEngine.transcribeWavAsset(appContext, audioSource.assetPath, onProgress)
            is AudioSource.File -> WhisperEngine.transcribeWavFile(audioSource.path, onProgress)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        return TranscriptionRun(
            plainTranscript = transcription.plain,
            timestampedTranscript = transcription.timestamped,
            elapsedMs = elapsedMs,
            modelLabel = modelLabel
        )
    }

    fun startTranscription(
        context: Context,
        selectedModel: ModelOption?,
        audioSource: AudioSource,
        isModelDownloading: Boolean,
        transcriptionName: String? = null,
        targetFolderId: Long? = null
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
                _uiState.update {
                    it.copy(
                        isTranscribing = true,
                        progress = null,
                        statusMessage = "Loading model…"
                    )
                }
                _uiState.update { it.copy(statusMessage = "Preparing audio…") }

                val result = performTranscription(appContext, selectedModel, audioSource) { processed, total ->
                    updateProgress("Transcribing…", processed, total)
                }
                val timeLine = "Completed in " + String.format(java.util.Locale.US, "%.1f", result.elapsedMs / 1000.0) + " s (" + result.elapsedMs + " ms)"
                val providedLabel = transcriptionName?.trim()?.takeIf { it.isNotEmpty() }
                val fileLabel = providedLabel ?: when (audioSource) {
                    is AudioSource.Asset -> audioSource.assetPath.substringAfterLast('/')
                    is AudioSource.File -> java.io.File(audioSource.path).name
                }

                appendLog("File: $fileLabel\nModel: ${result.modelLabel}\nTranscript:\n${result.plainTranscript}\n$timeLine")
                val audioPath = persistAudioFile(appContext, audioSource)
                val entry = SavedTranscription(
                    id = idCounter.incrementAndGet(),
                    fileLabel = fileLabel,
                    modelLabel = result.modelLabel,
                    transcript = result.plainTranscript,
                    timestampedTranscript = result.timestampedTranscript,
                    timestamp = System.currentTimeMillis(),
                    audioPath = audioPath,
                    transcriptionDurationMs = result.elapsedMs,
                    folderId = targetFolderId
                )
                saveTranscriptionEntry(entry)
                _uiState.update { it.copy(statusMessage = "Transcription saved: $fileLabel") }
            } catch (t: Throwable) {
                appendLog("Transcribe failed: $t")
                _uiState.update { it.copy(statusMessage = "Transcribe failed: ${t.message}") }
            } finally {
                _uiState.update { it.copy(isTranscribing = false, progress = null) }
            }
        }
    }

    fun reTranscribeEntry(context: Context, entryId: Long, selectedModel: ModelOption? = null) {
        val entry = _uiState.value.savedTranscriptions.firstOrNull { it.id == entryId } ?: return
        val audioPath = entry.audioPath
        if (audioPath.isNullOrBlank()) {
            appendLog("Cannot re-transcribe ${entry.fileLabel}: audio file missing.")
            _uiState.update { it.copy(statusMessage = "Audio missing for ${entry.fileLabel}") }
            return
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            appendLog("Cannot re-transcribe ${entry.fileLabel}: audio file not found.")
            _uiState.update { it.copy(statusMessage = "Audio file not found for ${entry.fileLabel}") }
            return
        }
        if (_uiState.value.isTranscribing) {
            appendLog("A transcription is already running in the background…")
            _uiState.update { it.copy(statusMessage = "Please wait for the current transcription to finish.") }
            return
        }

        val entrySnapshot = entry
        val appContext = context.applicationContext
        val source = AudioSource.File(audioFile.absolutePath)
        val option = selectedModel ?: modelOptionFromLabel(entrySnapshot.modelLabel)

        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.update {
                    it.copy(
                        isTranscribing = true,
                        progress = null,
                        statusMessage = "Re-transcribing ${entrySnapshot.fileLabel}…"
                    )
                }
                _uiState.update { it.copy(statusMessage = "Preparing audio…") }

                val result = performTranscription(appContext, option, source) { processed, total ->
                    updateProgress("Re-transcribing ${entrySnapshot.fileLabel}…", processed, total)
                }
                val updatedEntry = entrySnapshot.copy(
                    transcript = result.plainTranscript,
                    timestampedTranscript = result.timestampedTranscript,
                    modelLabel = result.modelLabel,
                    timestamp = System.currentTimeMillis(),
                    transcriptionDurationMs = result.elapsedMs
                )
                appendLog("Re-transcribed ${entrySnapshot.fileLabel} (${result.modelLabel})")

                var persisted: List<SavedTranscription> = emptyList()
                _uiState.update { current ->
                    val next = current.savedTranscriptions.map { if (it.id == entryId) updatedEntry else it }
                    persisted = next
                    current.copy(savedTranscriptions = next, statusMessage = "Updated ${entrySnapshot.fileLabel}")
                }
                TranscriptionRepository.persist(persisted)
            } catch (t: Throwable) {
                appendLog("Re-transcribe failed: $t")
                _uiState.update { it.copy(statusMessage = "Re-transcribe failed: ${t.message}") }
            } finally {
                _uiState.update { it.copy(isTranscribing = false, progress = null) }
            }
        }
    }

    fun ensureTimestampedTranscript(context: Context, entryId: Long) {
        val entry = _uiState.value.savedTranscriptions.firstOrNull { it.id == entryId } ?: return
        if (!entry.timestampedTranscript.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = null) }
            return
        }

        val audioPath = entry.audioPath
        if (audioPath.isNullOrBlank()) {
            appendLog("Cannot generate timestamps for ${entry.fileLabel}: audio file missing.")
            _uiState.update { it.copy(statusMessage = "Audio missing for ${entry.fileLabel}") }
            return
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            appendLog("Cannot generate timestamps for ${entry.fileLabel}: audio file not found.")
            _uiState.update { it.copy(statusMessage = "Audio file not found for ${entry.fileLabel}") }
            return
        }
        if (_uiState.value.isTranscribing) {
            appendLog("A transcription is already running in the background…")
            _uiState.update { it.copy(statusMessage = "Please wait for the current transcription to finish.") }
            return
        }

        val appContext = context.applicationContext
        val option = modelOptionFromLabel(entry.modelLabel)
        val source = AudioSource.File(audioFile.absolutePath)

        viewModelScope.launch(ioDispatcher) {
            try {
                _uiState.update {
                    it.copy(
                        isTranscribing = true,
                        progress = null,
                        statusMessage = "Generating timestamp transcript…"
                    )
                }
                var persisted: List<SavedTranscription> = emptyList()
                val result = performTranscription(appContext, option, source) { processed, total ->
                    updateProgress("Generating timestamp transcript…", processed, total)
                }
                _uiState.update { current ->
                    val next = current.savedTranscriptions.map { saved ->
                        if (saved.id == entryId) {
                            saved.copy(timestampedTranscript = result.timestampedTranscript)
                        } else saved
                    }
                    persisted = next
                    current.copy(savedTranscriptions = next, statusMessage = null)
                }
                if (persisted.isNotEmpty()) {
                    TranscriptionRepository.persist(persisted)
                }
            } catch (t: Throwable) {
                appendLog("Timestamp generation failed: $t")
                _uiState.update { it.copy(statusMessage = "Timestamp generation failed: ${t.message}") }
            } finally {
                _uiState.update { it.copy(isTranscribing = false, progress = null) }
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

    fun listFoldersInCurrent(): List<Folder> {
        val state = _uiState.value
        return state.folders.filter { it.parentId == state.currentFolderId }.sortedBy { it.name.lowercase() }
    }

    fun listTranscriptionsInCurrent(): List<SavedTranscription> {
        val state = _uiState.value
        return state.savedTranscriptions.filter { it.folderId == state.currentFolderId }
    }

    private fun collectDescendantFolderIds(rootId: Long, folders: List<Folder>, acc: MutableSet<Long>) {
        val children = folders.filter { it.parentId == rootId }
        for (child in children) {
            if (acc.add(child.id)) {
                collectDescendantFolderIds(child.id, folders, acc)
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch(ioDispatcher) {
            var foldersToPersist: List<Folder> = emptyList()
            var transToPersist: List<SavedTranscription> = emptyList()
            _uiState.update { current ->
                val target = current.folders.firstOrNull { it.id == folderId } ?: return@update current
                val toDelete: MutableSet<Long> = mutableSetOf(folderId)
                collectDescendantFolderIds(folderId, current.folders, toDelete)

                // Delete audio files for any transcriptions inside the folders being deleted
                current.savedTranscriptions.forEach { st ->
                    if (st.folderId != null && toDelete.contains(st.folderId)) {
                        runCatching { st.audioPath?.let { java.io.File(it).takeIf { f -> f.exists() }?.delete() } }
                    }
                }

                val remainingFolders = current.folders.filterNot { it.id in toDelete }
                val remainingTrans = current.savedTranscriptions.filterNot { st -> st.folderId != null && toDelete.contains(st.folderId) }

                foldersToPersist = remainingFolders
                transToPersist = remainingTrans

                current.copy(
                    folders = remainingFolders,
                    savedTranscriptions = remainingTrans,
                    currentFolderId = target.parentId
                )
            }
            if (foldersToPersist.isNotEmpty() || transToPersist.isNotEmpty()) {
                TranscriptionRepository.persistFolders(foldersToPersist)
                TranscriptionRepository.persist(transToPersist)
            } else {
                // Still persist empties if everything was removed to keep files in sync
                TranscriptionRepository.persistFolders(foldersToPersist)
                TranscriptionRepository.persist(transToPersist)
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
            var status: String? = null
            try {
                _uiState.update { it.copy(statusMessage = if (it.isTranscribing) it.statusMessage else "Deleting model…") }
                val file = ModelManager.getLocalModelFile(appContext, selectedModel.fileName)
                if (file.exists()) {
                    val ok = withContext(ioDispatcher) { file.delete() }
                    WhisperEngine.reset()
                    if (ok) {
                        appendLog("Deleted model: ${file.name}")
                        status = "${selectedModel.id} deleted successfully"
                    } else {
                        appendLog("Failed to delete: ${file.name}")
                        status = "Failed to delete ${selectedModel.id}"
                    }
                } else {
                    appendLog("Model not found locally: ${selectedModel.fileName}")
                    status = "Model not found locally"
                }
            } catch (t: Throwable) {
                appendLog("Delete failed: $t")
                status = "Delete failed: ${t.message}"
            } finally {
                _uiState.update { current ->
                    if (current.isTranscribing) current else current.copy(statusMessage = status)
                }
            }
        }
    }
}
