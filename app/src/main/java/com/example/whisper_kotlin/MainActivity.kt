package com.example.whisper_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.compose.AppTheme
import com.example.whisper_kotlin.data.TranscriptionRepository
import com.example.whisper_kotlin.navigation.ThemePreference
import com.example.whisper_kotlin.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize repository
        TranscriptionRepository.initialize(applicationContext)
        
        setContent {
            var themeSelection by rememberSaveable { mutableStateOf(ThemePreference.System.name) }
            val themePreference = remember(themeSelection) { ThemePreference.valueOf(themeSelection) }
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themePreference) {
                ThemePreference.System -> systemDark
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }

            AppTheme(darkTheme = isDarkTheme) {
                HomeScreen(
                    themePreference = themePreference,
                    onThemePreferenceChange = { selected -> themeSelection = selected.name }
                )

                // Set status bar color to match nav bar
                val statusBarColor = if (isDarkTheme) {
                    androidx.compose.ui.graphics.Color(0xFF2C2C2E)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }

                SideEffect {
                    window.statusBarColor = statusBarColor.toArgb()
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT

                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }
    }
}
