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
    fun transcribeWavAsset(context: Context, wavAssetPath: String): WhisperTranscription =
        context.assets.open(wavAssetPath).use { input ->
            val (_, samples) = WavReader.readPcm16Mono16k(input)
            val c = ctx ?: error("Model not loaded. Call loadModelFromAssets() first.")
            c.transcribeData(samples)
        }

    /** Transcribe 16k mono PCM16 WAV from an absolute file path. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun transcribeWavFile(path: String): WhisperTranscription =
        java.io.File(path).inputStream().use { input ->
            val (_, samples) = WavReader.readPcm16Mono16k(input)
            val c = ctx ?: error("Model not loaded. Call loadModelFromAssets() first.")
            c.transcribeData(samples)
        }
}
