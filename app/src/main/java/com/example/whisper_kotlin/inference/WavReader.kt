package com.example.whisper_kotlin

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV reader for 16 kHz mono PCM16 files -> FloatArray [-1,1].
 * Throws IllegalArgumentException for unsupported formats.
 */
object WavReader {
    private const val TARGET_SAMPLE_RATE = 16_000

    data class Info(
        val originalSampleRate: Int,
        val originalChannels: Int,
        val bitsPerSample: Int,
        val formatCode: Int
    )

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
            var resolvedFormat = 0
            var validBitsOverride: Int? = null
            var isFloat = false
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
                        val formatInfo = resolveFormat(audioFormat, bb)
                        resolvedFormat = formatInfo.formatCode
                        validBitsOverride = formatInfo.validBitsPerSample
                        when (resolvedFormat) {
                            1 -> isFloat = false
                            3 -> isFloat = true
                            else -> throw IllegalArgumentException("Unsupported WAV format code: $resolvedFormat")
                        }
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
            if (channels < 1) throw IllegalArgumentException("WAV must have at least one channel")
            if (channels > 8) throw IllegalArgumentException("WAV channel count $channels not supported")
            if (bitsPerSample !in setOf(8, 16, 24, 32, 64)) {
                throw IllegalArgumentException("Unsupported WAV bit depth: $bitsPerSample")
            }
            if (validBitsOverride != null && validBitsOverride > 0) {
                bitsPerSample = validBitsOverride!!
            }
            if (isFloat && bitsPerSample < 32) {
                throw IllegalArgumentException("IEEE float WAV must be 32-bit or 64-bit")
            }
            val bytes = data!!
            val frameStrideBytes = when {
                isFloat && bitsPerSample == 64 -> 8 * channels
                bitsPerSample == 24 -> 3 * channels
                bitsPerSample == 8 -> channels
                else -> (bitsPerSample / 8) * channels
            }
            if (frameStrideBytes == 0) throw IllegalArgumentException("Invalid WAV frame size")
            val frames = bytes.size / frameStrideBytes
            val mono = FloatArray(frames)
            var offset = 0
            for (frame in 0 until frames) {
                var accum = 0f
                for (ch in 0 until channels) {
                    val sample = if (isFloat) {
                        when (bitsPerSample) {
                            32 -> java.lang.Float.intBitsToFloat(readInt32LE(bytes, offset)).also { offset += 4 }
                            64 -> java.lang.Double.longBitsToDouble(readInt64LE(bytes, offset)).also { offset += 8 }.toFloat()
                            else -> throw IllegalArgumentException("Unsupported float bit depth: $bitsPerSample")
                        }
                    } else {
                        when (bitsPerSample) {
                            8 -> ((bytes[offset++].toInt() and 0xFF) - 128) / 128f
                            16 -> (readInt16LE(bytes, offset).toInt() / 32768f).also { offset += 2 }
                            24 -> (readInt24LE(bytes, offset) / 8388608f).also { offset += 3 }
                            32 -> (readInt32LE(bytes, offset).toDouble() / 2147483648.0).also { offset += 4 }.toFloat()
                            else -> throw IllegalArgumentException("Unsupported PCM bit depth: $bitsPerSample")
                        }
                    }
                    accum += sample
                }
                mono[frame] = (accum / channels).coerceIn(-1f, 1f)
            }

            val output = if (sampleRate == TARGET_SAMPLE_RATE) {
                mono
            } else {
                resampleToTarget(mono, sampleRate, TARGET_SAMPLE_RATE)
            }

            return Info(sampleRate, channels, bitsPerSample, resolvedFormat) to output
        }
    }

    private data class FormatInfo(val formatCode: Int, val validBitsPerSample: Int?)

    private fun resolveFormat(format: Int, bb: ByteBuffer): FormatInfo {
        if (format != 0xFFFE) {
            return FormatInfo(format, null)
        }
        if (bb.remaining() < 2) return FormatInfo(format, null)
        val cbSize = bb.short.toInt() and 0xFFFF
        val extraStart = bb.position()
        if (cbSize <= 0 || bb.remaining() < cbSize) {
            bb.position((extraStart + cbSize).coerceAtMost(bb.limit()))
            return FormatInfo(format, null)
        }
        var resolved = format
        var validBits: Int? = null
        if (cbSize >= 2 && bb.remaining() >= 2) {
            val vb = bb.short.toInt() and 0xFFFF
            if (vb != 0) validBits = vb
        }
        if (cbSize >= 6 && bb.remaining() >= 4) {
            bb.int // channel mask (ignored)
        }
        if (cbSize >= 22 && bb.remaining() >= 16) {
            val guid = ByteArray(16)
            bb.get(guid)
            val subFormat = (guid[0].toInt() and 0xFF) or ((guid[1].toInt() and 0xFF) shl 8)
            resolved = subFormat
        }
        bb.position(extraStart + cbSize)
        return FormatInfo(resolved, validBits)
    }

    private fun readInt16LE(bytes: ByteArray, offset: Int): Short {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return ((b1 shl 8) or b0).toShort()
    }

    private fun readInt24LE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt()
        var value = b0 or (b1 shl 8) or (b2 shl 16)
        if (value and 0x800000 != 0) {
            value = value or -0x1000000
        }
        return value
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readInt64LE(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((bytes[offset + i].toLong() and 0xFFL) shl (8 * i))
        }
        return result
    }

    private fun resampleToTarget(
        samples: FloatArray,
        fromRate: Int,
        toRate: Int
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val targetLength = ((samples.size.toLong() * toRate) / fromRate).coerceAtLeast(1)
        val step = fromRate.toDouble() / toRate.toDouble()
        val out = FloatArray(targetLength.toInt())
        var srcPos = 0.0
        for (i in out.indices) {
            val idx = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val nextIdx = (idx + 1).coerceAtMost(samples.lastIndex)
            val frac = (srcPos - idx).toFloat()
            val sample = if (idx == nextIdx) {
                samples[idx]
            } else {
                samples[idx] * (1f - frac) + samples[nextIdx] * frac
            }
            out[i] = sample
            srcPos += step
        }
        return out
    }
}
