package com.example.whisper_kotlin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect

@Composable
fun FolderSelectorRow(
    textColor: Color,
    isDark: Boolean,
    buttonBg: Color,
    folders: List<Folder>,
    selectedFolderId: Long?,
    onFolderSelected: (Long?) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)

    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Title chip (non-clickable)
            Box(
                modifier = Modifier
                    .background(buttonBg, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "Folder",
                    style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                )
            }

            // Current selection chip (clickable)
            Box(
                modifier = Modifier
                    .border(1.dp, textColor.copy(alpha = 0.25f), shape)
                    .background(Color.Transparent, shape)
                    .onGloballyPositioned { coords ->
                        anchorBounds = coords.boundsInWindow()
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.Gray.copy(alpha = 0.3f)),
                        onClick = { showMenu = true }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = selectedFolderId?.let { id ->
                        folders.firstOrNull { it.id == id }?.name
                    } ?: "None"
                    BasicText(
                        text = label,
                        style = TextStyle(color = textColor, fontWeight = FontWeight.Medium)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Open folder menu",
                        tint = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        if (showMenu && anchorBounds != null) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val x = anchorBounds.left
                        val y = anchorBounds.bottom + 6
                        return IntOffset(x, y)
                    }
                },
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .background(buttonBg, RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp)
                ) {
                    @Composable
                    fun renderItem(indent: Int, id: Long?, name: String) {
                        Row(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = Color.Gray.copy(alpha = 0.25f)),
                                    onClick = {
                                        onFolderSelected(id)
                                        showMenu = false
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.height(0.dp))
                            val prefix = if (indent > 0) ("  ".repeat(indent) + "• ") else ""
                            BasicText(
                                text = prefix + name,
                                style = TextStyle(color = textColor)
                            )
                        }
                    }

                    renderItem(0, null, "None")

                    // Build a tree to render nested folders with indentation
                    val childrenByParent = folders.groupBy { it.parentId }

                    @Composable
                    fun traverse(parentId: Long?, indent: Int) {
                        val children = childrenByParent[parentId].orEmpty().sortedBy { it.name.lowercase() }
                        for (child in children) {
                            renderItem(indent, child.id, child.name)
                            traverse(child.id, indent + 1)
                        }
                    }

                    traverse(parentId = null, indent = 0)
                }
            }
        }
    }
}
