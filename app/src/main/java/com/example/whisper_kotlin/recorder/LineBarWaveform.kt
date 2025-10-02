package com.example.whisper_kotlin.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun LineBarWaveform(
    modifier: Modifier,
    bars: FloatArray,
    color: Color,
    backgroundColor: Color? = null
) {
    Canvas(modifier = modifier) {
        val n = bars.size.coerceAtLeast(1)
        val w = size.width
        val h = size.height
        val gap = w * 0.006f
        val barW = (w - gap * (n - 1)) / n
        backgroundColor?.let { drawRect(it) }
        for (i in 0 until n) {
            val x = i * (barW + gap)
            val v = bars[i].coerceIn(0f, 1f)
            val barH = (h * (0.1f + 0.9f * v))
            val y0 = (h - barH) / 2f
            val y1 = y0 + barH
            drawLine(color, start = Offset(x + barW / 2f, y0), end = Offset(x + barW / 2f, y1), strokeWidth = barW)
        }
    }
}
