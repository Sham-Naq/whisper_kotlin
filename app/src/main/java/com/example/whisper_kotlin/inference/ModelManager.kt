package com.example.whisper_kotlin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads whisper.cpp GGML models from Hugging Face into app-private storage.
 * - Default model: ggml-tiny.bin
 * - Destination: context.filesDir/models/
 * - Skips network if the target file already exists and is non-empty
 * - Optional progress callback for UI
 */
object ModelManager {

    // --- Catalog of supported whisper.cpp GGML models ---
    data class ModelSpec(val id: String, val fileName: String, val url: String)

    private const val HF_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    private val AVAILABLE_MODELS: List<ModelSpec> = listOf(
        ModelSpec(
            id = "tiny",
            fileName = "ggml-tiny.bin",
            url = HF_BASE + "ggml-tiny.bin?download=true"
        ),
        ModelSpec(
            id = "tiny-q5",
            fileName = "ggml-tiny-q5_1.bin",
            url = HF_BASE + "ggml-tiny-q5_1.bin?download=true"
        ),
        ModelSpec(
            id = "tiny-q8",
            fileName = "ggml-tiny-q8_0.bin",
            url = HF_BASE + "ggml-tiny-q8_0.bin?download=true"
        ),
        ModelSpec(
            id = "base",
            fileName = "ggml-base.bin",
            url = HF_BASE + "ggml-base.bin?download=true"
        ),
        ModelSpec(
            id = "base-q5",
            fileName = "ggml-base-q5_1.bin",
            url = HF_BASE + "ggml-base-q5_1.bin?download=true"
        ),
        ModelSpec(
            id = "base-q8",
            fileName = "ggml-base-q8_0.bin",
            url = HF_BASE + "ggml-base-q8_0.bin?download=true"
        ),
        ModelSpec(
            id = "small",
            fileName = "ggml-small.bin",
            url = HF_BASE + "ggml-small.bin?download=true"
        ),
        ModelSpec(
            id = "small-q5",
            fileName = "ggml-small-q5_1.bin",
            url = HF_BASE + "ggml-small-q5_1.bin?download=true"
        ),
        ModelSpec(
            id = "small-q8",
            fileName = "ggml-small-q8_0.bin",
            url = HF_BASE + "ggml-small-q8_0.bin?download=true"
        )
    )

    // Defaults centralized here so callers don't hardcode
    private const val DEFAULT_MODEL_FILE_NAME = "ggml-tiny.bin"
    private const val DEFAULT_MODEL_URL = HF_BASE + "ggml-tiny.bin?download=true"

    /** Expose catalog to UI layers (read-only). */
    fun availableModels(): List<ModelSpec> = AVAILABLE_MODELS

    /** First entry in the catalog, treated as default for UI fallbacks. */
    fun defaultModel(): ModelSpec? = AVAILABLE_MODELS.firstOrNull()

    /** Find a model by id (e.g., "ggml-tiny-q5_1") or by exact fileName (e.g., "ggml-tiny-q5_1.bin"). */
    fun findModel(idOrFileName: String): ModelSpec? = AVAILABLE_MODELS.firstOrNull { spec ->
        spec.id.equals(idOrFileName, ignoreCase = true) || spec.fileName.equals(idOrFileName, ignoreCase = true)
    }

    // --- Cancellation management for in-flight downloads ---
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()

    /** Request cancellation for an ongoing download by model id or fileName. */
    fun cancelDownload(modelIdOrFileName: String): Boolean {
        val fileName = findModel(modelIdOrFileName)?.fileName ?: modelIdOrFileName
        val flag = cancelFlags.computeIfAbsent(fileName) { AtomicBoolean(false) }
        flag.set(true)
        // Best-effort: disconnect connection to unblock read
        activeConnections[fileName]?.disconnect()
        return true
    }

    data class Progress(val bytesRead: Long, val totalBytes: Long) {
        val percent: Int
            get() = if (totalBytes > 0) ((bytesRead * 100.0 / totalBytes).toInt()) else -1
    }

    /** Outcome of ensuring a model: tells caller if it was already present. */
    data class EnsureOutcome(val file: File, val alreadyPresent: Boolean)

    /** True if the named model (id or fileName) exists and is non-empty under filesDir/models. */
    fun isModelPresent(context: Context, modelFileName: String = DEFAULT_MODEL_FILE_NAME): Boolean {
        val fileName = findModel(modelFileName)?.fileName ?: modelFileName
        val target = File(File(context.filesDir, "models"), fileName)
        return target.exists() && target.length() > 0L
    }

    /**
     * Ensure the model exists locally, downloading if needed.
     * Returns [EnsureOutcome] with file and whether it was already present.
     */
    suspend fun ensureModel(
        context: Context,
        modelFileName: String = DEFAULT_MODEL_FILE_NAME,
        modelUrl: String = DEFAULT_MODEL_URL,
        onProgress: ((Progress) -> Unit)? = null
    ): EnsureOutcome = withContext(Dispatchers.IO) {
        val resolvedFileName = findModel(modelFileName)?.fileName ?: modelFileName
        val resolvedUrl = findModel(modelFileName)?.url ?: modelUrl
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, resolvedFileName)

        if (target.exists() && target.length() > 0L) {
            return@withContext EnsureOutcome(target, alreadyPresent = true)
        }

        val url = URL(resolvedUrl)
        val tmp = File.createTempFile("dl_", ".part", modelsDir)
        // Register cancel flag for this file
        val cancelFlag = cancelFlags.computeIfAbsent(resolvedFileName) { AtomicBoolean(false) }
        try {
            downloadToFile(resolvedFileName, url, tmp, cancelFlag) { read, total ->
                onProgress?.invoke(Progress(read, total))
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return@withContext EnsureOutcome(target, alreadyPresent = false)
        } catch (ce: java.util.concurrent.CancellationException) {
            // Treat cancellation as a normal outcome: clean up and rethrow or return? We'll rethrow to let UI know
            runCatching { tmp.delete() }
            if (target.exists() && target.length() == 0L) runCatching { target.delete() }
            throw ce
        } catch (t: Throwable) {
            // On any failure or cancellation, delete temp and any zero-length target
            runCatching { tmp.delete() }
            if (target.exists() && target.length() == 0L) runCatching { target.delete() }
            throw t
        } finally {
            // Clear flags and connection tracking
            cancelFlags.remove(resolvedFileName)
            activeConnections.remove(resolvedFileName)
        }
    }

    /** Convenience overload: ensure by model id or fileName using the built-in catalog. */
    suspend fun ensureModel(
        context: Context,
        modelIdOrFileName: String,
        onProgress: ((Progress) -> Unit)? = null
    ): EnsureOutcome {
        val spec = findModel(modelIdOrFileName)
            ?: ModelSpec(id = modelIdOrFileName, fileName = modelIdOrFileName, url = DEFAULT_MODEL_URL)
        return ensureModel(context, modelFileName = spec.fileName, modelUrl = spec.url, onProgress = onProgress)
    }

    /** Back-compat: returns only the File, downloading if needed. */
    suspend fun getOrDownloadModel(
        context: Context,
        modelFileName: String = DEFAULT_MODEL_FILE_NAME,
        modelUrl: String? = null,
        onProgress: ((Progress) -> Unit)? = null
    ): File {
        val outcome = ensureModel(
            context = context,
            modelFileName = modelFileName,
            modelUrl = modelUrl ?: DEFAULT_MODEL_URL,
            onProgress = onProgress
        )
        return outcome.file
    }

    /** Returns the File handle under filesDir/models for a given id or fileName (no I/O). */
    fun getLocalModelFile(context: Context, modelIdOrFileName: String = DEFAULT_MODEL_FILE_NAME): File {
        val fileName = findModel(modelIdOrFileName)?.fileName ?: modelIdOrFileName
        val modelsDir = File(context.filesDir, "models")
        return File(modelsDir, fileName)
    }

    private fun downloadToFile(
        keyFileName: String,
        url: URL,
        outFile: File,
        cancelFlag: AtomicBoolean,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?
    ) {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "whisper-kotlin/1.0 (Android)")
            }
            // Track active connection for cancellation
            activeConnections[keyFileName] = connection
            connection.connect()

            val response = connection.responseCode
            if (response !in 200..299) {
                throw IllegalStateException("HTTP $response for $url")
            }

            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            input = connection.inputStream
            output = FileOutputStream(outFile)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesReadTotal = 0L
            if (cancelFlag.get()) throw java.util.concurrent.CancellationException()
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesReadTotal += read
                onProgress?.invoke(bytesReadTotal, total)
                if (cancelFlag.get()) throw java.util.concurrent.CancellationException()
            }
            output.fd.sync() // best-effort
        } finally {
            runCatching { output?.close() }
            runCatching { input?.close() }
            connection?.disconnect()
            // Remove connection tracking once closed
            activeConnections.remove(keyFileName)
        }
    }
}
