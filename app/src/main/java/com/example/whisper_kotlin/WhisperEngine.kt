package com.example.whisper_kotlin

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.whispercpp.java.whisper.WhisperContext

/**
 * Thin Kotlin wrapper over the Java JNI API to load model from assets and transcribe.
 */
object WhisperEngine {
    @Volatile private var ctx: WhisperContext? = null
    // Track which model is loaded to avoid stale context when switching
    @Volatile private var loadedKey: String? = null // e.g., "file:/abs/path" or "asset:models/ggml-*.bin"

    /** Clear the current whisper context (next load will recreate). */
    fun reset() {
        // If WhisperContext exposes a close/dispose in your version, you can call it here.
        // We keep it safe for compilation across versions by just clearing the reference.
        ctx = null
        loadedKey = null
    }

    /** Load a ggml model from an absolute file path on disk (e.g., filesDir/models/ggml-*.bin). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadModelFromFile(modelPath: String, force: Boolean = false) {
        val key = "file:" + modelPath
        if (!force && ctx != null && loadedKey == key) return
        if (ctx != null && (force || loadedKey != key)) reset()
        ctx = WhisperContext.createContextFromFile(modelPath)
        loadedKey = key
    }

    /** Load a ggml model from assets (e.g., models/ggml-tiny-q5_1.bin). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadModelFromAssets(context: Context, assetPath: String, force: Boolean = false) {
        val key = "asset:" + assetPath
        if (!force && ctx != null && loadedKey == key) return
        if (ctx != null && (force || loadedKey != key)) reset()
        ctx = WhisperContext.createContextFromAsset(context.assets, assetPath)
        loadedKey = key
    }

    /** Return system info string from whisper.cpp. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun systemInfo(): String = WhisperContext.getSystemInfo()

    /** Transcribe 16k mono PCM16 WAV from assets. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun transcribeWavAsset(context: Context, wavAssetPath: String): String {
        val input = context.assets.open(wavAssetPath)
        val (_, samples) = WavReader.readPcm16Mono16k(input)
        val c = ctx ?: error("Model not loaded. Call loadModelFromAssets() first.")
        return c.transcribeData(samples)
    }
}
