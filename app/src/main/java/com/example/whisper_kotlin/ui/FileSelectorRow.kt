package com.example.whisper_kotlin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

                    // Switch back to Main for state updates
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

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (source) {
        is AudioSource.Asset -> source.assetPath.substringAfterLast('/')
        is AudioSource.File -> java.io.File(source.path).name
    }

    // Anchor for popup
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(buttonBg, RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Select file", style = TextStyle(color = textColor, fontWeight = FontWeight.Medium))
            }

            BasicText(
                selectedLabel,
                style = TextStyle(color = textColor),
                modifier = Modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() }
            )
        }

        if (expanded) {
            val cardBg = if (isDark) Color(0xFF232323) else Color(0xFFFFFFFF)
            val border = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(anchorBounds.left.toInt(), anchorBounds.bottom.toInt()),
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp)
                        .width(200.dp)
                ) {
                    // Default JFK asset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSourceChanged(AudioSource.Asset("samples/samples_jfk.wav"))
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText("samples_jfk.wav", style = TextStyle(color = textColor))
                    }

                    // Cached files
                    cachedFiles.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSourceChanged(AudioSource.File(f.absolutePath))
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText(f.name, style = TextStyle(color = textColor))
                        }
                    }

                    // Upload action (SAF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                launcher.launch("audio/*")
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText("Upload…", style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}