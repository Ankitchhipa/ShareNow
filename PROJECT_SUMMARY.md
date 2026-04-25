# Share Now Project Summary

## 1. Project Overview

This project is a **Kotlin Multiplatform + Compose Multiplatform** mobile file-sharing app targeting:

- Android
- iOS

The project root is still named `Xherit`, but the **current Android app name is `Share Now`** as defined in:

- `composeApp/src/androidMain/res/values/strings.xml`

The app is designed as a **modern premium file transfer experience** inspired by apps like SHAREit / EasyShare, with:

- futuristic dark-themed visuals
- glassmorphism cards
- neon action styling
- QR-based device pairing
- hotspot / local-network-based transfer logic
- file browser and categorized picker
- transfer history
- file preview support

## 2. Current App Flow

The main shared app entry is:

- `composeApp/src/commonMain/kotlin/com/xherit/App.kt`

That renders:

- `FileShareApp()` from `composeApp/src/commonMain/kotlin/com/xherit/fileshare/ui/FileShareApp.kt`

The current screen flow implemented in `AppScreen` is:

1. Splash
2. Onboarding
3. Home
4. File Selection
5. Scanning / QR flow
6. Transfer Progress
7. Success
8. History
9. Profile
10. Preview

This is driven with local `remember` state inside `FileShareApp`, not a dedicated navigation library.

## 3. Architecture Understanding

### Shared Layers

The project mostly follows a lightweight layered structure:

- `model/` for UI/domain models
- `data/` for transfer, history, and connection logic
- `platform/` for expect/actual platform hooks
- `ui/` for app shell, theme, components, and screens

### Main Shared Files

- `fileshare/model/FileShareModels.kt`
- `fileshare/data/ConnectionManager.kt`
- `fileshare/data/TransferManager.kt`
- `fileshare/data/HistoryManager.kt`
- `fileshare/platform/FileSharePlatform.kt`
- `fileshare/ui/FileShareApp.kt`

### Important Note

There are **two architectural styles present at once**:

1. `FileShareApp.kt` currently orchestrates flow directly with `ConnectionManager` and `TransferManager`.
2. `presentation/FileShareViewModels.kt` contains `SendViewModel` and `ReceiveViewModel`, but the current `FileShareApp.kt` does **not** appear to use them.

So the project has **partially overlapping approaches**:

- direct state orchestration in the UI
- view-model-based orchestration prepared separately

This is one of the main areas where the project could be cleaned up.

## 4. Core Domain Models

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/model/FileShareModels.kt`

Key models:

- `ConnectionPayload`
  - QR payload containing:
    - host
    - port
    - device name
    - optional hotspot SSID
    - optional hotspot password
- `SharedFile`
  - selected file metadata
  - supports:
    - in-memory bytes
    - file path
    - thumbnail
    - directory flag
    - modified time
- `ReceivedFile`
  - received file metadata and saved path
- `TransferProgress`
  - overall and per-file progress
  - speed formatting
  - ETA formatting
- `PermissionUiState`
  - camera
  - storage
  - all files
  - network
- `TransferHistoryItem`
  - sent/received history records

There are also enums for:

- file categories
- app screens
- history filters
- transfer direction
- share mode

## 5. File Transfer Logic

### ConnectionManager

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/data/ConnectionManager.kt`

What it does:

- starts a local hotspot via platform hooks
- starts a server socket
- builds a QR payload for the receiver
- accepts incoming socket connections
- connects receiver side to hotspot and then socket
- retries socket connection several times for stability
- stops hotspot/server when done

Important behavior:

- sender tries to expose hotspot credentials in QR payload
- receiver can connect to hotspot before socket connection
- fallback path exists if hotspot startup fails

### TransferManager

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/data/TransferManager.kt`

What it does:

- sends total transfer size
- sends file count
- sends metadata for all files first
- then streams file contents in chunks
- receives metadata first on receiver side
- writes received chunks to a temp file path via platform functions

Transfer protocol currently supports:

- filenames
- file sizes
- thumbnails
- chunked transfer
- overall and per-file progress

## 6. UI / Design System

### Theme

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/ui/theme/Theme.kt`

Current style:

- dark premium background
- blue / violet primary accent
- glass-like surfaces
- rounded shapes
- bold typography

Despite the function name `FuturisticTheme`, it currently applies a single color scheme and comments indicate it is effectively fixed rather than truly dual-theme.

### Reusable Components

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/ui/components/FuturisticComponents.kt`

Key reusable pieces:

- `ShimmerEffect`
- `FuturisticBackground`
- `GlassCard`
- `NeonButton`
- `NeonOutlineButton`
- `PermissionBottomSheet`
- `FuturisticBottomBar`

These form the app’s main visual design system.

## 7. Main Screens and Their Roles

The project has a richer screen set under:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/ui/screens/`

### SplashScreen

- animated logo intro
- branding
- auto-advances after delay

### OnboardingScreen

- onboarding flow before entering the main app

### HomeScreen

- app dashboard
- send / receive emphasis
- device label
- quick stats
- currently shows branding such as `SHARE NOW`

### FileSelectionScreen

- category-based browsing
- image/video/audio/internal storage tabs
- internal storage navigation with breadcrumb-like behavior
- supports preview before sending
- supports multi-selection

### ScanningScreen

- dual-use screen
- sender mode shows QR code
- receiver mode supports:
  - radar-style waiting UI
  - QR scanner mode

### TransferProgressScreen

- large circular progress
- progress percentage
- speed
- time remaining
- cancel action

### SuccessScreen

- success state after transfer
- supports previewing received files
- send more / back home actions

### HistoryScreen

- all / sent / received tabs
- reads data from `HistoryManager`
- allows previewing received items

### ProfileScreen

- profile/settings style UI

### PreviewScreen

- file preview screen for shared/received content

## 8. History System

Defined in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/data/HistoryManager.kt`

Current behavior:

- uses an in-memory `MutableStateFlow`
- prepends new items
- has placeholder `loadHistory()`

Important limitation:

- history is **not persisted**
- app restarts will lose history unless persistence is added later

## 9. Platform Abstraction Layer

Declared in:

- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.kt`
- `composeApp/src/commonMain/kotlin/com/xherit/fileshare/platform/SocketBridge.kt`

This layer abstracts:

- current device name
- local IP
- file read/write chunk APIs
- temporary file paths
- QR generation
- permission handling
- file picking
- file querying by category
- directory listing
- storage stats
- QR scanner UI
- media players
- back handler
- current time
- hotspot start/stop
- Wi-Fi connection
- low-level socket/server socket

This is the most important cross-platform seam in the project.

## 10. Android Implementation Status

Main Android platform file:

- `composeApp/src/androidMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.android.kt`

Android is the **most complete platform implementation**.

Implemented capabilities include:

- device name lookup from:
  - global device settings
  - Bluetooth name
  - secure settings
  - manufacturer/model fallback
- local IP discovery
- chunked file reading and writing
- QR generation via ZXing
- runtime permission flow
- file picker
- category-based file loading
- storage stats
- CameraX + ML Kit QR scanning
- Android media playback support
- Android back handling
- socket/server-socket support
- hotspot and Wi-Fi related hooks

Android manifest:

- `composeApp/src/androidMain/AndroidManifest.xml`

It requests many permissions, including:

- camera
- internet
- Wi-Fi/network state
- location
- Bluetooth
- media read access
- manage external storage

This confirms Android is intended to support a deep local-sharing workflow.

## 11. iOS Implementation Status

Main iOS platform file:

- `composeApp/src/iosMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.ios.kt`

iOS is currently **only partially implemented**.

What exists:

- device name
- current time
- placeholder permission reporting
- placeholder QR scanner UI
- hotspot unsupported message

What is still scaffolded / incomplete:

- real socket transport
- real QR generation
- real file picker
- file reading/writing
- real media/file browsing
- real scanner implementation

This means the project is **functionally much stronger on Android than on iOS right now**.

## 12. Build / Tooling Setup

Important Gradle files:

- `settings.gradle.kts`
- `build.gradle.kts`
- `composeApp/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`

Key stack from build config:

- Kotlin Multiplatform
- Compose Multiplatform
- Android application plugin
- Material 3
- CameraX
- ML Kit barcode scanning
- ZXing
- Kamel image loading
- Multiplatform Settings
- kotlinx-datetime
- Media3
- Ktor client dependencies

Targets configured:

- Android
- iOS Arm64
- iOS Simulator Arm64

## 13. Tests Present

Common tests exist in:

- `composeApp/src/commonTest/kotlin/com/xherit/fileshare/data/TransferManagerTest.kt`
- `composeApp/src/commonTest/kotlin/com/xherit/fileshare/model/ConnectionPayloadTest.kt`
- `composeApp/src/commonTest/kotlin/com/xherit/fileshare/model/TransferProgressTest.kt`

Coverage is currently light and mostly focused on:

- payload encode/decode
- transfer progress math
- simple transfer-related logic

There is **not yet deep protocol or platform-level testing**.

## 14. Current Project Strengths

- Clear product direction: premium file-sharing app
- Good Compose-based visual ambition
- Useful shared models for transfer state
- Chunked file-transfer logic exists
- QR + hotspot concept is integrated into architecture
- Android side is fairly feature-rich
- History and preview workflows already exist
- Platform abstraction is reasonably well separated

## 15. Current Risks / Gaps

### Architectural overlap

- `FileShareApp` is currently doing a lot of orchestration directly
- `SendViewModel` / `ReceiveViewModel` exist but are not the primary live flow

### iOS incompleteness

- many iOS platform hooks are placeholders

### History persistence

- currently in-memory only

### State management complexity

- app navigation and transfer logic are all handled inside one large composable shell

### Branding inconsistency

The project currently mixes names:

- root project name: `Xherit`
- older branding in some screens/components: `Xherit`, `XHERIT`
- current Android app label: `Share Now`
- home screen text also shows `SHARE NOW`

This suggests branding migration is in progress but not fully completed.

## 16. My Understanding of the Project

This is a **premium-looking local file sharing app** being built with Compose Multiplatform, with Android as the main working target and iOS still in scaffold stage.

The app’s intended experience is:

- open app
- pass onboarding
- choose send or receive
- sender picks files
- sender generates QR and possibly hosts hotspot
- receiver scans QR
- receiver joins local connection
- files transfer with progress/speed UI
- results appear in success and history screens
- received files can be previewed

From a product perspective, the project is already beyond a starter app. It has:

- a real transfer protocol
- real Android QR and permission plumbing
- a polished visual direction
- a complete app shell with multiple screens

From an engineering perspective, the next high-value improvements would be:

1. unify state management around a single architecture
2. finish iOS native platform implementation
3. persist history
4. remove branding inconsistencies
5. add stronger automated tests

## 17. Suggested Important Files to Read First

If someone new joins the project, these are the best files to start with:

1. `composeApp/src/commonMain/kotlin/com/xherit/fileshare/ui/FileShareApp.kt`
2. `composeApp/src/commonMain/kotlin/com/xherit/fileshare/model/FileShareModels.kt`
3. `composeApp/src/commonMain/kotlin/com/xherit/fileshare/data/ConnectionManager.kt`
4. `composeApp/src/commonMain/kotlin/com/xherit/fileshare/data/TransferManager.kt`
5. `composeApp/src/commonMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.kt`
6. `composeApp/src/androidMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.android.kt`
7. `composeApp/src/iosMain/kotlin/com/xherit/fileshare/platform/FileSharePlatform.ios.kt`

## 18. Final Summary in One Line

**Share Now is a Compose Multiplatform premium local file-sharing app with strong Android-side implementation, a futuristic multi-screen UI, shared transfer logic, and a still-incomplete iOS platform layer.**
