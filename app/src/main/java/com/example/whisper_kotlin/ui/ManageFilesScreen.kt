package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whisper_kotlin.data.TranscriptionRepository
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFilesScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    transcriptionViewModel: TranscriptionViewModel? = null,
    onBack: () -> Unit
) {
    val uiState = transcriptionViewModel?.uiState?.collectAsState()
    val transcriptionCount = uiState?.value?.savedTranscriptions?.size ?: 0
    var audioFilesSize by remember { mutableStateOf(0L) }
    var audioFilesCount by remember { mutableStateOf(0) }

    // Refresh files info
    LaunchedEffect(transcriptionCount) {
        val audioDir = TranscriptionRepository.audioDirectory()
        if (audioDir != null && audioDir.exists()) {
            val files = audioDir.listFiles() ?: emptyArray()
            audioFilesCount = files.size
            audioFilesSize = files.sumOf { it.length() }
        } else {
            audioFilesCount = 0
            audioFilesSize = 0L
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = "Manage Files",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recordings info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recordings",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$transcriptionCount file${if (transcriptionCount != 1) "s" else ""}",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }

                Divider(color = textColor.copy(alpha = 0.15f))

                // Audio files info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Audio Storage",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        formatFileSize(audioFilesSize),
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }

                Divider(color = textColor.copy(alpha = 0.15f))

                // Audio file count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Audio Files",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$audioFilesCount file${if (audioFilesCount != 1) "s" else ""}",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }

            // Help text
            Text(
                text = "To delete recordings, use the Transcriptions or Recents screen.",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
