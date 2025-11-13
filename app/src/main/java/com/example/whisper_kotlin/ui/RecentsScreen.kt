package com.example.whisper_kotlin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TimeGroup(
    val title: String,
    val entries: List<SavedTranscription>
)

private fun getTimeCategory(timestamp: Long, now: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    
    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val weekStart = calendar.apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis
    
    val monthStart = calendar.apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -30)
    }.timeInMillis
    
    return when {
        timestamp >= todayStart -> "Today"
        timestamp >= weekStart -> "This Week"  
        timestamp >= monthStart -> "This Month"
        else -> "A Long Time Ago"
    }
}

@Composable
fun RecentsScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    viewModel: TranscriptionViewModel,
    onOpenTranscription: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val now = System.currentTimeMillis()

    val groupedTranscriptions = remember(uiState.savedTranscriptions) {
        uiState.savedTranscriptions
            .sortedByDescending { it.timestamp }
            .take(20) // Increased from 10 to 20
            .groupBy { getTimeCategory(it.timestamp, now) }
            .map { (title, entries) -> TimeGroup(title, entries) }
            .sortedBy { group ->
                when (group.title) {
                    "Today" -> 0
                    "This Week" -> 1
                    "This Month" -> 2
                    "A Long Time Ago" -> 3
                    else -> 4
                }
            }
    }

    if (groupedTranscriptions.isEmpty() || groupedTranscriptions.all { it.entries.isEmpty() }) {
        Column(modifier = modifier.padding(16.dp)) {
            androidx.compose.material.Text(
                text = "No recent transcriptions.",
                color = textColor.copy(alpha = 0.8f),
                style = TextStyle(fontSize = 16.sp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        groupedTranscriptions.forEachIndexed { groupIndex, group ->
            if (group.entries.isNotEmpty()) {
                // Section header
                item(key = "header_${group.title}") {
                    androidx.compose.material.Text(
                        text = group.title,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = textColor,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
                
                // Entries in this group
                group.entries.forEachIndexed { entryIndex, entry ->
                    item(key = "entry_${entry.id}") {
                        TranscriptionItem(
                            entry = entry,
                            textColor = textColor,
                            onClick = { onOpenTranscription(entry.id) }
                        )
                        
                        // Add divider except for the last item in the last group
                        val isLastGroup = groupIndex == groupedTranscriptions.lastIndex
                        val isLastItem = entryIndex == group.entries.lastIndex
                        if (!(isLastGroup && isLastItem)) {
                            Divider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = textColor.copy(alpha = 0.1f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
        
        // Add bottom padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TranscriptionItem(
    entry: SavedTranscription,
    textColor: Color,
    onClick: () -> Unit
) {
    val firstLine = remember(entry.transcript) {
        entry.transcript
            .lines()
            .firstOrNull()
            ?.trim()
            ?.take(80) // Limit to reasonable length
            ?.let { if (entry.transcript.length > 80) "$it..." else it }
            ?: "Tap to view transcript"
    }
    
    val durationText = remember(entry.transcriptionDurationMs) {
        val durationSec = (entry.transcriptionDurationMs / 1000).toInt()
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = textColor.copy(alpha = 0.1f))
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Waveform icon
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        
        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            androidx.compose.material.Text(
                text = entry.fileLabel,
                color = textColor,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.material.Text(
                text = firstLine,
                color = textColor.copy(alpha = 0.6f),
                style = TextStyle(fontSize = 12.sp)
            )
        }
        
        // Duration
        androidx.compose.material.Text(
            text = durationText,
            color = textColor.copy(alpha = 0.6f),
            style = TextStyle(fontSize = 12.sp)
        )
    }
}
