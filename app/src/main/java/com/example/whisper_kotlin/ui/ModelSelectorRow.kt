package com.example.whisper_kotlin

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.Canvas
import androidx.compose.material.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val scope = rememberCoroutineScope()
    // Populate from ModelManager catalog so all supported models appear
    val options = remember {
        ModelManager.availableModels().map { spec ->
            ModelOption(id = spec.id, fileName = spec.fileName, url = spec.url)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var localSelectedId by rememberSaveable { mutableStateOf(selectedModel?.id ?: options.firstOrNull()?.id ?: "whisper-tiny") }

    // Anchor bounds in window coordinates for popup placement
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 0.dp, vertical = 0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Select model button
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
                BasicText("Select model", style = TextStyle(color = textColor, fontWeight = FontWeight.Medium))
            }
            // Selected model label
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = when {
                        downloadingId != null -> "Downloading ${downloadingId}… ${progressPct}%"
                        selectedModel != null -> selectedModel.id
                        else -> localSelectedId
                    },
                    style = TextStyle(color = textColor),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        anchorBounds = coords.boundsInWindow()
                    }
                )
                if (downloadingId != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 12.dp, color = Color.Gray.copy(alpha = 0.3f))
                            ) { onCancelDownload() },
                        contentAlignment = Alignment.Center
                    ) {
                        CancelIcon(color = textColor)
                    }
                }
            }
        }

        if (expanded) {
            val cardBg = if (isDark) Color(0xFF232323) else Color(0xFFFFFFFF)
            val border = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
            val density = LocalDensity.current
            val anchorSnapshot = anchorBounds
            val verticalPaddingPx = with(density) { 6.dp.roundToPx() }
            val popupPositionProvider = remember(anchorSnapshot, verticalPaddingPx) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        parentBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        if (anchorSnapshot.isEmpty) {
                            return IntOffset.Zero
                        }
                        val rawLeft = when (layoutDirection) {
                            LayoutDirection.Ltr -> anchorSnapshot.left.roundToInt()
                            LayoutDirection.Rtl -> (anchorSnapshot.right - popupContentSize.width).roundToInt()
                        }
                        val rawTop = anchorSnapshot.bottom.roundToInt() + verticalPaddingPx
                        val maxLeft = windowSize.width - popupContentSize.width
                        val maxTop = windowSize.height - popupContentSize.height
                        val clampedLeft = if (maxLeft > 0) rawLeft.coerceIn(0, maxLeft) else 0
                        val clampedTop = if (maxTop > 0) rawTop.coerceIn(0, maxTop) else 0
                        return IntOffset(clampedLeft, clampedTop)
                    }
                }
            }
            val anchorWidthDp = if (anchorSnapshot.isEmpty) 0.dp else with(density) { anchorSnapshot.width.toDp() }
            val menuWidth = anchorWidthDp.coerceAtLeast(180.dp)
            // Show as an overlay popup positioned under the anchor label
            Popup(
                popupPositionProvider = popupPositionProvider,
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp)
                        .width(menuWidth)
                ) {
                    options.forEach { opt ->
                        val isDownloaded = ModelManager.isModelPresent(ctx, opt.fileName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = downloadingId == null) {
                                    if (isDownloaded) {
                                        localSelectedId = opt.id
                                        onSelectModel(opt)
                                        expanded = false
                                    } else {
                                        onRequestDownload(opt)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText(text = opt.id, style = TextStyle(color = textColor))
                            if (!isDownloaded) {
                                DownloadIcon(color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
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
