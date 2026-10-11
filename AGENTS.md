# Central Agent Memory: Pikachu Browser

## Project Architecture & Tech Stack
- **Framework**: Kotlin Multiplatform (KMP) 2.4.20 + Compose Multiplatform 1.12.1
- **Targets**: Android (`:app:androidApp`), iOS (`:app:iosApp`), Desktop (`:app:desktopApp`), Shared (`:app:shared`), Server (`:server`)
- **Git Branch**: `kmp-browser` (kept strictly separate from the Python browser setup on `main`)
- **State Management**: Clean Architecture + MVI (`BrowserViewModel`, `ChatViewModel`, `BrowserIntent`, `ChatIntent`)
- **Dependency Injection**: Koin 4.0.0 (`koin-compose`)
- **Local Persistence**: SQLDelight 2.0.2 (`PikachuDatabase` with `Bookmarks.sq`, `History.sq`, and `Settings.sq`)
- **Networking**: Ktor 3.6.0 Client & Server
- **UI System**: Material3 with strictly SansSerif Typography, high-contrast Figma-quality design tokens, no cursive/serif default fonts.

## Key Architectural Decisions & Protocols
1. **Persistent Settings in Database**:
   - `Settings.sq` stores all user configuration toggles in `SettingsEntity(key, value)`.
   - `BrowserViewModel` loads settings at startup and automatically persists changes across app reboots:
     - `background_play_enabled`
     - `ad_block_enabled`
     - `clear_session_on_exit`
     - `private_mode`
     - `search_engine`
2. **Ephemeral Session Privacy & Feed Wipe**:
   - `clearBrowserSession()` (multiplatform expect/actual in `util/PlatformSession.kt`) invokes:
     - `CookieManager.removeAllCookies(null)`
     - `CookieManager.removeSessionCookies(null)`
     - `WebStorage.deleteAllData()`
     - `webView.clearCache(true)`
     - `webView.clearFormData()`
   - Runs automatically on app exit and startup when `clear_session_on_exit` is enabled (default `true`) or in private mode.
   - Guarantees sites like YouTube always open fresh to a clean starting homepage without pre-built tracking feeds or cached user sessions.
3. **Sleek Bottom Navigation Bar**:
   - Redundant `◀` and `▶` buttons removed from the bottom navbar.
   - Modern 4-action layout:
     - `⌂` Home: Navigates to active tab's start page.
     - `✨ Ask AI`: Direct one-tap AI companion modal drawer.
     - `[N]` Tabs Badge: Opens 2-column tab switcher.
     - `•••` Menu: Opens categorized cards modal.
4. **Tab Manager & Backstack Navigation**:
   - `TabsListScreen.kt` implements a 2-column card grid layout showing tab titles, URLs, active state borders, and close actions.
   - Selecting a tab or creating a new tab directly synchronizes Compose routes (`LandingRoute` or `BrowserRoute`) with the tab's current state.
   - Hardware back button intercepted via `CommonBackHandler`:
     - Priority 1: WebKit backstack (`webViewController.goBack()`).
     - Priority 2: Return to tab's start page (`LandingRoute`).
     - Priority 3: Sub-screen pop (`navController.popBackStack()`).
     - Priority 4: Clean app exit.
5. **Scroll Behavior & Toolbar Auto-Hiding**:
   - Scaffold uses `NestedScrollConnection` reacting to WebView scrolling deltas (`isNestedScrollingEnabled = true`).
   - Scrolling down slides Top Bar and Bottom Bar off-screen to award 100% vertical screen estate to web pages.
   - Scrolling up instantly snaps bars back into view. Bars stay permanently fixed on `LandingRoute`.
6. **AI Context Awareness**:
   - WebView JavaScript extraction automatically pulls `document.title`, `window.location.href`, and `document.body.innerText`.
   - Context is piped into `ChatDrawer` and `ChatViewModel`, displaying live connection status and providing ground-truth website context to the LLM.
7. **Three-Dots Menu**:
   - Categorized card-based layout with horizontal dividers.
   - 🛡️ Privacy Shield toggle.
   - 🔒 Private Browsing toggle.
   - 🕒 History screen (`HistoryScreen.kt` backed by SQLDelight `HistoryEntity`).
   - ⭐ Bookmarks screen (`BookmarksScreen.kt` backed by SQLDelight `BookmarkEntity`).
   - 📥 Downloads screen (`DownloadsScreen.kt`).
   - 📤 Native Share Sheet (`rememberShareLauncher` invoking Android `Intent.ACTION_SEND`).
   - ⚙️ Settings (`SettingsScreen.kt` with persistent database configs and clear data action).
   - ✨ AI Settings (`AiSettingsScreen.kt` with backend server URL configuration).
8. **Server**:
   - Native Ktor server in `:server` providing `/api/chat`, `/api/summarize`, `/api/search`, and `/api/health`.
9. **Build & Deployment**:
   - Android Build: `./gradlew :app:androidApp:assembleDebug`.
   - Server Build: `./gradlew :server:assemble`.
   - APK Location: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Change History
- **2026-10-10**:
  - Initialized `AGENTS.md` central memory.
  - Implemented `Settings.sq` in SQLDelight for persistent configuration storage across app restarts.
  - Implemented `clearBrowserSession()` wiping cookies, web storage, and cache on startup and exit.
  - Resolved infinite reload loop by tracking `lastLoadedUrl` in `PikachuWebView`.
  - Removed arrow controlling buttons from bottom navbar; added center "Ask AI" pill.
  - Rebuilt `TabsListScreen` into a modern 2-column card grid with tab title and URL tracking.
  - Fixed tab switching and backstack routing across `LandingRoute` and `BrowserRoute`.
  - Created and committed KMP codebase to dedicated `kmp-browser` git branch.
  - Implemented Brave-grade Background Streaming Engine:
    - `PikachuMediaService`: Native Android Foreground Service (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`) with low-priority media notification and `PowerManager.PARTIAL_WAKE_LOCK` to keep CPU active under lock screen and background.
    - `PikachuNativeWebView`: Overrode `onWindowVisibilityChanged`, `dispatchWindowVisibilityChanged`, `onVisibilityChanged`, `onWindowFocusChanged`, `onPause`, and `pauseTimers` to prevent Chromium from freezing media decoders and audio buffers.
    - `background_playback.bundle.js`: Injected into Android and iOS assets; overrides Page Visibility API (`hidden`, `visibilityState`, `hasFocus`), drops visibility event listeners, intercepts `HTMLMediaElement.pause` to block auto-pauses while honoring manual clicks, and maintains auto-resume watchdog.
    - Promoted `BrowserViewModel` in Koin `viewModelModule` from `factory` to `single` to share global browser state reliably across `MainActivity`, `App`, and `PikachuWebView`.
  - Created sub-branch `feature/brave-privacy` branching off `kmp-browser`.
  - Implemented Brave-Grade User Privacy Engine (Multiplatform Android & iOS):
    - `UrlSanitizer.kt`: Pure KMP URL filter stripping cross-site tracking tokens (`fbclid`, `gclid`, `utm_*`, `si`, `igshid`, `msclkid`, etc.) and automatically De-AMPing Google cache URLs to canonical publisher links.
    - `farbling.bundle.js`: Injected at `document-start` into Android WebView and iOS WKWebView (`WKUserScript`). Injects deterministic per-origin and per-session pseudo-random micro-noise into Canvas (`toDataURL`, `getImageData`), AudioContext (`getChannelData`, `getFloatFrequencyData`), and WebGL vendor/renderer parameters to neutralize fingerprinting (e.g. FingerprintJS). Emits `Sec-GPC: 1` and `DNT: 1` signals.
    - `SettingsScreen.kt`: Added user toggles for Tracking Query Filter, Fingerprinting Farbler, and De-AMPing, persisted via SQLDelight.
    - `ChatDrawer.kt`: Updated Pikachu AI assistant window with an elegant banner: *"This feature is currently not available. We will make it available soon."*
- **2026-10-11**:
  - Overhauled Tab Management and Multiplatform Backstack & Backward Swipe Architecture (Brave-Grade):
    - Replaced destructive `NavHost` route bouncing (`LandingRoute` vs `BrowserRoute`) with unified `BrowserHomeRoute` holding persistent tab-driven state.
    - Implemented `AndroidTabWebViewPool` and `IosTabWebViewPool` caching WebViews per tab (`TabManager.kt` expect/actual). Retains WebBackForwardList, scroll position, DOM state, and navigation history across tab switching with zero network reload.
    - Added `tabBackStack: List<String>` (MRU history) in `BrowserState` and intents `PopTab`, `UpdateTabNavState`, `CloseCurrentTab`.
    - Enforced Brave-grade 6-priority back navigation hierarchy in `CommonBackHandler`:
      1. Dismiss active sheets (Chat, Menu).
      2. Pop sub-screens (Settings, TabsList, Bookmarks, History).
      3. Navigate back within current tab's active WebView history (`webViewController.goBack()`).
      4. If at root of web history in tab started from Landing, return to tab Landing page (`GoHome`).
      5. Switch to previous tab in MRU `tabBackStack` (`PopTab`).
      6. System back cleans up and minimizes/exits app without abrupt jumping to default tab.
    - Added horizontal drag gesture to bottom navbar to swipe left/right between open tabs.
  - Implemented Default Search Engine Flexibility:
    - Added `SearchEngineUtil.kt` supporting `DuckDuckGo`, `Google`, `Brave Search`, and `Bing` with unified query encoding and persistent SQLite storage.
    - Replaced hardcoded DuckDuckGo URLs in address bar and landing page with dynamic engine resolution.
    - Overhauled `SettingsScreen.kt` with a sleek, Figma-quality radio selection card for all 4 search engines.

