package com.example.whisper_kotlin.data

import android.content.Context
import com.example.whisper_kotlin.SavedTranscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object TranscriptionRepository {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    private val mutex = Mutex()
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun load(): List<SavedTranscription> = mutex.withLock {
        val context = appContext ?: return emptyList()
        val file = storageFile(context)
        if (!file.exists() || file.length() == 0L) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<SavedTranscription>>(file.readText()) }
                .getOrElse { emptyList() }
        }
    }

    suspend fun persist(entries: List<SavedTranscription>) = mutex.withLock {
        val context = appContext ?: return@withLock
        val file = storageFile(context)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(entries))
    }

    fun audioDirectory(): File? {
        val context = appContext ?: return null
        return File(storageDir(context), "audio").apply { mkdirs() }
    }

    private fun storageDir(context: Context): File = File(context.filesDir, "transcriptions").apply { mkdirs() }
    private fun storageFile(context: Context): File = File(storageDir(context), "transcriptions.json")
}
