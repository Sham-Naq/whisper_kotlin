package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whisper_kotlin.ModelManager
import com.example.whisper_kotlin.data.TranscriptionRepository
import com.example.whisper_kotlin.navigation.ThemePreference
import com.example.whisper_kotlin.ui.components.DropdownMenuItem
import com.example.whisper_kotlin.ui.components.ReusableDropdown
import java.io.File
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1),
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    selectedModel: ModelOption? = null,
    transcriptionViewModel: TranscriptionViewModel? = null,
    onSelectModel: ((ModelOption) -> Unit)? = null,
    onNavigateToManageModels: () -> Unit = {},
    onNavigateToManageFiles: () -> Unit = {},
    onNavigateToAppFeatures: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current

    // Collect downloaded models info
    var downloadedModels by remember { mutableStateOf<List<Pair<ModelManager.ModelSpec, Long>>>(emptyList()) }
    var totalModelsSize by remember { mutableStateOf(0L) }

    // Collect files info
    val uiState = transcriptionViewModel?.uiState?.collectAsState()
    val transcriptionCount = uiState?.value?.savedTranscriptions?.size ?: 0
    var audioFilesSize by remember { mutableStateOf(0L) }
    var audioFilesCount by remember { mutableStateOf(0) }

    // Refresh models info
    LaunchedEffect(Unit) {
        val models = ModelManager.availableModels().mapNotNull { spec ->
            val file = ModelManager.getLocalModelFile(context, spec.fileName)
            if (file.exists() && file.length() > 0) {
                spec to file.length()
            } else null
        }
        downloadedModels = models
        totalModelsSize = models.sumOf { it.second }
    }

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

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start
    ) {

        // Storage section header
        Text(
            text = "Storage",
            color = textColor.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        // Storage card with both items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            // Manage Models item
            SettingsMenuItem(
                icon = Icons.Outlined.Memory,
                title = "Manage Models",
                subtitle = "${downloadedModels.size} model${if (downloadedModels.size != 1) "s" else ""} • ${formatFileSize(totalModelsSize)}",
                textColor = textColor,
                onClick = onNavigateToManageModels,
                showBackground = false
            )

            // Divider
            Divider(
                color = textColor.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Manage Files item
            SettingsMenuItem(
                icon = Icons.Outlined.Folder,
                title = "Manage Files",
                subtitle = "$transcriptionCount recording${if (transcriptionCount != 1) "s" else ""} • ${formatFileSize(audioFilesSize)}",
                textColor = textColor,
                onClick = onNavigateToManageFiles,
                showBackground = false
            )
        }

        // Information section header
        Text(
            text = "Information",
            color = textColor.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        // Information card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            SettingsMenuItem(
                icon = Icons.Outlined.Info,
                title = "App Features",
                subtitle = "Learn about what makes this app special",
                textColor = textColor,
                onClick = onNavigateToAppFeatures,
                showBackground = false
            )

            // Divider
            Divider(
                color = textColor.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            )

            SettingsMenuItem(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "How we protect your data and privacy",
                textColor = textColor,
                onClick = onNavigateToPrivacyPolicy,
                showBackground = false
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color,
    onClick: () -> Unit,
    showBackground: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showBackground) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = textColor.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ModelListItem(
    spec: ModelManager.ModelSpec,
    size: Long,
    isSelected: Boolean,
    textColor: Color,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = spec.id,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Currently selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = formatFileSize(size),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        androidx.compose.material3.IconButton(
            onClick = onDelete
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete model",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}