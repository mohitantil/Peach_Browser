---
name: peach-kmp-architecture
description: Professional KMP architecture guide for Peach Browser. Defines Clean Architecture, MVI, Koin, SQLDelight, and tracks current progress & next phases.
---

# 🍑 Peach Browser KMP Architecture & Progress Tracker

> **Context**: The KMP (Kotlin Multiplatform) codebase of Peach Browser is a modern, production-grade browser targeting Android, iOS, and Desktop. Clean Architecture + MVI ensures state predictability and zero data leaks.

## 🏗️ Architectural Standard Stack

We follow a strict **Clean Architecture + MVI** pattern.

### Core Technologies:
- **UI Framework**: Compose Multiplatform (CMP) 1.12.1 for shared UI.
- **State Management**: MVI (Model-View-Intent) using `StateFlow` and `SharedFlow`.
- **Dependency Injection**: [Koin](https://insert-koin.io/) 4.0.0 (`koin-compose`).
- **Networking**: [Ktor](https://ktor.io/) 3.6.0 Client & Server.
- **Persistence**: [SQLDelight](https://sqldelight.github.io/sqldelight/) 2.0.2 (`PeachDatabase` with `Bookmarks.sq`, `History.sq`, `Settings.sq`).

### Clean Architecture Layers (in `PeachBrowser/app/shared/src/commonMain/kotlin/...`)
1. **Domain Layer (`domain/`)**: Pure Kotlin. Contains `Models`, `Repositories` (interfaces), and `UseCases`. Has NO dependencies on Ktor, Koin, or SQLDelight.
2. **Data Layer (`data/`)**: Implements Domain interfaces. Houses Ktor API clients, SQLDelight database drivers, and Data Transfer Objects (DTOs).
3. **Presentation Layer (`presentation/`)**: MVI architecture.
   - **Intent (Action)**: User actions (e.g., `SubmitSearch(query)`).
   - **Model (State)**: UI State represented as Kotlin Data Classes (`data class BrowserState(...)`).
   - **ViewModel / Store**: Receives Intents, processes via UseCases, and emits State via `StateFlow`.
4. **UI Layer (`ui/`)**: Jetpack Compose Composable functions observing `StateFlow`.

---

## 📈 Current Implementation Status (Progress Tracker)

**Phase 1: Project Initialization & WebView Sandboxing [✅ COMPLETED]**
- Scaffolded KMP project (Android, Desktop, iOS, Shared).
- Configured Android `WebView` via `AndroidView` inside Compose.
- Injected `privacy_shield.bundle.js` natively into Android WebKit (`WebViewCompat.addDocumentStartJavaScript`).
- Configured strictly ephemeral zero-persistence settings (`domStorageEnabled = true`, `databaseEnabled = false`).
- Integrated `PeachWebView` into `App.kt` entrypoint.

**Phase 2: Networking & Dependency Injection [✅ COMPLETED]**
- Implemented **Koin** dependency injection across all layers (`Modules.kt`, `Koin.kt`).
- Scaffolded Ktor HTTP Client for Groq LLM proxy and SearXNG public instances (`LlmApiService`, `SearchApiService`).
- Defined `Domain` Models, Repositories, and UseCases for AI orchestration and Search.

**Phase 3: Shared UI & Chat Drawer (MVI) [✅ COMPLETED]**
- Implemented the bottom-sheet AI Chat Drawer (`ChatDrawer.kt`) using Compose Multiplatform.
- Set up MVI `ChatViewModel` and `ChatIntent` models to process user queries.
- Overlaid the Chat Drawer dynamically on top of the native `PeachWebView` in `App.kt`.

**Phase 4: SQLDelight Persistence & Settings Storage [✅ COMPLETED]**
- Set up SQLDelight `Bookmarks.sq`, `History.sq`, `Settings.sq` schemas.
- Provided platform-specific `DriverFactory` implementations via `expect`/`actual` for Android, Desktop (JVM), and iOS.
- Injected `PeachDatabase` globally via the `databaseModule` in Koin.

**Phase 5: Background Playback, Privacy Shield & Farbling [✅ COMPLETED]**
- Android foreground service `PeachMediaService` with wake lock.
- Chromium visibility hook in `PeachNativeWebView` preventing audio/video freezing.
- Canvas, WebGL, and AudioContext noise injector `farbling.bundle.js`.
- URL sanitizer stripping tracking tokens and De-AMPing cache links.

**Phase 6: Rebranding, Navigation Loop Elimination & Session Sanitation [✅ COMPLETED]**
- Fully rebranded from Pikachu to Peach across all modules, assets, and iOS targets.
- Fixed back navigation circular reload loop in `PeachWebView.android.kt` and `PeachWebView.ios.kt`.
- Implemented `goBackSmartly()` preventing redirect loops on SPAs (YouTube, DuckDuckGo).
- Fixed back hierarchy in `App.kt` returning to Landing page in 1 gesture without tab jumping.
- Implemented `resetToCleanSession()` ensuring the browser always starts fresh on Landing page when reopened from background.
- Cleaned up obsolete directories and streamlined `.gitignore`.
