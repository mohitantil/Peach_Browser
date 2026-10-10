---
name: pikachu-kmp-architecture
description: Professional KMP architecture guide for Pikachu Browser. Defines Clean Architecture, MVI, Koin, SQLDelight, and tracks current progress & next phases.
---

# ⚡ Pikachu Browser KMP Architecture & Progress Tracker

> **Context**: The KMP (Kotlin Multiplatform) port of Pikachu Browser is completely independent from the working Python-based desktop application. We are translating ideas (zero-persistence browsing, agentic capabilities, privacy shield) into a modern, professional Kotlin codebase.

## 🏗️ Architectural Standard Stack

We follow a strict **Clean Architecture + MVI** pattern.

### Core Technologies:
- **UI Framework**: Compose Multiplatform (CMP) for shared UI.
- **State Management**: MVI (Model-View-Intent) using `StateFlow` and `SharedFlow`.
- **Dependency Injection**: [Koin](https://insert-koin.io/) (Shared cross-platform DI graph).
- **Networking**: [Ktor](https://ktor.io/) HTTP Client (for Groq LLM API and SearXNG).
- **Persistence**: [SQLDelight](https://sqldelight.github.io/sqldelight/) (for any necessary caching, strictly controlled due to zero-persistence requirements).

### Clean Architecture Layers (in `app/shared/src/commonMain/kotlin/...`)
1. **Domain Layer (`domain/`)**: Pure Kotlin. Contains `Models`, `Repositories` (interfaces), and `UseCases`. Has NO dependencies on Ktor, Koin, or SQLDelight.
2. **Data Layer (`data/`)**: Implements Domain interfaces. Houses Ktor API clients, SQLDelight database drivers, and Data Transfer Objects (DTOs).
3. **Presentation Layer (`presentation/`)**: MVI architecture.
   - **Intent (Action)**: User actions (e.g., `SubmitSearch(query)`).
   - **Model (State)**: UI State represented as Kotlin Data Classes (`data class BrowserState(...)`).
   - **ViewModel / Store**: Receives Intents, processes via UseCases, and emits State via `StateFlow`.
4. **UI Layer (`ui/`)**: Jetpack Compose Composable functions observing `StateFlow`.

## 🔄 MVI Unidirectional Data Flow

```kotlin
// Example MVI structure for Agent interaction
sealed interface AgentIntent {
    data class SendMessage(val message: String) : AgentIntent
    object ClearChat : AgentIntent
}

data class AgentState(
    val messages: List<String> = emptyList(),
    val isLoading: Boolean = false
)

// In ViewModel
fun processIntent(intent: AgentIntent) {
    when (intent) {
        is AgentIntent.SendMessage -> {
            // 1. Update State to Loading
            // 2. Call Domain UseCase (e.g., SendQueryUseCase)
            // 3. Update State with Result
        }
    }
}
```

---

## 📈 Current Implementation Status (Progress Tracker)

**Phase 1: Project Initialization & WebView Sandboxing [✅ COMPLETED]**
- Scaffolded KMP project (Android, Desktop, iOS, Shared).
- Configured Android `WebView` via `AndroidView` inside Compose.
- Injected `privacy_shield.bundle.js` natively into Android WebKit (`WebViewCompat.addDocumentStartJavaScript`).
- Configured strictly ephemeral zero-persistence settings (`domStorageEnabled = true`, `databaseEnabled = false`).
- Integrated `PikachuWebView` into `App.kt` entrypoint.

**Phase 2: Networking & Dependency Injection [✅ COMPLETED]**
- Implemented **Koin** dependency injection across all layers (`Modules.kt`, `Koin.kt`).
- Scaffolded Ktor HTTP Client for Groq LLM proxy and SearXNG public instances (`LlmApiService`, `SearchApiService`).
- Defined `Domain` Models, Repositories, and UseCases for AI orchestration and Search.

**Phase 3: Shared UI & Chat Drawer (MVI) [✅ COMPLETED]**
- Implemented the bottom-sheet AI Chat Drawer (`ChatDrawer.kt`) using Compose Multiplatform.
- Set up MVI `ChatViewModel` and `ChatIntent` models to process user queries through `ChatWithLlmUseCase`.
- Overlaid the Chat Drawer dynamically on top of the native `PikachuWebView` in `App.kt` using Koin injection.

**Phase 4: SQLDelight & Offline Vector Stubbing [✅ COMPLETED]**
- Set up SQLDelight `Bookmarks.sq` schema for strict opt-in bookmarking.
- Provided platform-specific `DriverFactory` implementations via `expect`/`actual` for Android, Desktop (JVM), and iOS.
- Injected `PikachuDatabase` globally via the `databaseModule` in Koin.

---

## 🤝 Agent Instructions (How to use this file)
1. **Never conflate** the PySide6/Python codebase with this KMP codebase. The Python app is a reference for *features*, not code structure.
2. **Update the Tracker**: When you complete a phase or sub-task, update the `Current Implementation Status` section above so future agents know exactly where to pick up.
3. **Strict Clean Architecture**: Do not put network calls inside Composables. Always route through MVI ViewModels -> UseCases -> Repositories.
