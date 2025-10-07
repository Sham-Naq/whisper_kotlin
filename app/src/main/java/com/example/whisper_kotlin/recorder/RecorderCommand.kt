package com.example.whisper_kotlin.recorder

sealed class RecorderCommand {
    data object Start : RecorderCommand()
    data object StopAndTranscribe : RecorderCommand()
    data object Pause : RecorderCommand()
    data object Resume : RecorderCommand()
}
