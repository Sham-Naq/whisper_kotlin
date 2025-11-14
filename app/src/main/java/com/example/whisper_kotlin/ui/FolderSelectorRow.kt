package com.example.whisper_kotlin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import com.example.whisper_kotlin.ui.components.DropdownMenuItem

@Composable
fun FolderSelectorRow(
    textColor: Color,
    isDark: Boolean,
    buttonBg: Color,
    folders: List<Folder>,
    selectedFolderId: Long?,
    onFolderSelected: (Long?) -> Unit
) {
    val selectedLabel = selectedFolderId?.let { id ->
        folders.firstOrNull { it.id == id }?.name
    } ?: "None"

    ReusableDropdown(
        label = "Folder",
        selectedText = selectedLabel,
        textColor = textColor,
        isDark = isDark,
        dropdownContent = { onDismiss ->
            // Build a tree to render nested folders with indentation
            val childrenByParent = folders.groupBy { it.parentId }

            @Composable
            fun renderItem(indent: Int, id: Long?, name: String) {
                val prefix = if (indent > 0) ("  ".repeat(indent) + "• ") else ""
                DropdownMenuItem(
                    text = prefix + name,
                    textColor = textColor,
                    onClick = {
                        onFolderSelected(id)
                        onDismiss()
                    }
                )
            }

            @Composable
            fun traverse(parentId: Long?, indent: Int) {
                val children = childrenByParent[parentId].orEmpty().sortedBy { it.name.lowercase() }
                for (child in children) {
                    renderItem(indent, child.id, child.name)
                    traverse(child.id, indent + 1)
                }
            }

            renderItem(0, null, "None")
            traverse(parentId = null, indent = 0)
        }
    )
}
