package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whisper_kotlin.ModelManager
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
fun ManageModelsScreen(
    modifier: Modifier = Modifier,
    textColor: Color,
    selectedModel: ModelOption? = null,
    onSelectModel: ((ModelOption) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<ModelManager.ModelSpec?>(null) }

    // Collect downloaded models info
    var downloadedModels by remember { mutableStateOf<List<Pair<ModelManager.ModelSpec, Long>>>(emptyList()) }
    var totalModelsSize by remember { mutableStateOf(0L) }

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            text = "Manage Models",
                            style = MaterialTheme.typography.titleMedium
                        )
                        androidx.compose.material3.Text(
                            text = "Total: ${formatFileSize(totalModelsSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (downloadedModels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No models downloaded yet.",
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    downloadedModels.forEach { (spec, size) ->
                        ModelListItem(
                            spec = spec,
                            size = size,
                            isSelected = selectedModel?.id == spec.id,
                            textColor = textColor,
                            onDelete = {
                                modelToDelete = spec
                                showDeleteModelDialog = true
                            }
                        )
                    }
                }
            }

        }

        // Delete model confirmation dialog
        if (showDeleteModelDialog && modelToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteModelDialog = false
                    modelToDelete = null
                },
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = textColor,
                title = {
                    Text("Delete model?", color = textColor, fontWeight = FontWeight.SemiBold)
                },
                text = {
                    Text(
                        "This will remove ${modelToDelete?.id} from local storage. You'll need to download it again to use it later.",
                        color = textColor.copy(alpha = 0.85f)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val spec = modelToDelete
                        if (spec != null) {
                            val file = ModelManager.getLocalModelFile(context, spec.fileName)
                            if (file.exists()) {
                                file.delete()
                            }
                            // Update the list
                            downloadedModels = downloadedModels.filterNot { it.first.id == spec.id }
                            totalModelsSize = downloadedModels.sumOf { it.second }

                            // If we deleted the selected model, switch to default
                            if (selectedModel?.id == spec.id) {
                                val fallbackSpec = ModelManager.defaultModel()
                                if (fallbackSpec != null && onSelectModel != null) {
                                    val fallbackOption = ModelOption(fallbackSpec.id, fallbackSpec.fileName, fallbackSpec.url)
                                    onSelectModel(fallbackOption)
                                }
                            }
                        }
                        showDeleteModelDialog = false
                        modelToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteModelDialog = false
                        modelToDelete = null
                    }) {
                        Text("Cancel")
                    }
                }
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = spec.id,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Currently selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = formatFileSize(size),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
        androidx.compose.material3.IconButton(
            onClick = onDelete
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete model",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
