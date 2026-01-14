package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFeaturesScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1)
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        text = "App Features",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        // Gift icon with circular background
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp)
            )
        }

        // Always Free heading
        Text(
            text = "Always Free",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        // Description text
        Text(
            text = "Enjoy all features of the app completely free of charge.",
            fontSize = 16.sp,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // What You Get section
        Text(
            text = "What You Get",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.align(Alignment.Start)
        )

        // Features list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FeatureItem(
                icon = InfinityIcon(),
                iconColor = Color(0xFF00C853),
                title = "Unlimited Transcriptions",
                description = "Transcribe as many recordings as you want without any limit.",
                textColor = textColor
            )

            FeatureItem(
                icon = TargetIcon(),
                iconColor = Color(0xFF00C853),
                title = "Highly Accurate",
                description = "Powered by a state-of-the-art AI model for precise transcriptions.",
                textColor = textColor
            )

            FeatureItem(
                icon = Icons.Filled.Public,
                iconColor = Color(0xFF00C853),
                title = "99+ Languages",
                description = "Supports transcription in over 99 languages worldwide.",
                textColor = textColor
            )

            FeatureItem(
                icon = Icons.Filled.Lock,
                iconColor = Color(0xFF00C853),
                title = "Safe & Secure",
                description = "Your recordings and transcriptions remain securely on your device.",
                textColor = textColor
            )

            FeatureItem(
                icon = Icons.Filled.PlayArrow,
                iconColor = Color(0xFF00C853),
                title = "Ad-Supported",
                description = "Occasional ads help keep the app free",
                textColor = textColor
            )
        }
        }
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = textColor.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
    }
}

// Custom infinity icon since Material Icons doesn't have one
@Composable
fun InfinityIcon(): ImageVector {
    return androidx.compose.material.icons.Icons.Filled.AllInclusive
}

// Custom target/accuracy icon
@Composable
fun TargetIcon(): ImageVector {
    return androidx.compose.material.icons.Icons.Filled.GpsFixed
}
