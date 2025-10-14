package com.example.whisper_kotlin

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.ExecutionException

/**
 * Thin Kotlin wrapper over the Java JNI API to load model from assets and transcribe.
 */
object WhisperEngine {
    private const val LOG_TAG = "WhisperEngine"

    private const val SAMPLE_RATE = 16_000
    private const val MAX_CHUNK_SECONDS = 28
    private const val OVERLAP_MS = 500

    private val MAX_CHUNK_SAMPLES = SAMPLE_RATE * MAX_CHUNK_SECONDS
    private val OVERLAP_SAMPLES = SAMPLE_RATE * OVERLAP_MS / 1000
    private const val SAMPLES_PER_TICK = SAMPLE_RATE / 100 // 10 ms tick at 16 kHz

    @Volatile private var ctx: TimestampWhisperContext? = null
    // Track which model is loaded to avoid stale context when switching
    @Volatile private var loadedKey: String? = null // e.g., "file:/abs/path" or "asset:models/ggml-*.bin"

    /** Clear the current whisper context (next load will recreate). */
    fun reset() {
        val current = ctx
        if (current != null) {
            try {
                current.release()
            } catch (e: ExecutionException) {
                Log.w(LOG_TAG, "Failed to release whisper context", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(LOG_TAG, "Release interrupted", e)
            }
        }
        ctx = null
        loadedKey = null
    }

    /** Load a ggml model from an absolute file path on disk (e.g., filesDir/models/ggml-*.bin). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadModelFromFile(modelPath: String, force: Boolean = false) {
        val key = "file:" + modelPath
        if (!force && ctx != null && loadedKey == key) return
        if (ctx != null && (force || loadedKey != key)) reset()
        ctx = TimestampWhisperContext.createContextFromFile(modelPath)
        loadedKey = key
    }

    /** Load a ggml model from assets (e.g., models/ggml-tiny-q5_1.bin). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadModelFromAssets(context: Context, assetPath: String, force: Boolean = false) {
        val key = "asset:" + assetPath
        if (!force && ctx != null && loadedKey == key) return
        if (ctx != null && (force || loadedKey != key)) reset()
        ctx = TimestampWhisperContext.createContextFromAsset(context.assets, assetPath)
        loadedKey = key
    }

    /** Return system info string from whisper.cpp. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun systemInfo(): String = TimestampWhisperContext.getSystemInfo()

    /** Transcribe 16k mono PCM16 WAV from assets. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun transcribeWavAsset(
        context: Context,
        wavAssetPath: String,
        onProgress: ((processedChunks: Int, totalChunks: Int) -> Unit)? = null
    ): WhisperTranscription =
        context.assets.open(wavAssetPath).use { input ->
            val (_, samples) = WavReader.readPcm16Mono16k(input)
            transcribeSamples(samples, onProgress)
        }

    /** Transcribe 16k mono PCM16 WAV from an absolute file path. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun transcribeWavFile(
        path: String,
        onProgress: ((processedChunks: Int, totalChunks: Int) -> Unit)? = null
    ): WhisperTranscription =
        java.io.File(path).inputStream().use { input ->
            val (_, samples) = WavReader.readPcm16Mono16k(input)
            transcribeSamples(samples, onProgress)
        }

    private fun transcribeSamples(
        samples: FloatArray,
        onProgress: ((processedChunks: Int, totalChunks: Int) -> Unit)?
    ): WhisperTranscription {
        val context = ctx ?: error("Model not loaded. Call loadModelFromAssets() first.")
        if (samples.size <= MAX_CHUNK_SAMPLES) {
            onProgress?.invoke(0, 1)
            val result = context.transcribeData(samples)
            onProgress?.invoke(1, 1)
            return result
        }
        val chunks = deriveChunks(samples)
        val totalChunks = chunks.size
        onProgress?.invoke(0, totalChunks)
        val plainBuilder = StringBuilder()
        val timestampBuilder = StringBuilder()
        val mergedSegments = ArrayList<WhisperSegment>()
        var lastAddedEndTick = -1L

        var processedChunks = 0
        for (chunk in chunks) {
            val chunkSamples = samples.copyOfRange(chunk.start, chunk.endExclusive)
            val result = context.transcribeData(chunkSamples)
            val offsetTicks = chunk.start.toLong() / SAMPLES_PER_TICK
            for (segment in result.segments) {
                val adjustedT0 = segment.t0 + offsetTicks
                val adjustedT1 = segment.t1 + offsetTicks
                if (mergedSegments.isNotEmpty() && adjustedT1 <= lastAddedEndTick) {
                    continue
                }
                mergedSegments.add(WhisperSegment(segment.text, adjustedT0, adjustedT1))
                lastAddedEndTick = maxOf(lastAddedEndTick, adjustedT1)
                plainBuilder.append(segment.text)
                timestampBuilder.append('(')
                timestampBuilder.append(formatTimestamp(adjustedT0))
                timestampBuilder.append(") ")
                timestampBuilder.append(segment.text.trimStart { it == ' ' })
                if (!segment.text.endsWith('\n')) {
                    timestampBuilder.append('\n')
                }
            }
            processedChunks += 1
            onProgress?.invoke(processedChunks, totalChunks)
        }

        return WhisperTranscription(
            plain = plainBuilder.toString(),
            timestamped = timestampBuilder.toString(),
            segments = mergedSegments
        )
    }

    private data class Chunk(val start: Int, val endExclusive: Int)

    private fun deriveChunks(samples: FloatArray): List<Chunk> {
        val chunks = ArrayList<Chunk>()
        val total = samples.size
        var start = 0
        while (start < total) {
            val end = (start + MAX_CHUNK_SAMPLES).coerceAtMost(total)
            chunks.add(Chunk(start, end))
            if (end >= total) break
            val next = end - OVERLAP_SAMPLES
            start = if (next <= start) end else next
        }
        return chunks
    }

    private fun formatTimestamp(ticks: Long): String {
        val totalMillis = ticks * 10
        val totalSeconds = totalMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
