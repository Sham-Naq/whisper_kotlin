package com.example.whisper_kotlin.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Simple microphone recorder that emits normalized amplitude bars with EMA smoothing. */
class AudioRecorder(
    private val barCount: Int = 48,
    private val scope: CoroutineScope
) {
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val minBuffer = max(
        2048,
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
    )

    private var record: AudioRecord? = null
    private var job: Job? = null
    private var output: java.io.OutputStream? = null
    @Volatile private var paused: Boolean = false

    private val _bars = MutableStateFlow(FloatArray(barCount) { 0f })
    val bars: StateFlow<FloatArray> = _bars

    private var ema: FloatArray = FloatArray(barCount) { 0f }
    private val alpha = 0.45f // smoothing factor (higher = more responsive)
    private val visualizationGain = 1.8f

    fun start(outputRawFile: java.io.File? = null) {
        if (job != null) return
        paused = false
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            minBuffer
        )
        record = rec
        rec.startRecording()
        output = outputRawFile?.let { java.io.BufferedOutputStream(it.outputStream()) }

        job = scope.launch(Dispatchers.Default) {
            val buf = ShortArray(minBuffer / 2)
            val window = max(192, sampleRate / (barCount * 3 / 2)) // smaller window for more detail
            var accAbs = 0L
            var count = 0
            val values = FloatArray(barCount)
            var barIdx = 0
            while (isActive && record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                if (paused) {
                    // still read to keep buffer from blocking, but don't write or update bars
                    rec.read(buf, 0, buf.size)
                    continue
                }
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                // optionally write shorts as little-endian bytes
                output?.let { out ->
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        out.write(s and 0xFF)
                        out.write((s ushr 8) and 0xFF)
                    }
                }
                for (i in 0 until n) {
                    accAbs += abs(buf[i].toInt())
                    count++
                    if (count >= window) {
                        val avg = (accAbs.toFloat() / count) / 32768f
                        val boosted = (avg * visualizationGain).coerceIn(0f, 1f)
                        values[barIdx % barCount] = sqrt(boosted)
                        accAbs = 0
                        count = 0
                        barIdx++
                    }
                }
                // smooth and publish
                for (i in 0 until barCount) {
                    val v = values[i]
                    ema[i] = alpha * v + (1f - alpha) * ema[i]
                }
                _bars.value = ema.copyOf()
            }
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    suspend fun stop() {
        paused = false
        job?.cancelAndJoin()
        job = null
        record?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        record = null
        try { output?.flush() } catch (_: Throwable) {}
        try { output?.close() } catch (_: Throwable) {}
        output = null
        // decay bars to zero
        ema.fill(0f)
        _bars.value = ema.copyOf()
    }
}
