# Whisper Kotlin Android Boilerplate

A minimal Android app skeleton using Kotlin with optional NDK/CMake wiring. Files are placeholders for you to fill later.

## Structure
- `settings.gradle.kts` — Gradle project settings
- `build.gradle.kts` — Root Gradle build logic
- `gradle.properties` — Common Gradle config
- `app/` — Android application module
  - `build.gradle.kts` — Android module config with externalNativeBuild hook
  - `proguard-rules.pro` — Placeholder for R8 rules
  - `src/main/` — Sources and resources
    - `AndroidManifest.xml`
    - `java|kotlin/` package with `MainActivity.kt`
    - `res/` basic layouts and values
    - `assets/` placeholder
    - `cpp/` CMakeLists.txt and native sources (optional)

## Requirements
- Android Studio Giraffe+ or Gradle 8.x
- Android SDK + NDK (for native code)

## Quick start
Open the folder in Android Studio; it will import as a Gradle project. Select an emulator or device and run.
