package com.example.whisper_kotlin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whisper_kotlin.navigation.BottomTab
import com.example.whisper_kotlin.navigation.ThemePreference
import com.example.whisper_kotlin.navigation.bottomTabOrder
import com.example.whisper_kotlin.recorder.RecorderCommand

enum class SettingsScreen {
    ManageModels,
    ManageFiles,
    AppFeatures,
    PrivacyPolicy
}

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun HomeScreen(
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(BottomTab.Transcription) }
    var detailEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settingsScreen by rememberSaveable { mutableStateOf<SettingsScreen?>(null) }

    val disabledTabs = remember { setOf(BottomTab.Chats) }
    val navigableTabs = remember(disabledTabs) { bottomTabOrder.filterNot { it in disabledTabs } }

    var isRecorderSheetVisible by rememberSaveable { mutableStateOf(false) }
    var showLanguageSelection by rememberSaveable { mutableStateOf(false) }
    var selectedLanguageCode by rememberSaveable { mutableStateOf("en") }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val primaryTextColor = MaterialTheme.colorScheme.onBackground

    var recorderCommand by remember { mutableStateOf<RecorderCommand?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecorderPaused by remember { mutableStateOf(false) }

    val transcriptionViewModel: TranscriptionViewModel = viewModel()

    val selectedModelState = remember { mutableStateOf<ModelOption?>(null) }
    val selectedModel by selectedModelState
    var audioSource by remember { mutableStateOf<AudioSource>(AudioSource.Asset("samples/samples_jfk.wav")) }
    var isModelDownloading by remember { mutableStateOf(false) }

    val detailEntry = detailEntryId?.let { transcriptionViewModel.getTranscription(it) }
    val uiState by transcriptionViewModel.uiState.collectAsState()
    val recorderSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetState ->
            if (targetState == androidx.compose.material3.SheetValue.Hidden && (isRecording || uiState.isTranscribing)) {
                false
            } else {
                true
            }
        }
    )
    val currentFolder = uiState.currentFolderId?.let { folderId ->
        uiState.folders.firstOrNull { it.id == folderId }
    }
    val isInFolder = uiState.currentFolderId != null
    val isDetailTranscribing = detailEntry?.status == TranscriptionStatus.Pending && uiState.isTranscribing

    val displayedTab = if (isRecorderSheetVisible) BottomTab.Recorder else activeTab

    val currentHeader = when {
        showLanguageSelection -> "Language"
        detailEntry != null -> "Transcript"
        settingsScreen == SettingsScreen.ManageModels -> "Manage Models"
        settingsScreen == SettingsScreen.ManageFiles -> "Manage Files"
        settingsScreen == SettingsScreen.AppFeatures -> "App Features"
        settingsScreen == SettingsScreen.PrivacyPolicy -> "Privacy Policy"
        displayedTab == BottomTab.Transcription && currentFolder != null -> currentFolder.name
        else -> displayedTab.topBar
    }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val openTranscription: (Long) -> Unit = { id ->
        detailEntryId = id
        isRecorderSheetVisible = false
    }

    BackHandler(enabled = detailEntry != null) {
        if (!isDetailTranscribing) {
            detailEntryId = null
        }
    }

    BackHandler(enabled = isRecorderSheetVisible && !isRecording && !uiState.isTranscribing) {
        isRecorderSheetVisible = false
    }

    BackHandler(enabled = settingsScreen != null) {
        settingsScreen = null
    }

    BackHandler(enabled = showLanguageSelection) {
        showLanguageSelection = false
        isRecorderSheetVisible = true
    }

    val shouldReturnToTranscriptions =
        detailEntry == null && activeTab != BottomTab.Transcription && !isRecorderSheetVisible && settingsScreen == null
    if (shouldReturnToTranscriptions) {
        BackHandler {
            activeTab = BottomTab.Transcription
        }
    }

    LaunchedEffect(detailEntryId, detailEntry) {
        if (detailEntryId != null && detailEntry == null) {
            detailEntryId = null
        }
    }

    // Confirm deletion dialog
    if (showDeleteDialog && detailEntry != null) {
        DeleteConfirmationDialog(
            entry = detailEntry,
            primaryTextColor = primaryTextColor,
            onConfirm = {
                transcriptionViewModel.deleteTranscription(context, detailEntry.id)
                showDeleteDialog = false
                detailEntryId = null
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!(displayedTab == BottomTab.Settings && settingsScreen != null) && !showLanguageSelection) {
                    HomeTopBar(
                        currentHeader = currentHeader,
                        isInFolder = isInFolder,
                        activeTab = activeTab,
                        onFolderBackClick = {
                            val parentId = currentFolder?.parentId
                            transcriptionViewModel.navigateToFolder(parentId)
                        }
                    )
                }
            },
            bottomBar = {
                // Only show bottom bar when not viewing detail or language selection
                if (detailEntry == null && !showLanguageSelection) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HomeBottomBar(
                            activeTab = displayedTab,
                            isInFolder = isInFolder,
                            isDark = isDark,
                            disabledTabs = disabledTabs,
                            onTabSelected = { tab ->
                                when {
                                    tab == BottomTab.Recorder -> {
                                        isRecorderSheetVisible = true
                                    }

                                    tab !in disabledTabs -> {
                                        isRecorderSheetVisible = false
                                        detailEntryId = null
                                        settingsScreen = null
                                        activeTab = tab
                                        // When clicking Transcription tab while in a folder, go to root
                                        if (tab == BottomTab.Transcription && isInFolder) {
                                            transcriptionViewModel.navigateToFolder(null)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val initialIndex =
                            navigableTabs.indexOf(initialState).takeIf { it >= 0 } ?: 0
                        val targetIndex =
                            navigableTabs.indexOf(targetState).takeIf { it >= 0 } ?: 0
                        val forward = targetIndex > initialIndex
                        val enter = slideInHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) fullWidth else -fullWidth
                        }
                        val exit = slideOutHorizontally(animationSpec = tween(320)) { fullWidth ->
                            if (forward) -fullWidth else fullWidth
                        }
                        enter togetherWith exit
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                    label = "tabAnimation"
                ) { tab ->
                    TabContent(
                        tab = tab,
                        primaryTextColor = primaryTextColor,
                        isDark = isDark,
                        transcriptionViewModel = transcriptionViewModel,
                        selectedModel = selectedModel,
                        onSelectModel = { model -> selectedModelState.value = model },
                        isModelDownloading = isModelDownloading,
                        onModelDownloadingChanged = { isModelDownloading = it },
                        audioSource = audioSource,
                        onAudioSourceChanged = { audioSource = it },
                        recorderCommand = recorderCommand,
                        onCommandHandled = { recorderCommand = null },
                        onRecordingStateChanged = { recording ->
                            isRecording = recording
                            if (!recording) {
                                isRecorderPaused = false
                            }
                        },
                        themePreference = themePreference,
                        onThemePreferenceChange = onThemePreferenceChange,
                        settingsScreen = settingsScreen,
                        onSettingsScreenChange = { settingsScreen = it },
                        onOpenTranscription = { id ->
                            openTranscription(id)
                        },
                        selectedLanguageCode = selectedLanguageCode,
                        onLanguageSelectionRequested = { showLanguageSelection = true }
                    )
                }
            }

        }

        if (isRecorderSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!isRecording && !uiState.isTranscribing) {
                        isRecorderSheetVisible = false
                    }
                },
                sheetState = recorderSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                    }
                },
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .navigationBarsPadding()
                ) {
                    com.example.whisper_kotlin.recorder.RecorderScreen(
                        modifier = Modifier.fillMaxSize(),
                        isDark = isDark,
                        textColor = primaryTextColor,
                        selectedModel = selectedModel,
                        onSelectModel = { model -> selectedModelState.value = model },
                        isModelDownloading = isModelDownloading,
                        onModelDownloadingChanged = { isModelDownloading = it },
                        audioSource = audioSource,
                        onAudioSourceChanged = { audioSource = it },
                        transcriptionViewModel = transcriptionViewModel,
                        command = recorderCommand,
                        onCommandHandled = { recorderCommand = null },
                        onRecordingStateChanged = { recording ->
                            isRecording = recording
                            if (!recording) {
                                isRecorderPaused = false
                            }
                        },
                        onViewTranscription = openTranscription,
                        initialFolderId = if (activeTab == BottomTab.Transcription) uiState.currentFolderId else null,
                        selectedLanguageCode = selectedLanguageCode,
                        onLanguageSelectionRequested = {
                            isRecorderSheetVisible = false
                            showLanguageSelection = true
                        }
                    )
                }
            }
        }

        if (detailEntry != null) {
            val detailSheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { targetState ->
                    // Prevent the sheet from being dismissed via swipe or scrim; it will
                    // only be closed explicitly via the close button we provide.
                    targetState != androidx.compose.material3.SheetValue.Hidden
                }
            )

            ModalBottomSheet(
                onDismissRequest = {
                    // With confirmValueChange blocking Hidden, this should not be
                    // invoked by swipe, but keep the guard for safety.
                    if (!isDetailTranscribing) {
                        detailEntryId = null
                    }
                },
                sheetState = detailSheetState,
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                    }
                },
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .navigationBarsPadding()
                ) {
                    TranscriptionDetailScreen(
                        entry = detailEntry,
                        textColor = primaryTextColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        viewModel = transcriptionViewModel
                    )

                    TextButton(
                        onClick = {
                            if (!isDetailTranscribing) {
                                detailEntryId = null
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 16.dp)
                            .offset(y = -16.dp),
                        colors = androidx.compose.material.ButtonDefaults.textButtonColors(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.material.Text(
                            text = "Done",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Language Selection Screen
        if (showLanguageSelection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LanguageSelectionScreen(
                    selectedLanguageCode = selectedLanguageCode,
                    onLanguageSelected = { code ->
                        selectedLanguageCode = code
                        showLanguageSelection = false
                        isRecorderSheetVisible = true
                    },
                    textColor = primaryTextColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    currentHeader: String,
    isInFolder: Boolean,
    activeTab: BottomTab,
    onFolderBackClick: () -> Unit
) {
    val topBarColor = MaterialTheme.colorScheme.background

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = currentHeader,
                style = MaterialTheme.typography.titleMedium
            )
        },
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = topBarColor
        ),
        navigationIcon = {
            if (isInFolder && activeTab == BottomTab.Transcription) {
                IconButton(onClick = onFolderBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back to parent folder"
                    )
                }
            }
        },
    )
}

@Composable
private fun HomeBottomBar(
    activeTab: BottomTab,
    isInFolder: Boolean,
    isDark: Boolean,
    disabledTabs: Set<BottomTab>,
    onTabSelected: (BottomTab) -> Unit
) {
    val defaultColor =
        if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Black
    val selectedColor = MaterialTheme.colorScheme.primary
    val recorderLiftOffset = (-22).dp
    val recorderCircleSize = 64.dp

    val navContainerColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = navContainerColor,
            modifier = Modifier
                .fillMaxWidth(),
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0)
        ) {
            bottomTabOrder.forEach { tab ->
                val isDisabled = tab in disabledTabs
                val isRecorder = tab == BottomTab.Recorder
                val targetColor = when {
                    activeTab == tab -> selectedColor
                    isDisabled -> defaultColor.copy(alpha = 0.3f)
                    else -> defaultColor
                }
                val animatedColor by animateColorAsState(
                    targetValue = targetColor,
                    label = "navColor"
                )
                val isSelected = activeTab == tab
                val labelText = if (isDisabled) "Chats" else tab.header
                NavigationBarItem(
                    icon = {
                        if (isRecorder) {
                            // Transparent placeholder for recorder (actual button overlaid)
                            Spacer(modifier = Modifier.size(recorderCircleSize))
                        } else {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tab.icon(isSelected),
                                    contentDescription = tab.header,
                                    modifier = Modifier.size(28.dp),
                                    tint = animatedColor
                                )
                                // "Coming Soon" badge for Chats tab
                                if (tab == BottomTab.Chats) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-12).dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Coming Soon",
                                            color = MaterialTheme.colorScheme.surface,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    selected = isSelected,
                    onClick = { if (!isDisabled) onTabSelected(tab) },
                    label = {
                        if (!isRecorder){
                            Text(
                                text = labelText,
                                color = animatedColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = animatedColor,
                        unselectedIconColor = animatedColor,
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = !isDisabled
                )
            }
        }

        // Overlay the floating recorder button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val recorderIndex = bottomTabOrder.indexOf(BottomTab.Recorder)
            val totalTabs = bottomTabOrder.size
            val density = LocalDensity.current
            // Calculate position based on tab index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(
                        x = with(density) {
                            (((recorderIndex.toFloat() / (totalTabs - 1)) - 0.5f) * 200.dp.toPx()).toDp()
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = recorderLiftOffset)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (BottomTab.Recorder !in disabledTabs) {
                                onTabSelected(BottomTab.Recorder)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(recorderCircleSize)
                            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                            .background(
                                color = if (activeTab == BottomTab.Recorder)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            BottomTab.Recorder.icon(true),
                            contentDescription = BottomTab.Recorder.header,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: BottomTab,
    primaryTextColor: androidx.compose.ui.graphics.Color,
    isDark: Boolean,
    transcriptionViewModel: TranscriptionViewModel,
    selectedModel: ModelOption?,
    onSelectModel: (ModelOption?) -> Unit,
    isModelDownloading: Boolean,
    onModelDownloadingChanged: (Boolean) -> Unit,
    audioSource: AudioSource,
    onAudioSourceChanged: (AudioSource) -> Unit,
    recorderCommand: RecorderCommand?,
    onCommandHandled: () -> Unit,
    onRecordingStateChanged: (Boolean) -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    settingsScreen: SettingsScreen?,
    onSettingsScreenChange: (SettingsScreen?) -> Unit,
    onOpenTranscription: (Long) -> Unit,
    selectedLanguageCode: String = "en",
    onLanguageSelectionRequested: () -> Unit = {}
) {
    when (tab) {
        BottomTab.Recents -> {
            RecentsScreen(
                modifier = Modifier.fillMaxSize(),
                textColor = primaryTextColor,
                viewModel = transcriptionViewModel,
                onOpenTranscription = onOpenTranscription
            )
        }

        BottomTab.Transcription -> {
            TranscriptionScreen(
                modifier = Modifier.fillMaxSize(),
                textColor = primaryTextColor,
                viewModel = transcriptionViewModel,
                isDetailVisible = false,
                onOpenTranscription = onOpenTranscription
            )
        }

        BottomTab.Recorder -> {
            com.example.whisper_kotlin.recorder.RecorderScreen(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark,
                textColor = primaryTextColor,
                selectedModel = selectedModel,
                onSelectModel = onSelectModel,
                isModelDownloading = isModelDownloading,
                onModelDownloadingChanged = onModelDownloadingChanged,
                audioSource = audioSource,
                onAudioSourceChanged = onAudioSourceChanged,
                transcriptionViewModel = transcriptionViewModel,
                command = recorderCommand,
                onCommandHandled = onCommandHandled,
                onRecordingStateChanged = onRecordingStateChanged,
                onViewTranscription = onOpenTranscription,
                initialFolderId = null,
                selectedLanguageCode = selectedLanguageCode,
                onLanguageSelectionRequested = onLanguageSelectionRequested
            )
        }

        BottomTab.Chats -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor
                )
            }
        }

        BottomTab.Settings -> {
            when (settingsScreen) {
                SettingsScreen.ManageModels -> {
                    ManageModelsScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor,
                        selectedModel = selectedModel,
                        onSelectModel = onSelectModel,
                        onBack = { onSettingsScreenChange(null) }
                    )
                }
                SettingsScreen.ManageFiles -> {
                    ManageFilesScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor,
                        transcriptionViewModel = transcriptionViewModel,
                        onBack = { onSettingsScreenChange(null) }
                    )
                }
                SettingsScreen.AppFeatures -> {
                    AppFeaturesScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor
                    )
                }
                SettingsScreen.PrivacyPolicy -> {
                    PrivacyPolicyScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor
                    )
                }
                null -> {
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor,
                        themePreference = themePreference,
                        onThemePreferenceChange = onThemePreferenceChange,
                        selectedModel = selectedModel,
                        transcriptionViewModel = transcriptionViewModel,
                        onSelectModel = onSelectModel,
                        onNavigateToManageModels = { onSettingsScreenChange(SettingsScreen.ManageModels) },
                        onNavigateToManageFiles = { onSettingsScreenChange(SettingsScreen.ManageFiles) },
                        onNavigateToAppFeatures = { onSettingsScreenChange(SettingsScreen.AppFeatures) },
                        onNavigateToPrivacyPolicy = { onSettingsScreenChange(SettingsScreen.PrivacyPolicy) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    entry: SavedTranscription,
    primaryTextColor: androidx.compose.ui.graphics.Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = primaryTextColor,
        title = {
            androidx.compose.material.Text(
                "Delete transcription?",
                color = primaryTextColor,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            androidx.compose.material.Text(
                "This will permanently remove ${entry.fileLabel}.",
                color = primaryTextColor.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                androidx.compose.material.Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material.Text("Cancel")
            }
        }
    )
}
