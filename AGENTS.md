# Central Agent Memory: Peach Browser

## Project Architecture & Tech Stack
- **Framework**: Kotlin Multiplatform (KMP) 2.4.20 + Compose Multiplatform 1.12.1
- **Targets**: Android (`:app:androidApp`), iOS (`:app:iosApp`), Desktop (`:app:desktopApp`), Shared (`:app:shared`), Server (`:server`)
- **Git Branch**: `kmp-browser` (kept strictly separate from the Python browser setup on `main`)
- **State Management**: Clean Architecture + MVI (`BrowserViewModel`, `ChatViewModel`, `BrowserIntent`, `ChatIntent`)
- **Dependency Injection**: Koin 4.0.0 (`koin-compose`)
- **Local Persistence**: SQLDelight 2.0.2 (`PeachDatabase` with `Bookmarks.sq`, `History.sq`, and `Settings.sq`)
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
5. **Rock-Solid Docked Toolbars & Clean Scaffolding**:
   - The top address bar is strictly rendered only on the browser home route (`BrowserHomeRoute`), and completely omitted on sub-screens.
   - Toolbars remain cleanly docked without erratic off-screen translation or status bar clipping, ensuring instant one-tap access to URL and controls.
   - All sub-screens (`Settings`, `History`, `Bookmarks`, `Downloads`, `AiSettings`, `TabsList`) have their own native `TopAppBar` with status bar padding and generous 32dp bottom spacing so content is never cut off by the navigation bar.
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
  - Overhauled Tab Management and Multiplatform Backstack & Backward Swipe Architecture:
    - Replaced destructive `NavHost` route bouncing (`LandingRoute` vs `BrowserRoute`) with unified `BrowserHomeRoute` holding persistent tab-driven state.
    - Implemented `AndroidTabWebViewPool` and `IosTabWebViewPool` caching WebViews per tab (`TabManager.kt` expect/actual). Retains WebBackForwardList, scroll position, DOM state, and navigation history across tab switching with zero network reload.
    - Added `tabBackStack: List<String>` (MRU history) in `BrowserState` and intents `PopTab`, `UpdateTabNavState`, `CloseCurrentTab`.
    - Enforced 6-priority back navigation hierarchy in `CommonBackHandler`:
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
  - Resolved UI Inconsistencies, Double Scaffolding, and Off-Screen Toolbar Glitches:
    - Omitted top address bar entirely on sub-screens (`Settings`, `History`, `Bookmarks`, `Downloads`, `AiSettings`, `TabsList`), eliminating duplicate top bars.
    - Docked top address bar and bottom navigation bar cleanly without nested scroll offsets that previously clipped into the status bar or slid toolbars off-screen.
    - Fixed bottom navigation bar `⌂` Home icon when clicked inside `SettingsScreen` and other sub-screens, popping to `BrowserHomeRoute` and resetting active tab to the home start page.
  - Resolved WebView Navigation Feedback Loop, Auto-Refresh & Address Bar Flickering (v1.0.1):
    - Fixed circular dependency between Chromium's internal backstack and ViewModel's `Tab.url`. In `PikachuWebView.android.kt` and `PikachuWebView.ios.kt`, `AndroidView.update` now checks `currentWebUrl == webView.url` and keeps `lastLoadedUrl` synchronized directly with `onPageStarted` and `onPageFinished`, preventing destructive force-reloads on back navigation and link clicks.
    - Implemented `WebChromeClient.onCreateWindow` with transport WebView and enabled `setSupportMultipleWindows(true)` and `javaScriptCanOpenWindowsAutomatically(true)`, allowing search engine results with `target="_blank"` and `window.open` (such as Brave Search) to open reliably.
    - Added safe scheme routing in `shouldOverrideUrlLoading` for external intents (`intent://`, `tel:`, `mailto:`).
    - Added `PikachuWebViewController.reset()` to wipe controller URL, title, and loading state whenever transitioning to the Landing/Home page or switching tabs.
    - Fixed address bar `LaunchedEffect` in `App.kt` to clear `addressText = ""` whenever on `isLandingPage`, resolving address bar flickering showing stale session URLs (e.g. YouTube).
    - Cleared `AndroidTabWebViewPool` on cold start in `BrowserViewModel` when `clear_session_on_exit` is active, and released tab WebView on `GoHome`.
- **2026-10-12**:
  - Rebranding to "Peach Browser":
    - Replaced copyrighted Pikachu branding and mascot asset with a 3D clay-style peach portrait featuring pastel gradient coloring (`peach_hero.jpg` in `composeResources/drawable`).
    - Updated UI headers, strings, AI assistant prompt, and metadata across `LandingPage`, `MenuOverlay`, `TabsListScreen`, `AiSettingsScreen`, `ChatDrawer`, `ChatViewModel`, and `strings.xml`.
  - Implemented Fullscreen & Landscape Video Engine (Android WebView):
    - Added `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` to `MainActivity` in `AndroidManifest.xml` to prevent activity recreation and video reloads on device rotation.
    - Implemented `WebChromeClient.onShowCustomView` and `onHideCustomView` in `PikachuWebView.android.kt`, mounting Chromium's video player into a black fullscreen overlay decor view.
    - Added immersive system bar hiding (`WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and automatic switch to `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE` for edge-to-edge landscape viewing.
    - Integrated priority 0 back navigation handling in `CommonBackHandler` and `PikachuWebViewController` to smoothly dismiss custom view fullscreen on back button or gesture, restoring portrait orientation and system bars.
    - Formulated comprehensive end-to-end plan for folder and package namespace refactoring (`PikachuBrowser` -> `PeachBrowser`).
  - Implemented Clock-Synced Music & Video Sleep Timer Engine (Android & iOS):
    - `MediaDetector.kt`: Domain detector matching YouTube Music, YouTube, Spotify, Gaana, JioSaavn, Apple Music, SoundCloud, Pandora, Tidal, Deezer, etc.
    - `media_detector.bundle.js`: Real-time media output detector injected into Android & iOS WebViews inspecting audio/video elements and MediaSession playback status. Emits state via `PeachNativeBridge` and `webkit.messageHandlers.peachMedia`.
    - `SleepTimerSheet.kt`: Material3 modal sheet supporting 15m, 30m, 45m, 60m presets and custom duration inputs.
    - Wall-Clock Sync: Countdown driven by `targetEndTimeMillis` preventing ticker drift when device is locked or backgrounded.
    - Configurable Expiration Actions:
      - Pause Playback: Invokes `pauseMedia()` Javascript bridge across all active media elements without terminating the tab.
      - Close Tab: Smoothly closes the streaming tab.
      - Close Browser: Triggers `exitBrowserApp()` invoking Android `Activity.finishAffinity()` / iOS clean exit.
    - Sleek Floating Pill Button: Positioned above bottom navigation bar when streaming audio or while sleep timer is active with live countdown ticker. Also accessible via 3-dots Menu.
  - Implemented Ephemeral Sensor Permissions (Microphone & Camera):
    - Strict session-based RAM storage (`SessionPermissionManager.kt`), never persisted to database or disk.
    - Handled via `WebChromeClient.onPermissionRequest` on Android and WKWebView permission handlers on iOS.
    - Instant auto-purge on page navigation (`onPageStarted`), tab closure (`CloseTab`), or app exit (`onDestroy`).
    - Ongoing notification indicator alert (`🔴 Microphone/Camera in use by [origin]`).
  - Completed Full Project and Namespace Refactoring (`PeachBrowser`):
    - Renamed root directory to `PeachBrowser` in the repository and committed cleanly.
    - Updated package namespaces from `com.example.pikachubrowser` to `com.example.peachbrowser` across all modules (`:app:androidApp`, `:app:shared`, `:app:desktopApp`, `:app:iosApp`, `:core`, `:server`).
    - Renamed database to `PeachDatabase` (`com.example.peachbrowser.db`) in SQLDelight.
    - Renamed classes (`PeachApplication`, `PeachMediaService`, `PeachWebView`, `PeachWebViewController`, `PeachNativeWebView`).
    - Resolved App Launch Crash:
      1. Fixed `ForegroundServiceDidNotStartInTimeException`: called `startForegroundPlayback()` immediately in `PeachMediaService.onCreate()` and added `isAudioPlaying` guard in `MainActivity.onPause()`.
      2. Fixed `MissingResourceException`: placed `peach_hero.jpg` into `peachbrowser.app.shared.generated.resources` within `androidApp/src/main/assets` and verified Compose multiplatform resource generation.
    - Successfully built and installed on connected physical device with live execution verified.
  - Python Workspace Separation & Isolated Refactor Tools:
    - Moved legacy desktop Python browser implementation into dedicated `Peach-Python/` directory.
    - Preserved `refactor_project.py` script in `refactor_tools/` outside Git tracking and isolated from KMP.
  - Resolved Sleep Timer Background Pause & Expiration:
    - Added `window.__PEACH_PAUSE_ALL_MEDIA__` bridge and `__peach_sleep_timer_paused` flag in `background_playback.bundle.js` preventing background auto-pause blocker and watchdog from overriding sleep timer pauses.
    - Wrapped `pauseMediaAction` execution in main looper `post { ... }` in `PeachWebView.android.kt`.
    - Added `stopPlatformMediaService()` expect/actual stopping foreground service notification and wake lock when sleep timer pauses audio or when exiting app.
- **2026-10-13**:
  - Resolved Back Navigation Reload Loops & Circular State Dependency:
    - Removed destructive `webView.loadUrl(url)` and redundant script re-evaluations from Compose `AndroidView.update` and `UIKitView.update` blocks in `PeachWebView.android.kt` and `PeachWebView.ios.kt`. Initial page load is handled on creation and explicit navigations via `controller.loadUrlAction`.
    - Implemented `PeachNativeWebView.goBackSmartly()` and `canGoBackSmartly()`: inspects `copyBackForwardList()`, automatically collapses redirect loops, anchor/hash jumps, and duplicate URLs (e.g. YouTube, DuckDuckGo), and cleanly navigates back multiple steps if needed.
    - Streamlined `CommonBackHandler` hierarchy in `App.kt`: eliminated dead branch (`activeTab.initialUrl.isBlank()`) and removed erratic MRU tab-bouncing (`hasTabBackHistory -> PopTab`). Swiping back at the root of a tab's web history immediately transitions to the Landing page in 1 single gesture.
    - Back gesture on Landing page cleanly minimizes/exits app without hijacking navigation.
  - Resolved Session Restore Bug on Background Close:
    - Added `BrowserIntent.ResetToCleanSession` and `resetToCleanSession()` in `BrowserViewModel`: clears all tab WebViews from pool, invokes `clearBrowserSession()`, and resets `tabs` back to a clean initial Landing state.
    - Added clean session reset guard in `MainActivity.onCreate()` and `onDestroy()`: ensures reopening the browser from background or warm cache starts fresh on the Landing page when `clear_session_on_exit` is active.
    - Implemented `onTaskRemoved()` in `PeachMediaService`: releases wake locks and stops foreground service when app task is swiped from Android Recents.
  - Eliminated Legacy Branding Remnants & Optimized Project:
    - Renamed JavaScript injection flags from `__PIKACHU_*__` to `__PEACH_*__` across Android and iOS assets (`background_playback.bundle.js`, `privacy_shield.bundle.js`, `farbling.bundle.js`).
    - Updated iOS Xcode project configs (`Config.xcconfig`, `project.pbxproj`) from `PikachuBrowser` to `PeachBrowser`.
    - Completely deleted obsolete root directory `PikachuBrowser/` using robocopy to bypass long-path limits.
    - Removed duplicate `refactor_project.py` at root and streamlined `.gitignore`.
    - Renamed and updated skill references (`peach-browser-dev`, `peach-kmp-architecture`).

