package com.example.whisper_kotlin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import com.example.whisper_kotlin.ui.components.DropdownMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Sealed so Asset/File can extend it and when() can be exhaustive
sealed class AudioSource {
    data class Asset(val assetPath: String) : AudioSource()
    data class File(val path: String) : AudioSource()
}

@Composable
fun FileSelectorRow(
    textColor: Color,
    isDark: Boolean,
    buttonBg: Color,
    source: AudioSource,
    onSourceChanged: (AudioSource) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedDir = remember { java.io.File(ctx.cacheDir, "uploads").apply { mkdirs() } }

    // Cached files shown in dropdown
    val cachedFiles = remember {
        mutableStateListOf<java.io.File>().apply { addAll(cachedDir.listFiles()?.toList() ?: emptyList()) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val name = runCatching {
                        val c = ctx.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null
                        )
                        c?.use { if (it.moveToFirst()) it.getString(0) else null }
                    }.getOrNull() ?: ("upload_" + System.currentTimeMillis() + ".wav")

                    val target = java.io.File(cachedDir, name)
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        target.outputStream().use { outs -> ins.copyTo(outs) }
                    }

                    withContext(Dispatchers.Main) {
                        onSourceChanged(AudioSource.File(target.absolutePath))
                        if (cachedFiles.none { it.absolutePath == target.absolutePath }) {
                            cachedFiles.add(0, target)
                        }
                    }
                } catch (_: Throwable) {
                    // TODO: show error to user if needed
                }
            }
        }
    }

    val selectedLabel = when (source) {
        is AudioSource.Asset -> source.assetPath.substringAfterLast('/')
        is AudioSource.File -> java.io.File(source.path).name
    }

    ReusableDropdown(
        label = "File",
        selectedText = selectedLabel,
        textColor = textColor,
        isDark = isDark,
        dropdownContent = { onDismiss ->
            // Built-in asset option
            DropdownMenuItem(
                text = "samples_jfk.wav",
                textColor = textColor,
                onClick = {
                    onSourceChanged(AudioSource.Asset("samples/samples_jfk.wav"))
                    onDismiss()
                }
            )

            // Cached files
            cachedFiles.forEach { file ->
                DropdownMenuItem(
                    text = file.name,
                    textColor = textColor,
                    onClick = {
                        onSourceChanged(AudioSource.File(file.absolutePath))
                        onDismiss()
                    }
                )
            }

            // Upload option
            DropdownMenuItem(
                text = "Upload…",
                textColor = textColor,
                fontWeight = FontWeight.SemiBold,
                onClick = {
                    onDismiss()
                    launcher.launch("audio/*")
                }
            )
        }
    )
}