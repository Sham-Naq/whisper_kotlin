package com.example.whisper_kotlin

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV reader for 16 kHz mono PCM16 files -> FloatArray [-1,1].
 * Throws IllegalArgumentException for unsupported formats.
 */
object WavReader {
    data class Info(val sampleRate: Int, val channels: Int, val bitsPerSample: Int)

    fun readPcm16Mono16k(input: InputStream): Pair<Info, FloatArray> {
        input.use { ins ->
            fun readFully(n: Int): ByteArray {
                val buf = ByteArray(n)
                var off = 0
                while (off < n) {
                    val r = ins.read(buf, off, n - off)
                    if (r <= 0) throw IllegalArgumentException("Unexpected EOF in WAV header")
                    off += r
                }
                return buf
            }

            val riff = readFully(12)
            if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE")
                throw IllegalArgumentException("Not a RIFF/WAVE file")

            var fmtFound = false
            var dataFound = false
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var data: ByteArray? = null

            while (true) {
                val hdr = ByteArray(8)
                val read = ins.read(hdr)
                if (read < 8) break
                val id = String(hdr, 0, 4)
                val size = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val chunk = readFully(size + (size and 1)) // word aligned
                when (id) {
                    "fmt " -> {
                        val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                        val audioFormat = bb.short.toInt() and 0xFFFF
                        channels = bb.short.toInt() and 0xFFFF
                        sampleRate = bb.int
                        bb.int // byteRate
                        bb.short // blockAlign
                        bitsPerSample = bb.short.toInt() and 0xFFFF
                        if (audioFormat != 1) throw IllegalArgumentException("Only PCM supported")
                        fmtFound = true
                    }
                    "data" -> {
                        data = chunk.copyOf(size)
                        dataFound = true
                    }
                }
                if (fmtFound && dataFound) break
            }

            if (!fmtFound || !dataFound) throw IllegalArgumentException("Invalid WAV (missing fmt/data)")
            if (channels != 1 || sampleRate != 16000 || bitsPerSample != 16) {
                throw IllegalArgumentException("WAV must be mono, 16kHz, 16-bit PCM")
            }
            val bytes = data!!
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(bytes.size / 2)
            var i = 0
            while (bb.hasRemaining()) {
                val s = bb.short
                out[i++] = (s / 32768.0f).coerceIn(-1f, 1f)
            }
            return Info(sampleRate, channels, bitsPerSample) to out
        }
    }
}
