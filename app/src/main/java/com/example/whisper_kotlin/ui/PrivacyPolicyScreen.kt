package com.example.whisper_kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyPolicyScreen(
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF0D47A1)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Effective Date
        Text(
            text = "Effective Date: September 6, 2025",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Introduction
        SectionHeading(text = "Introduction", textColor = textColor)
        
        BodyText(
            text = "Welcome to OffScribe, an offline transcription application designed with your privacy as its utmost priority. Our core mission is to provide you with a powerful and reliable transcription tool that operates entirely on your mobile device, ensuring that your audio recordings and their corresponding transcriptions never leave your control or your device.",
            textColor = textColor
        )

        BodyText(
            text = "This Privacy Policy explains our unwavering commitment to privacy by detailing the information we *do not* collect, how your data is handled locally, and your rights in relation to your data within the application.",
            textColor = textColor
        )

        // Information We Do Not Collect
        SectionHeading(text = "Information We Do Not Collect", textColor = textColor)
        
        BodyText(
            text = "Our fundamental principle is absolute user data privacy. OffScribe is designed to function entirely offline, meaning **we do not collect, store, transmit, or process any personal data, audio recordings, or transcribed text from your device.**",
            textColor = textColor
        )

        BulletPoint(
            title = "Personal Identifiable Information (PII)",
            description = "We do not ask for, collect, store, or transmit your name, email address, phone number, location, IP address, device identifiers, or any other information that could identify you personally.",
            textColor = textColor
        )

        BulletPoint(
            title = "Audio Recordings",
            description = "Your audio recordings are created, processed, and stored *only* on your device. They are never uploaded to our servers, third-party servers, or any external network.",
            textColor = textColor
        )

        BulletPoint(
            title = "Transcribed Text",
            description = "The text generated from your recordings through our transcription engine is stored *only* on your device. It is never transmitted off your device.",
            textColor = textColor
        )

        BulletPoint(
            title = "Usage Data & Analytics",
            description = "We do not collect any usage statistics, analytics, crash logs, or diagnostic data about how you use the app. Our focus is purely on the functionality provided locally on your device.",
            textColor = textColor
        )

        SubBulletText(
            text = "*Note on System-Level Data:* Please be aware that standard, anonymized system-level diagnostic and usage data (e.g., crash reports, general app usage statistics) collected by Apple as part of the iOS operating system are outside the scope of this policy and are governed by Apple's privacy policies. **OffScribe** itself does not initiate or collect this data.",
            textColor = textColor
        )

        // Local Processing and Storage
        SectionHeading(text = "Local Processing and Storage", textColor = textColor)
        
        BodyText(
            text = "All core functionalities of OffScribe, including recording, transcription, and data management, occur exclusively on your mobile device.",
            textColor = textColor
        )

        SubsectionHeading(text = "Offline Transcription", textColor = textColor)
        BodyText(
            text = "The sophisticated transcription engine runs locally on your device. An internet connection is not required for the transcription process itself.",
            textColor = textColor
        )

        SubsectionHeading(text = "Local Storage", textColor = textColor)
        BodyText(
            text = "Your audio recordings and their corresponding transcriptions are stored *only* within the app's sandboxed environment on your device.",
            textColor = textColor
        )

        SubsectionHeading(text = "Folder Management", textColor = textColor)
        BodyText(
            text = "The folder functionality designed to help you organize your transcriptions operates entirely locally. All folder structures and their contents reside on your device.",
            textColor = textColor
        )

        SubsectionHeading(text = "Language Flexibility", textColor = textColor)
        BodyText(
            text = "The ability to change the transcription language, even after recording, is a feature executed purely on your device, utilizing locally available resources.",
            textColor = textColor
        )

        // No Data Transmission
        SectionHeading(text = "No Data Transmission", textColor = textColor)
        
        BodyText(
            text = "To reiterate, OffScribe is an offline-first application. At no point does our application transmit your recordings, transcriptions, personal data, or any other information generated or stored within the app to our servers, third-party servers, or any external network over the internet. Your data remains entirely confined to your device.",
            textColor = textColor
        )

        // Third-Party Services
        SectionHeading(text = "Third-Party Services", textColor = textColor)
        
        BodyText(
            text = "We do not integrate any third-party analytics, advertising, or data collection SDKs within OffScribe that would collect or transmit your personal data, recordings, or transcriptions. Our app relies solely on the capabilities of your device and standard iOS frameworks for its operation, none of which are used by us to collect user data.",
            textColor = textColor
        )

        // Your Control Over Local Data
        SectionHeading(text = "Your Control Over Local Data", textColor = textColor)
        
        BodyText(
            text = "Since all your data resides on your device, you have complete and exclusive control over it:",
            textColor = textColor
        )

        SubsectionHeading(text = "Access", textColor = textColor)
        BodyText(
            text = "You can access all your recordings and transcriptions directly within the app.",
            textColor = textColor
        )

        SubsectionHeading(text = "Management", textColor = textColor)
        BodyText(
            text = "You can organize your transcriptions into folders, edit transcribed text, and manage your recordings as you see fit.",
            textColor = textColor
        )

        SubsectionHeading(text = "Deletion", textColor = textColor)
        BodyText(
            text = "You can delete any recording or transcription, including entire folders, at any time. When you delete data from the app, it is removed from your device.",
            textColor = textColor
        )

        SubsectionHeading(text = "Device Security", textColor = textColor)
        BodyText(
            text = "The security of your data is directly tied to the security of your device. We recommend using device-level security features such as passcodes, Face ID, or Touch ID to protect your information.",
            textColor = textColor
        )

        // Children's Privacy
        SectionHeading(text = "Children's Privacy", textColor = textColor)
        
        BodyText(
            text = "Our Service does not address anyone under the age of 13. We do not knowingly collect personally identifiable information from children under 13. Given that we do not collect any personal data from any user, this principle naturally extends to children.",
            textColor = textColor
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeading(text: String, textColor: Color) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SubsectionHeading(text: String, textColor: Color) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun BodyText(text: String, textColor: Color) {
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        val boldPattern = "\\*\\*(.+?)\\*\\*".toRegex()
        val italicPattern = "\\*(.+?)\\*".toRegex()
        
        // Handle bold text
        boldPattern.findAll(text).forEach { matchResult ->
            // Add text before the match
            append(text.substring(lastIndex, matchResult.range.first))
            
            // Add bold text
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchResult.groupValues[1])
            }
            
            lastIndex = matchResult.range.last + 1
        }
        
        // Add remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    
    Text(
        text = annotatedString,
        fontSize = 15.sp,
        color = textColor.copy(alpha = 0.85f),
        lineHeight = 22.sp
    )
}

@Composable
private fun BulletPoint(title: String, description: String, textColor: Color) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "• $title",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Text(
            text = description,
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.7f),
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SubBulletText(text: String, textColor: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = textColor.copy(alpha = 0.65f),
        lineHeight = 19.sp,
        modifier = Modifier.padding(start = 28.dp, top = 8.dp),
        fontWeight = FontWeight.Normal
    )
}
