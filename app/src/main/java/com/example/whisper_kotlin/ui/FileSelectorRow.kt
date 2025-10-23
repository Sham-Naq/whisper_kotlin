package com.example.whisper_kotlin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val selectedLabel = when (source) {
        is AudioSource.Asset -> source.assetPath.substringAfterLast('/')
        is AudioSource.File -> java.io.File(source.path).name
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(buttonBg, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.Gray.copy(alpha = 0.3f))
                    ) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Select file", style = TextStyle(color = textColor, fontWeight = FontWeight.Medium))
            }

            val menuWidth = remember(labelSize, density) {
                val width = with(density) { labelSize.width.toDp() }
                if (width < 220.dp) 220.dp else width
            }

            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates -> labelSize = coordinates.size }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.Gray.copy(alpha = 0.3f))
                    ) { expanded = !expanded }
            ) {
                BasicText(
                    text = selectedLabel,
                    style = TextStyle(color = textColor)
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(menuWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color = if (isDark) Color(0xFF232323) else Color.White,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                ) {
                    DropdownMenuItem(onClick = {
                        onSourceChanged(AudioSource.Asset("samples/samples_jfk.wav"))
                        expanded = false
                    }) {
                        BasicText("samples_jfk.wav", style = TextStyle(color = textColor))
                    }

                    cachedFiles.forEach { file ->
                        DropdownMenuItem(onClick = {
                            onSourceChanged(AudioSource.File(file.absolutePath))
                            expanded = false
                        }) {
                            BasicText(file.name, style = TextStyle(color = textColor))
                        }
                    }

                    DropdownMenuItem(onClick = {
                        expanded = false
                        launcher.launch("audio/*")
                    }) {
                        BasicText(
                            "Upload…",
                            style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}