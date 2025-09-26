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

    /** Load a ggml model from assets (e.g., models/ggml-tiny-q5_1.bin). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadModelFromAssets(context: Context, assetPath: String) {
        if (ctx != null) return
        ctx = WhisperContext.createContextFromAsset(context.assets, assetPath)
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
