package com.example.whisper_kotlin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedBgColor: Color = Color(0xFFE0E0E0),
    unselectedBgColor: Color = Color(0xFFF5F5F5),
    selectedBorderColor: Color = Color(0xFF9E9E9E),
    unselectedBorderColor: Color = Color(0xFFE0E0E0),
    drawContainer: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg = if (selected) selectedBgColor else unselectedBgColor
    val borderColor = if (selected) selectedBorderColor else unselectedBorderColor
    val shape = RoundedCornerShape(10.dp)
    val base = if (drawContainer) {
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
    } else {
        modifier
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = base
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = drawContainer,
                    color = Color.Gray.copy(alpha = 0.3f)
                ),
                onClick = onClick
            )
            .padding(vertical = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
