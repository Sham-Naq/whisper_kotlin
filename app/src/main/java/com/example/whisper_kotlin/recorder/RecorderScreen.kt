package com.example.whisper_kotlin.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.whisper_kotlin.WhisperEngine

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    textColor: Color,
    command: RecorderCommand? = null,
    onCommandHandled: () -> Unit = {},
    onRecordingStateChanged: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Recorder state must be defined before it's referenced in the permission callback
    val recorder = remember { AudioRecorder(scope = scope) }
    var isRecording by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf<String?>(null) }
    // temp file lifecycle
    val cacheDir = context.cacheDir
    var rawFile by remember { mutableStateOf<java.io.File?>(null) }
    var wavFile by remember { mutableStateOf<java.io.File?>(null) }
    // Helper to start recording into a raw PCM file and reset transcript
    val startRecording: () -> Unit = {
        val rf = java.io.File(cacheDir, "rec_${'$'}{System.currentTimeMillis()}.pcm")
        rawFile = rf
        recorder.start(rf)
        transcript = null
        isRecording = true
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Start recording immediately on grant
            startRecording()
        }
    }
    val bars by recorder.bars.collectAsState()

    // React to external commands
    LaunchedEffect(command) {
        when (command) {
            is RecorderCommand.Start -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    startRecording()
                    onRecordingStateChanged(true)
                } else {
                    requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                onCommandHandled()
            }
            is RecorderCommand.Pause -> {
                if (isRecording) {
                    recorder.pause()
                    onRecordingStateChanged(true)
                }
                onCommandHandled()
            }
            is RecorderCommand.Resume -> {
                if (isRecording) {
                    recorder.resume()
                    onRecordingStateChanged(true)
                }
                onCommandHandled()
            }
            is RecorderCommand.StopAndTranscribe -> {
                if (isRecording) {
                    // mirror the stop click
                    scope.launch(Dispatchers.IO) {
                        recorder.stop()
                        val rf = rawFile
                        if (rf != null && rf.exists() && rf.length() > 0) {
                            val wf = java.io.File(cacheDir, rf.nameWithoutExtension + ".wav")
                            try {
                                WavWriter16kMonoPcm16.wrapRawPcmToWav(rf, wf)
                                wavFile = wf
                                val text = WhisperEngine.transcribeWavFile(wf.absolutePath)
                                transcript = text
                            } catch (t: Throwable) {
                                transcript = "Transcription failed: ${'$'}t"
                            }
                        } else {
                            transcript = "No audio captured"
                        }
                    }
                    isRecording = false
                    onRecordingStateChanged(false)
                }
                onCommandHandled()
            }
            null -> {}
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        // Waveform area
        LineBarWaveform(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(if (isDark) Color(0xFF181818) else Color(0xFFF0F0F0)),
            bars = bars,
            color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1E88E5),
            backgroundColor = null
        )

        Spacer(Modifier.weight(1f))

        // The actual Start/Stop control is driven by the nav mic overlay; spacer keeps layout similar
        Spacer(Modifier.height(96.dp))
        // Transcript output
        transcript?.let { text ->
            androidx.compose.foundation.text.BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(color = textColor)
            )
        }
    }
}
