package com.example.whisper_kotlin

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.whispercpp.java.whisper.WhisperCpuConfig
import com.whispercpp.java.whisper.WhisperLib
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TIMESTAMP_LOG_TAG = "TimestampWhisperLib"

data class WhisperTranscription(
    val plain: String,
    val timestamped: String
)

class TimestampWhisperContext private constructor(private var ptr: Long) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Throws(ExecutionException::class, InterruptedException::class)
    fun transcribeData(data: FloatArray): WhisperTranscription {
        return executor.submit(Callable {
            if (ptr == 0L) {
                error("Attempt to transcribe using a released context")
            }
            val numThreads = WhisperCpuConfig.getPreferredThreadCount()
            Log.d(TIMESTAMP_LOG_TAG, "Selecting $numThreads threads")
            synchronized(lock) {
                WhisperLib.fullTranscribe(ptr, numThreads, data)
                val textCount = WhisperLib.getTextSegmentCount(ptr)
                val plainBuilder = StringBuilder()
                val timestampBuilder = StringBuilder()
                for (i in 0 until textCount) {
                    val segment = WhisperLib.getTextSegment(ptr, i)
                    plainBuilder.append(segment)

                    val t0 = WhisperLib.getTextSegmentT0(ptr, i)
                    val t1 = WhisperLib.getTextSegmentT1(ptr, i)
                    timestampBuilder.append("[")
                    timestampBuilder.append(toTimestamp(t0))
                    timestampBuilder.append(" --> ")
                    timestampBuilder.append(toTimestamp(t1))
                    timestampBuilder.append("]: ")
                    timestampBuilder.append(segment)
                    timestampBuilder.append('\n')
                }
                WhisperTranscription(
                    plain = plainBuilder.toString(),
                    timestamped = timestampBuilder.toString()
                )
            }
        }).get()
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun benchMemory(nThreads: Int): String = executor.submit(Callable {
        WhisperLib.benchMemcpy(nThreads)
    }).get()

    @Throws(ExecutionException::class, InterruptedException::class)
    fun benchGgmlMulMat(nThreads: Int): String = executor.submit(Callable {
        WhisperLib.benchGgmlMulMat(nThreads)
    }).get()

    @Throws(ExecutionException::class, InterruptedException::class)
    fun release() {
        if (executor.isShutdown) {
            return
        }
        executor.submit {
            if (ptr != 0L) {
                WhisperLib.freeContext(ptr)
                ptr = 0
            }
        }.get()
        executor.shutdown()
    }

    protected fun finalize() {
        try {
            release()
        } catch (ignored: Exception) {
            // Ignore cleanup exceptions during finalization
        }
    }

    companion object {
        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.O)
        fun createContextFromFile(modelPath: String): TimestampWhisperContext {
            val ptr = WhisperLib.initContext(modelPath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create context with path $modelPath")
            }
            return TimestampWhisperContext(ptr)
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.O)
        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): TimestampWhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create context from asset $assetPath")
            }
            return TimestampWhisperContext(ptr)
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.O)
        fun createContextFromStream(stream: InputStream): TimestampWhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create context from provided stream")
            }
            return TimestampWhisperContext(ptr)
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.O)
        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}

private fun toTimestamp(t: Long): String {
    var msec = t * 10
    val hr = msec / (1000 * 60 * 60)
    msec -= hr * (1000 * 60 * 60)
    val min = msec / (1000 * 60)
    msec -= min * (1000 * 60)
    val sec = msec / 1000
    msec -= sec * 1000
    return String.format("%02d:%02d:%02d.%03d", hr, min, sec, msec)
}
