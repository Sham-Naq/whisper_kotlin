package com.example.whisper_kotlin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * A reusable dropdown component with a clean, modern design.
 *
 * @param label The label to display above the dropdown (e.g., "Select model")
 * @param selectedText The currently selected option text to display
 * @param textColor The text color for the dropdown
 * @param isDark Whether the app is in dark mode
 * @param modifier Modifier for the dropdown container
 * @param enabled Whether the dropdown is enabled for interaction
 * @param trailingContent Optional content to display after the dropdown button (e.g., cancel icon)
 * @param dropdownContent The content to show in the popup menu
 */
@Composable
fun ReusableDropdown(
    label: String,
    selectedText: String,
    textColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
    dropdownContent: @Composable (onDismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        BasicText(
            text = label,
            style = TextStyle(
                color = textColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )

        // Dropdown button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = textColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = textColor.copy(alpha = 0.1f))
                ) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .onGloballyPositioned { coords ->
                    anchorBounds = coords.boundsInWindow()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = selectedText,
                style = TextStyle(
                    color = textColor,
                    fontSize = 15.sp
                )
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Open dropdown",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Trailing content (e.g., cancel button)
        trailingContent?.invoke()
    }

    // Popup menu
    if (expanded) {
        val density = LocalDensity.current
        val cardBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFFAFAFA)
        val borderColor = if (isDark) Color(0xFF404040) else Color(0xFFE0E0E0)
        val anchorSnapshot = anchorBounds
        val verticalPaddingPx = with(density) { 8.dp.roundToPx() }

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

        val anchorWidthDp = if (anchorSnapshot.isEmpty) 0.dp else with(density) {
            anchorSnapshot.width.toDp()
        }
        val popupMinWidth = anchorWidthDp.coerceAtLeast(200.dp)

        Popup(
            popupPositionProvider = popupPositionProvider,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = popupMinWidth)
                    .heightIn(max = 300.dp)
                    .background(cardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    item {
                        dropdownContent { expanded = false }
                    }
                }
            }
        }
    }
}

/**
 * A standard dropdown menu item.
 *
 * @param text The text to display
 * @param textColor The text color
 * @param onClick Action to perform when clicked
 * @param modifier Modifier for the item
 * @param enabled Whether the item is enabled
 * @param trailingIcon Optional trailing icon composable
 */
@Composable
fun DropdownMenuItem(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = textColor.copy(alpha = 0.15f))
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 15.sp,
                fontWeight = fontWeight
            )
        )
        trailingIcon?.invoke()
    }
}
