package com.example.whisper_kotlin

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import com.example.whisper_kotlin.ui.components.DropdownMenuItem

data class ModelOption(val id: String, val fileName: String, val url: String)

@Composable
fun ModelSelectorRow(
    textColor: Color,
    isDark: Boolean,
    buttonBg: Color,
    selectedModel: ModelOption?,
    downloadingId: String?,
    progressPct: Int,
    onSelectModel: (ModelOption) -> Unit,
    onRequestDownload: (ModelOption) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val ctx = LocalContext.current
    val options = remember {
        ModelManager.availableModels().map { spec ->
            ModelOption(id = spec.id, fileName = spec.fileName, url = spec.url)
        }
    }
    var localSelectedId by rememberSaveable { mutableStateOf(selectedModel?.id ?: options.firstOrNull()?.id ?: "whisper-tiny") }

    val selectedText = when {
        downloadingId != null -> "Downloading ${downloadingId}… ${progressPct}%"
        selectedModel != null -> selectedModel.id
        else -> localSelectedId
    }

    ReusableDropdown(
        label = "Model",
        selectedText = selectedText,
        textColor = textColor,
        isDark = isDark,
        enabled = downloadingId == null,
        trailingContent = if (downloadingId != null) {
            {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 12.dp, color = textColor.copy(alpha = 0.2f))
                        ) { onCancelDownload() },
                    contentAlignment = Alignment.Center
                ) {
                    CancelIcon(color = textColor)
                }
            }
        } else null,
        dropdownContent = { onDismiss ->
            options.forEach { opt ->
                val isDownloaded = ModelManager.isModelPresent(ctx, opt.fileName)
                DropdownMenuItem(
                    text = opt.id,
                    textColor = textColor,
                    enabled = downloadingId == null,
                    onClick = {
                        if (isDownloaded) {
                            localSelectedId = opt.id
                            onSelectModel(opt)
                            onDismiss()
                        } else {
                            onRequestDownload(opt)
                            onDismiss()
                        }
                    },
                    trailingIcon = if (!isDownloaded) {
                        { DownloadIcon(color = textColor) }
                    } else null
                )
            }
        }
    )
}

@Composable
private fun DownloadIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shaftW = w * 0.18f
        val arrowH = h * 0.6f
        // Arrow shaft
        drawRoundRect(
            color = color,
            topLeft = Offset((w - shaftW) / 2f, 0f),
            size = Size(shaftW, arrowH * 0.6f),
            cornerRadius = CornerRadius(shaftW / 2, shaftW / 2)
        )
        // Arrow head (wide rect to suggest triangle)
        val headH = arrowH * 0.4f
        drawRoundRect(color, topLeft = Offset(w * 0.25f, arrowH * 0.4f), size = Size(w * 0.5f, headH), cornerRadius = CornerRadius(headH / 4, headH / 4))
        // Base line
        drawRoundRect(color, topLeft = Offset(w * 0.2f, h * 0.82f), size = Size(w * 0.6f, h * 0.12f), cornerRadius = CornerRadius(h * 0.06f, h * 0.06f))
    }
}

@Composable
private fun CancelIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w.coerceAtMost(h) * 0.18f)
        // Draw an X
        drawLine(color = color, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = stroke)
    }
}
