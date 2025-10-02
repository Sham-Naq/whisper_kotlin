package com.example.whisper_kotlin.recorder

import java.io.File
import java.io.RandomAccessFile

object WavWriter16kMonoPcm16 {
    fun wrapRawPcmToWav(rawPcmFile: File, wavFile: File, sampleRate: Int = 16_000) {
        val pcmBytes = rawPcmFile.length()
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0)
            // RIFF header
            raf.writeBytes("RIFF")
            raf.writeIntLE((36 + pcmBytes).toInt())
            raf.writeBytes("WAVE")
            // fmt chunk
            raf.writeBytes("fmt ")
            raf.writeIntLE(16) // PCM
            raf.writeShortLE(1) // audio format = 1 (PCM)
            raf.writeShortLE(1) // channels = 1
            raf.writeIntLE(sampleRate)
            val byteRate = sampleRate * 2 // 16-bit mono
            raf.writeIntLE(byteRate)
            raf.writeShortLE(2) // block align (channels * bytesPerSample)
            raf.writeShortLE(16) // bits per sample
            // data chunk
            raf.writeBytes("data")
            raf.writeIntLE(pcmBytes.toInt())
            // append raw pcm
            rawPcmFile.inputStream().use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    raf.write(buf, 0, n)
                }
            }
        }
    }

    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 24) and 0xFF).toByte()
        ))
    }
    private fun RandomAccessFile.writeShortLE(v: Int) {
        write(byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte()
        ))
    }
}
