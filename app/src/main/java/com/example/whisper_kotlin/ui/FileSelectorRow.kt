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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
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
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val selectedLabel = when (source) {
        is AudioSource.Asset -> source.assetPath.substringAfterLast('/')
        is AudioSource.File -> java.io.File(source.path).name
    }
    // Anchor bounds in window coordinates for popup placement
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title - plain text, not clickable
            BasicText(
                "Select file",
                style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold)
            )

            val menuWidth = remember(labelSize, density) {
                val width = with(density) { labelSize.width.toDp() }
                if (width < 220.dp) 220.dp else width
            }

            Box(
                modifier = Modifier
                    .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .onGloballyPositioned { coordinates ->
                        labelSize = coordinates.size
                        anchorBounds = coordinates.boundsInWindow()
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.Gray.copy(alpha = 0.3f))
                    ) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = selectedLabel,
                        style = TextStyle(color = textColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "File options",
                        tint = textColor
                    )
                }

                if (expanded) {
                    val cardBg = if (isDark) Color(0xFF232323) else Color.White
                    val borderColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
                    val anchorSnapshot = anchorBounds
                    val verticalPaddingPx = with(density) { 6.dp.roundToPx() }
                    val popupPositionProvider = remember(anchorSnapshot, verticalPaddingPx) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                parentBounds: androidx.compose.ui.unit.IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize
                            ): IntOffset {
                                if (anchorSnapshot.isEmpty) return IntOffset.Zero
                                val rawLeft = when (layoutDirection) {
                                    LayoutDirection.Ltr -> anchorSnapshot.left.toInt()
                                    LayoutDirection.Rtl -> (anchorSnapshot.right - popupContentSize.width).toInt()
                                }
                                val rawTop = anchorSnapshot.bottom.toInt() + verticalPaddingPx
                                val maxLeft = windowSize.width - popupContentSize.width
                                val maxTop = windowSize.height - popupContentSize.height
                                val clampedLeft = if (maxLeft > 0) rawLeft.coerceIn(0, maxLeft) else 0
                                val clampedTop = if (maxTop > 0) rawTop.coerceIn(0, maxTop) else 0
                                return IntOffset(clampedLeft, clampedTop)
                            }
                        }
                    }
                    val anchorWidthDp = if (anchorSnapshot.isEmpty) 0.dp else with(density) { anchorSnapshot.width.toDp() }
                    val popupWidth = anchorWidthDp.coerceAtLeast(220.dp)

                    Popup(
                        popupPositionProvider = popupPositionProvider,
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = { expanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .background(cardBg, RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp)
                                .width(popupWidth)
                        ) {
                            // Built-in asset option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSourceChanged(AudioSource.Asset("samples/samples_jfk.wav"))
                                        expanded = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText("samples_jfk.wav", style = TextStyle(color = textColor))
                            }

                            // Cached files
                            cachedFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSourceChanged(AudioSource.File(file.absolutePath))
                                            expanded = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText(file.name, style = TextStyle(color = textColor))
                                }
                            }

                            // Upload option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expanded = false
                                        launcher.launch("audio/*")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText("Upload…", style = TextStyle(color = textColor, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
    }
}