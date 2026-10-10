# Central Agent Memory: Pikachu Browser

## Project Architecture & Tech Stack
- **Framework**: Kotlin Multiplatform (KMP) 2.4.20 + Compose Multiplatform 1.12.1
- **Targets**: Android (`:app:androidApp`), iOS (`:app:iosApp`), Desktop (`:app:desktopApp`), Shared (`:app:shared`), Server (`:server`)
- **State Management**: Clean Architecture + MVI (`BrowserViewModel`, `ChatViewModel`, `BrowserIntent`, `ChatIntent`)
- **Dependency Injection**: Koin 4.0.0 (`koin-compose`)
- **Local Persistence**: SQLDelight 2.0.2 (`PikachuDatabase` with `Bookmarks.sq` and `History.sq`)
- **Networking**: Ktor 3.6.0 Client & Server
- **UI System**: Material3 with strictly SansSerif Typography, high-contrast Figma-quality design tokens, no cursive/serif default fonts.

## Key Architectural Decisions & Protocols
1. **Native WebView Backstack**:
   - `PikachuWebViewController` wraps native WebKit engine capabilities (`webView.goBack()`, `webView.goForward()`, `webView.reload()`, and WebChromeClient progress/title tracking).
   - Bottom navigation `◀` and `▶` directly invoke native controller methods, preserving in-memory JavaScript state, scroll positions, and bfcache.
2. **Back Navigation**:
   - Hardware back button intercepted via `CommonBackHandler` (Android `BackHandler` multiplatform expect/actual).
   - Priority 1: `webViewController.goBack()` if `canGoBack`.
   - Priority 2: `navController.popBackStack()` if navigating sub-screens (Tabs, Settings, History, Bookmarks, Downloads, AI Settings).
   - Priority 3: Exit/minimize app cleanly.
3. **Scroll Behavior & Toolbar Auto-Hiding**:
   - Scaffold uses `NestedScrollConnection` reacting to WebView scrolling deltas (`isNestedScrollingEnabled = true`).
   - Scrolling down smoothly slides Top Bar and Bottom Bar off-screen to award 100% vertical screen estate to web pages.
   - Scrolling up instantly snaps bars back into view.
   - Bars stay permanently visible on `LandingRoute`.
4. **AI Context Awareness**:
   - WebView JavaScript extraction automatically pulls `document.title`, `window.location.href`, and `document.body.innerText`.
   - Context is piped into `ChatDrawer` and `ChatViewModel`, displaying live connection status and providing ground-truth website context to the LLM.
   - Text field avoids soft keyboard via automatic window insets without manual scrolling; includes Enter key action and sleek send button.
5. **Three-Dots Menu**:
   - Categorized card-based layout with horizontal dividers.
   - 🛡️ Privacy Shield toggle (toggles ad/tracker blocking script).
   - 🔒 Private Browsing toggle (ephemeral cookies, non-persistent storage).
   - 🕒 History screen (`HistoryScreen.kt` backed by SQLDelight `HistoryEntity`).
   - ⭐ Bookmarks screen (`BookmarksScreen.kt` backed by SQLDelight `BookmarkEntity`).
   - 📥 Downloads screen (`DownloadsScreen.kt`).
   - 📤 Native Share Sheet (`rememberShareLauncher` invoking Android `Intent.ACTION_SEND`).
   - ⚙️ Settings (`SettingsScreen.kt` with Brave background streaming toggle).
   - ✨ AI Settings (`AiSettingsScreen.kt` with backend server URL configuration).
6. **Server**:
   - Native Ktor server in `:server` providing:
     - `POST /api/chat`: Context-aware streaming & chat completion.
     - `POST /api/summarize`: Executive page summary extraction.
     - `GET /api/search`: SearXNG cascading aggregator.
     - `GET /api/health`: Health monitoring.
7. **Build & Deployment**:
   - Android Build: `./gradlew :app:androidApp:assembleDebug` (Verified: `BUILD SUCCESSFUL`).
   - Server Build: `./gradlew :server:assemble` (Verified: `BUILD SUCCESSFUL`).
   - APK Location: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Change History
- **2026-10-10**:
  - Initialized `AGENTS.md` central memory.
  - Resolved font rendering glitches by enforcing SansSerif CustomTypography.
  - Fixed QuickLinkBox contrast and implemented interactive "Add Site" modal on Landing Page.
  - Implemented `PikachuWebViewController` for native WebKit back/forward navigation.
  - Built `CommonBackHandler` multiplatform expect/actual for hardware back navigation.
  - Added `NestedScrollConnection` auto-hiding bars in `App.kt`.
  - Implemented `History.sq` in SQLDelight and wired History & Bookmarks persistence.
  - Created `HistoryScreen`, `BookmarksScreen`, `DownloadsScreen`, and `AiSettingsScreen`.
  - Wired native Android share sheet with `rememberShareLauncher()`.
  - Fixed `ChatDrawer` keyboard overlap, added webpage context pill, and replaced button with sleek send action.
  - Built Ktor backend endpoints in `:server` matching SearXNG/LLM pipeline.
  - Verified clean builds for both Android and Server targets.
