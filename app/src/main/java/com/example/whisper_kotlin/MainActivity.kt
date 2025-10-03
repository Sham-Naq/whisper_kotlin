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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
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
        // Track selected audio file/asset for transcription
        var audioSource by remember { mutableStateOf<AudioSource>(AudioSource.Asset("samples/samples_jfk.wav")) }
        var isModelDownloading by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Show model selector under the header on Transcription and Recorder pages
            if (currentRoute == BottomTab.Transcription.route || currentRoute == BottomTab.Recorder.route) {
                ModelSelectorRow(
                    textColor = primaryTextColor,
                    isDark = isDark,
                    buttonBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8EAF6),
                    onSelected = { opt -> selectedModel = opt },
                    onDownloadingChanged = { downloading -> isModelDownloading = downloading }
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (currentRoute == BottomTab.Transcription.route) {
                    FileSelectorRow(
                        textColor = primaryTextColor,
                        isDark = isDark,
                        buttonBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8EAF6),
                        source = audioSource,
                        onSourceChanged = { audioSource = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
                        audioSource = audioSource,
                        isModelDownloading = isModelDownloading
                    )
                }
                composable(BottomTab.Recorder.route) {
                    com.example.whisper_kotlin.recorder.RecorderScreen(
                        modifier = Modifier.fillMaxSize(),
                        isDark = isDark,
                        textColor = primaryTextColor
                    )
                }
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
            val route = currentRoute
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left item wrapper
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = route == BottomTab.Transcription.route
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleT")
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(activeIconColor.copy(alpha = 0.25f), CircleShape)
                                .blur(18.dp)
                        )
                    }
                    BottomNavItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
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
                        selectedBorderColor = Color.Transparent,
                        unselectedBorderColor = Color.Transparent,
                        drawContainer = false
                    ) {
                        IconTranscription(
                            color = if (isSelected) activeIconColor else inactiveIconColor,
                            modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }

                // Middle item wrapper
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = route == BottomTab.Recorder.route
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleR")
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(activeIconColor.copy(alpha = 0.25f), CircleShape)
                                .blur(18.dp)
                        )
                    }
                    BottomNavItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                navController.navigate(BottomTab.Recorder.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier,
                        drawContainer = false
                    ) {
                        IconRecorder(
                            color = if (isSelected) activeIconColor else inactiveIconColor,
                            modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                    }
                }

                // Right item wrapper
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = route == BottomTab.Settings.route
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.12f else 1f, label = "scaleS")
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(activeIconColor.copy(alpha = 0.25f), CircleShape)
                                .blur(18.dp)
                        )
                    }
                    BottomNavItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
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
                        selectedBorderColor = Color.Transparent,
                        unselectedBorderColor = Color.Transparent,
                        drawContainer = false
                    ) {
                        IconSettings(
                            color = if (isSelected) activeIconColor else inactiveIconColor,
                            modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
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