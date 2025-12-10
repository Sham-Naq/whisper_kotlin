package com.example.whisper_kotlin.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.WhisperEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelDownloadUiState(
    val selectedModel: ModelOption? = null,
    val downloadingId: String? = null,
    val progressPct: Int = 0,
    val message: String? = null,
    val isCancelling: Boolean = false
)

class ModelDownloadViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()

    fun cancelDownload() {
        val id = _uiState.value.downloadingId ?: return
        ModelManager.cancelDownload(id)
        _uiState.update { it.copy(isCancelling = true, message = "Cancelling…") }
    }

    fun setSelectedModel(option: ModelOption?) {
        _uiState.update { it.copy(selectedModel = option) }
    }

    fun selectIfPresent(context: Context, option: ModelOption) {
        viewModelScope.launch(ioDispatcher) {
            val file = ModelManager.getLocalModelFile(context.applicationContext, option.fileName)
            if (file.exists() && file.length() > 0L) {
                runCatching { WhisperEngine.loadModelFromFile(file.absolutePath, force = true) }
                _uiState.update { it.copy(selectedModel = option) }
            }
        }
    }

    fun startDownloadOrSelect(context: Context, option: ModelOption) {
        val appContext = context.applicationContext
        // If already selected and present, nothing to do.
        viewModelScope.launch(ioDispatcher) {
            val present = ModelManager.isModelPresent(appContext, option.fileName)
            if (present) {
                runCatching {
                    val mf = ModelManager.getLocalModelFile(appContext, option.fileName)
                    if (mf.exists() && mf.length() > 0) {
                        WhisperEngine.loadModelFromFile(mf.absolutePath, force = true)
                    }
                }
                _uiState.update { it.copy(selectedModel = option) }
                return@launch
            }
            if (_uiState.value.downloadingId != null) return@launch
            _uiState.update { it.copy(downloadingId = option.id, progressPct = 0, message = null, isCancelling = false) }
            try {
                try {
                    ModelManager.ensureModel(
                        context = appContext,
                        modelFileName = option.fileName,
                        modelUrl = option.url,
                        onProgress = { p ->
                            val pct =
                                if (p.totalBytes > 0) ((p.bytesRead * 100L) / p.totalBytes).toInt() else 0
                            _uiState.update { s -> s.copy(progressPct = pct.coerceIn(0, 100)) }
                        }
                    )
                } catch (ce: java.util.concurrent.CancellationException) {
                    _uiState.update { it.copy(message = "Download canceled", isCancelling = false) }
                    return@launch
                } catch (t: Throwable) {
                    _uiState.update { it.copy(message = "Download failed: ${'$'}{t.message}") }
                    return@launch
                }
                runCatching {
                    val mf = ModelManager.getLocalModelFile(appContext, option.fileName)
                    if (mf.exists() && mf.length() > 1_000_000) { // sanity threshold to avoid tiny/corrupt file loads
                        WhisperEngine.loadModelFromFile(mf.absolutePath, force = true)
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(message = "Model load error: ${'$'}{e.message}") }
                }
                _uiState.update { it.copy(selectedModel = option, message = it.message) }
            } finally {
                _uiState.update { it.copy(downloadingId = null, progressPct = 0, isCancelling = false) }
            }
        }
    }
}