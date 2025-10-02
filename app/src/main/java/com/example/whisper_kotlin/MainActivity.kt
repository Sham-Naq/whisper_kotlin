package com.example.whisper_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import android.content.res.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.whisper_kotlin.ModelManager
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }
    }
}

private enum class BottomTab(val route: String, val header: String) {
    Transcription(route = "transcription", header = "Transcription"),
    Recorder(route = "recorder", header = "Recorder"),
    Settings(route = "settings", header = "Settings")
}

@Composable
private fun HomeScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BottomTab.Transcription.route
    val currentHeader = when (currentRoute) {
        BottomTab.Recorder.route -> BottomTab.Recorder.header
        BottomTab.Settings.route -> BottomTab.Settings.header
        else -> BottomTab.Transcription.header
    }

    // Simple theme palette (foundation-only): dark-mode friendly with dark blue accents
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF121212) else Color(0xFFF7F7F7)
    val headerBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFE0E0E0)
    // Use dark blue instead of black; brighten in dark mode for contrast
    val primaryTextColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF0D47A1)
    val bottomBarBg = if (isDark) Color(0xFF1A1A1A) else Color.White
    val bottomBarDivider = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE6E6E6)
    val navSelectedBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
    val navUnselectedBg = if (isDark) Color(0xFF222222) else Color(0xFFF5F5F5)
    val navSelectedBorder = if (isDark) Color(0xFF3A3A3A) else Color(0xFF9E9E9E)
    val navUnselectedBorder = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE0E0E0)
    val activeIconColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF1E88E5)
    val inactiveIconColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF888888)

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        // Top header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Centered page header below the selector
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = currentHeader,
                        style = TextStyle(
                            color = primaryTextColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // Content area
        // Track selected model from the selector so Transcription screen can use it
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
        var isModelDownloading by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Show model selector under the header only on the Transcription page
            if (currentRoute == BottomTab.Transcription.route) {
                ModelSelectorRow(
                    textColor = primaryTextColor,
                    isDark = isDark,
                    buttonBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8EAF6),
                    onSelected = { opt -> selectedModel = opt },
                    onDownloadingChanged = { downloading -> isModelDownloading = downloading }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            NavHost(
                navController = navController,
                startDestination = BottomTab.Transcription.route,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                composable(BottomTab.Transcription.route) {
                    TranscriptionScreen(
                        modifier = Modifier.fillMaxSize(),
                        textColor = primaryTextColor,
                        selectedModel = selectedModel,
                        isModelDownloading = isModelDownloading
                    )
                }
                composable(BottomTab.Recorder.route) { RecorderScreen(Modifier.fillMaxSize(), textColor = primaryTextColor) }
                composable(BottomTab.Settings.route) { SettingsScreen(Modifier.fillMaxSize(), textColor = primaryTextColor) }
            }
        }

        // Bottom nav bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bottomBarBg)
        ) {
            // Top divider line separating content from nav bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(bottomBarDivider)
            )
            val circleSizeDp = 80.dp
            val itemSpacingDp = 8.dp
            val rowHorizontalPaddingDp = 8.dp
            val density = LocalDensity.current
            var rowWidthPx by remember { mutableStateOf(0) }
            var parentLeftInRoot by remember { mutableStateOf(0f) }
            val centers = remember { mutableStateListOf(0f, 0f, 0f) }
            val route = currentRoute
            val selectedIndex = when (route) {
                BottomTab.Transcription.route -> 0
                BottomTab.Recorder.route -> 1
                BottomTab.Settings.route -> 2
                else -> 0
            }
            val itemCount = 3
            val circlePx = with(density) { circleSizeDp.toPx() }
            val spacingPx = with(density) { itemSpacingDp.toPx() }
            val padPx = with(density) { rowHorizontalPaddingDp.toPx() }
            val totalSpacingPx = spacingPx * (itemCount - 1)
            // Row width includes its horizontal padding; compute content width for children
            val contentWidthPx = (rowWidthPx.toFloat() - padPx * 2f).coerceAtLeast(0f)
            val childWidthPx = if (contentWidthPx > 0f) (contentWidthPx - totalSpacingPx) / itemCount else 0f
            // Prefer precise centering using measured item centers; fallback to computed layout centers
            val measuredCenter = centers.getOrNull(selectedIndex)?.takeIf { it > 0f }
            val targetLeft = if (measuredCenter != null && parentLeftInRoot > 0f) {
                measuredCenter - parentLeftInRoot - circlePx / 2f
            } else if (childWidthPx > 0f) {
                padPx + selectedIndex * (childWidthPx + spacingPx) + (childWidthPx - circlePx) / 2f
            } else 0f
            val animatedLeft by animateFloatAsState(
                targetValue = targetLeft,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "navIndicatorX"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(circleSizeDp + 8.dp)
                    .onGloballyPositioned { parentLeftInRoot = it.positionInRoot().x }
            ) {
                // Sliding circular indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { translationX = animatedLeft }
                        .size(circleSizeDp)
                        .background(activeIconColor.copy(alpha = 0.25f), CircleShape)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = rowHorizontalPaddingDp, vertical = 4.dp)
                        .onGloballyPositioned { rowWidthPx = it.size.width },
                    horizontalArrangement = Arrangement.spacedBy(itemSpacingDp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
            // Left item wrapper to capture its center
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        centers[0] = coords.positionInRoot().x + coords.size.width / 2f
                    },
                contentAlignment = Alignment.Center
            ) {
                val isSelected = route == BottomTab.Transcription.route
                val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleT")
                BottomNavItem(
                selected = route == BottomTab.Transcription.route,
                onClick = {
                    if (route != BottomTab.Transcription.route) {
                        navController.navigate(BottomTab.Transcription.route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            restoreState = true
                        }
                    }
                },
                    modifier = Modifier,
                    selectedBgColor = navSelectedBg,
                    unselectedBgColor = navUnselectedBg,
                    // Hide outlines for Transcription item
                    selectedBorderColor = Color.Transparent,
                    unselectedBorderColor = Color.Transparent,
                    drawContainer = false
                ) {
                    IconTranscription(
                        color = if (route == BottomTab.Transcription.route) activeIconColor else inactiveIconColor,
                        modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
            // Middle item wrapper
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        centers[1] = coords.positionInRoot().x + coords.size.width / 2f
                    },
                contentAlignment = Alignment.Center
            ) {
                val isSelected = route == BottomTab.Recorder.route
                val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleR")
                BottomNavItem(
                selected = route == BottomTab.Recorder.route,
                onClick = {
                    if (route != BottomTab.Recorder.route) {
                        navController.navigate(BottomTab.Recorder.route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            restoreState = true
                        }
                    }
                },
                    modifier = Modifier,
                    // No rectangular container for the middle item
                    drawContainer = false
                ) {
                    IconRecorder(
                        color = if (route == BottomTab.Recorder.route) activeIconColor else inactiveIconColor,
                        modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
            // Right item wrapper
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        centers[2] = coords.positionInRoot().x + coords.size.width / 2f
                    },
                contentAlignment = Alignment.Center
            ) {
                val isSelected = route == BottomTab.Settings.route
                val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleS")
                BottomNavItem(
                selected = route == BottomTab.Settings.route,
                onClick = {
                    if (route != BottomTab.Settings.route) {
                        navController.navigate(BottomTab.Settings.route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            restoreState = true
                        }
                    }
                },
                    modifier = Modifier,
                    selectedBgColor = navSelectedBg,
                    unselectedBgColor = navUnselectedBg,
                    // Hide outlines for Settings item
                    selectedBorderColor = Color.Transparent,
                    unselectedBorderColor = Color.Transparent,
                    drawContainer = false
                ) {
                    IconSettings(
                        color = if (route == BottomTab.Settings.route) activeIconColor else inactiveIconColor,
                        modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
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
    val base = if (drawContainer) {
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    } else {
        modifier
    }
    Box(
        modifier = base
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun IconTranscription(color: Color = Color(0xFF111111), modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lineH = h * 0.16f
        val gap = h * 0.14f
        val radius = CornerRadius(lineH / 2, lineH / 2)
        drawRoundRect(color = color, topLeft = Offset(0f, 0f), size = Size(w, lineH), cornerRadius = radius)
        drawRoundRect(color = color, topLeft = Offset(0f, lineH + gap), size = Size(w, lineH), cornerRadius = radius)
        drawRoundRect(color = color, topLeft = Offset(0f, (lineH + gap) * 2), size = Size(w, lineH), cornerRadius = radius)
    }
}

@Composable
private fun IconRecorder(color: Color = Color(0xFF111111), modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w / 5f
        drawRoundRect(color, topLeft = Offset(0f, h * 0.4f), size = Size(barW, h * 0.6f), cornerRadius = CornerRadius(barW / 2, barW / 2))
        drawRoundRect(color, topLeft = Offset(barW * 2, h * 0.2f), size = Size(barW, h * 0.8f), cornerRadius = CornerRadius(barW / 2, barW / 2))
        drawRoundRect(color, topLeft = Offset(barW * 4, h * 0.35f), size = Size(barW, h * 0.65f), cornerRadius = CornerRadius(barW / 2, barW / 2))
    }
}

@Composable
private fun IconSettings(color: Color = Color(0xFF111111), modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2.5f
        // outer ring
        drawCircle(color = color, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.25f))
        // inner dot
        drawCircle(color = color, radius = r * 0.25f)
    }
}

@Composable
private fun TranscriptionScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1),
    selectedModel: ModelOption? = null,
    isModelDownloading: Boolean = false
) {
    val ctx = LocalContext.current
    var log by remember { mutableStateOf("No transcriptions made\n") }
    val scope = rememberCoroutineScope()

    fun append(s: String) { log += s + "\n" }

    Column(modifier = modifier) {
        // Actions row (no explicit load button; model is loaded on selection or lazily here)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Transcribe sample", textColor) {
                scope.launch(Dispatchers.IO) {
                    try {
                        if (isModelDownloading) {
                            append("Please wait for model download to finish…")
                            return@launch
                        }
                        // Ensure a model is loaded: prefer selected downloaded model, otherwise fallback to bundled asset
                        val chosen = selectedModel
                        val local = chosen?.let { opt ->
                            val f = java.io.File(java.io.File(ctx.filesDir, "models"), opt.fileName)
                            if (f.exists() && f.length() > 0L) f else null
                        }
                        val modelLabel = if (local != null) {
                            // Force reload in case a previous context is active for a different model
                            WhisperEngine.loadModelFromFile(local.absolutePath, force = true)
                            chosen?.id ?: local.name
                        } else {
                            WhisperEngine.loadModelFromAssets(ctx, "models/ggml-tiny-q5_1.bin", force = true)
                            "ggml-tiny-q5_1 (asset)"
                        }
                        val startNs = android.os.SystemClock.elapsedRealtimeNanos()
                        val text = WhisperEngine.transcribeWavAsset(ctx, "samples/samples_jfk.wav")
                        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
                        val timeLine = "Completed in " + String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0) + " s (" + elapsedMs.toLong() + " ms)"
                        append("Model: $modelLabel\nTranscript:\n$text\n$timeLine")
                    } catch (t: Throwable) {
                        append("Transcribe failed: ${'$'}t")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(text = log, style = TextStyle(color = textColor))
    }
}

@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = color, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun RecorderScreen(modifier: Modifier = Modifier, textColor: Color = Color(0xFF0D47A1)) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText("Recorder", style = TextStyle(color = textColor))
        // Future: recorder controls
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier, textColor: Color = Color(0xFF0D47A1)) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        BasicText("Settings", style = TextStyle(color = textColor))
        // Future: app settings
    }
}

// --- Model selector UI ---

private data class ModelOption(val id: String, val fileName: String, val url: String)

@Composable
private fun ModelSelectorRow(
    textColor: Color,
    isDark: Boolean,
    buttonBg: Color,
    onSelected: (ModelOption) -> Unit,
    onDownloadingChanged: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Populate from ModelManager catalog so all supported models appear
    val options = remember {
        ModelManager.availableModels().map { spec ->
            ModelOption(id = spec.id, fileName = spec.fileName, url = spec.url)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(options.firstOrNull()?.id ?: "whisper-tiny") }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progressPct by remember { mutableStateOf(0) }

    // Anchor bounds in window coordinates for popup placement
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 0.dp, vertical = 0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Select model button
            Box(
                modifier = Modifier
                    .background(buttonBg, RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText("Select model", style = TextStyle(color = textColor, fontWeight = FontWeight.Medium))
            }
            // Selected model label
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = if (downloadingId != null) "Downloading ${downloadingId}… ${progressPct}%" else selectedId,
                    style = TextStyle(color = textColor),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        anchorBounds = coords.boundsInWindow()
                    }
                )
                if (downloadingId != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { ModelManager.cancelDownload(downloadingId!!) },
                        contentAlignment = Alignment.Center
                    ) {
                        CancelIcon(color = textColor)
                    }
                }
            }
        }

        if (expanded) {
            val cardBg = if (isDark) Color(0xFF232323) else Color(0xFFFFFFFF)
            val border = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
            val density = LocalDensity.current
            val anchorSnapshot = anchorBounds
            val verticalPaddingPx = with(density) { 6.dp.roundToPx() }
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
            val anchorWidthDp = if (anchorSnapshot.isEmpty) 0.dp else with(density) { anchorSnapshot.width.toDp() }
            val menuWidth = anchorWidthDp.coerceAtLeast(180.dp)
            // Show as an overlay popup positioned under the anchor label
            Popup(
                popupPositionProvider = popupPositionProvider,
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp)
                        .width(menuWidth)
                ) {
                    options.forEach { opt ->
                        val isDownloaded = ModelManager.isModelPresent(ctx, opt.fileName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = downloadingId == null) {
                                    if (isDownloaded) {
                                        selectedId = opt.id
                                        onSelected(opt)
                                        expanded = false
                                        // Load ggml model immediately on selection if present (force to switch models)
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val modelFile = ModelManager.getLocalModelFile(ctx, opt.fileName)
                                                if (modelFile.exists() && modelFile.length() > 0L) {
                                                    WhisperEngine.loadModelFromFile(modelFile.absolutePath, force = true)
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    } else {
                                        // Start download
                                        downloadingId = opt.id
                                        progressPct = 0
                                        onDownloadingChanged(true)
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                ModelManager.ensureModel(
                                                    context = ctx,
                                                    modelFileName = opt.fileName,
                                                    modelUrl = opt.url,
                                                    onProgress = { p ->
                                                        val pct = if (p.totalBytes > 0) ((p.bytesRead * 100L) / p.totalBytes).toInt() else 0
                                                        progressPct = pct.coerceIn(0, 100)
                                                    }
                                                )
                                                selectedId = opt.id
                                                onSelected(opt)
                                                // After download completes, load ggml model (force to switch models)
                                                val modelFile = ModelManager.getLocalModelFile(ctx, opt.fileName)
                                                if (modelFile.exists() && modelFile.length() > 0L) {
                                                    WhisperEngine.loadModelFromFile(modelFile.absolutePath, force = true)
                                                }
                                            } catch (ce: java.util.concurrent.CancellationException) {
                                                // Optional: could set a transient "Canceled" message
                                            } catch (t: Throwable) {
                                                // Optional: surface error to UI
                                            } finally {
                                                downloadingId = null
                                                onDownloadingChanged(false)
                                                expanded = false
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText(text = opt.id, style = TextStyle(color = textColor))
                            if (!isDownloaded) {
                                DownloadIcon(color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadIcon(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shaftW = w * 0.18f
        val arrowH = h * 0.6f
        // Arrow shaft
        drawRoundRect(
            color = color,
            topLeft = Offset((w - shaftW) / 2f, 0f),
            size = Size(shaftW, arrowH * 0.6f),
            cornerRadius = CornerRadius(shaftW / 2, shaftW / 2)
        )
        // Arrow head (wide rect to suggest triangle)
        val headH = arrowH * 0.4f
        drawRoundRect(color, topLeft = Offset(w * 0.25f, arrowH * 0.4f), size = Size(w * 0.5f, headH), cornerRadius = CornerRadius(headH / 4, headH / 4))
        // Base line
        drawRoundRect(color, topLeft = Offset(w * 0.2f, h * 0.82f), size = Size(w * 0.6f, h * 0.12f), cornerRadius = CornerRadius(h * 0.06f, h * 0.06f))
    }
}

@Preview(showBackground = true, name = "Home - Transcription", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewHome_Transcription() {
    HomeScreen()
}



@Preview(showBackground = true, name = "Transcription Screen")
@Composable
private fun PreviewTranscriptionScreen() {
    TranscriptionScreen(modifier = Modifier.fillMaxSize())
}

@Composable
private fun PreviewRecorderScreen() {
    RecorderScreen(modifier = Modifier.fillMaxSize())
}

@Composable
private fun PreviewSettingsScreen() {
    SettingsScreen(modifier = Modifier.fillMaxSize())
}

@Composable
private fun CancelIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w.coerceAtMost(h) * 0.18f)
        // Draw an X
        drawLine(color = color, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = stroke)
        drawLine(color = color, start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = stroke)
    }
}