package com.example.whisper_kotlin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads Whisper models from Hugging Face into app-private storage.
 * - Default model: ggml-tiny.bin
 * - Destination: context.filesDir/models/
 * - Skips network if the target file already exists and is non-empty
 * - Optional progress callback for UI
 */
object ModelManager {

    // Defaults centralized here so Activities don't hardcode
    private const val DEFAULT_MODEL_FILE_NAME = "whisper-tiny.tflite"
    private const val DEFAULT_MODEL_URL =
        "https://huggingface.co/cik009/whisper/resolve/main/whisper-tiny.tflite?download=true"

    data class Progress(val bytesRead: Long, val totalBytes: Long) {
        val percent: Int
            get() = if (totalBytes > 0) ((bytesRead * 100.0 / totalBytes).toInt()) else -1
    }

    /** Outcome of ensuring a model: tells caller if it was already present. */
    data class EnsureOutcome(val file: File, val alreadyPresent: Boolean)

    /** True if the named model exists and is non-empty under filesDir/models. */
    fun isModelPresent(context: Context, modelFileName: String = DEFAULT_MODEL_FILE_NAME): Boolean {
        val target = File(File(context.filesDir, "models"), modelFileName)
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
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, modelFileName)

        if (target.exists() && target.length() > 0L) {
            return@withContext EnsureOutcome(target, alreadyPresent = true)
        }

        val url = URL(modelUrl)
        val tmp = File.createTempFile("dl_", ".part", modelsDir)
        try {
            downloadToFile(url, tmp) { read, total ->
                onProgress?.invoke(Progress(read, total))
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return@withContext EnsureOutcome(target, alreadyPresent = false)
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            throw t
        }
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

    private fun downloadToFile(
        url: URL,
        outFile: File,
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
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                bytesReadTotal += read
                onProgress?.invoke(bytesReadTotal, total)
            }
            output.fd.sync() // best-effort
        } finally {
            runCatching { output?.close() }
            runCatching { input?.close() }
            connection?.disconnect()
        }
    }
}
