package com.example.whisper_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.compose.AppTheme
import com.example.whisper_kotlin.data.TranscriptionRepository
import com.example.whisper_kotlin.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize repository
        TranscriptionRepository.initialize(applicationContext)
        
        setContent {
            AppTheme {
                HomeScreen()

                // Set status bar color based on theme
                val statusBarColor = MaterialTheme.colorScheme.surface
                val isDark = isSystemInDarkTheme()

                SideEffect {
                    window.statusBarColor = statusBarColor.toArgb()
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT

                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }
}
