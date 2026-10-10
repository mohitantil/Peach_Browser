# Central Agent Memory: Pikachu Browser

## Project Architecture & Tech Stack
- **Framework**: Kotlin Multiplatform (KMP) 2.4.20 + Compose Multiplatform 1.12.1
- **Targets**: Android (`:app:androidApp`), iOS (`:app:iosApp`), Desktop (`:app:desktopApp`), Shared (`:app:shared`), Server (`:server`)
- **Git Branch**: `kmp-browser` (kept strictly separate from the Python browser setup on `main`)
- **State Management**: Clean Architecture + MVI (`BrowserViewModel`, `ChatViewModel`, `BrowserIntent`, `ChatIntent`)
- **Dependency Injection**: Koin 4.0.0 (`koin-compose`)
- **Local Persistence**: SQLDelight 2.0.2 (`PikachuDatabase` with `Bookmarks.sq` and `History.sq`)
- **Networking**: Ktor 3.6.0 Client & Server
- **UI System**: Material3 with strictly SansSerif Typography, high-contrast Figma-quality design tokens, no cursive/serif default fonts.

## Key Architectural Decisions & Protocols
1. **Native WebView & Reload Loop Prevention**:
   - `PikachuWebView` tracks `lastLoadedUrl` via Compose `remember`, preventing infinite reload loops on recompositions.
   - Native WebKit engine backstack utilized via `PikachuWebViewController`.
2. **Sleek Bottom Navigation Bar**:
   - Redundant `◀` and `▶` buttons removed from the bottom navbar.
   - Modern 4-action layout:
     - `⌂` Home: Navigates to active tab's start page.
     - `✨ Ask AI`: Direct one-tap AI companion modal drawer.
     - `[N]` Tabs Badge: Opens 2-column tab switcher.
     - `•••` Menu: Opens categorized cards modal.
3. **Tab Manager & Backstack Navigation**:
   - `TabsListScreen.kt` implements a 2-column card grid layout showing tab titles, URLs, active state borders, and close actions.
   - Selecting a tab or creating a new tab directly synchronizes Compose routes (`LandingRoute` or `BrowserRoute`) with the tab's current state.
   - Hardware back button intercepted via `CommonBackHandler`:
     - Priority 1: WebKit backstack (`webViewController.goBack()`).
     - Priority 2: Return to tab's start page (`LandingRoute`).
     - Priority 3: Sub-screen pop (`navController.popBackStack()`).
     - Priority 4: Clean app exit.
4. **Scroll Behavior & Toolbar Auto-Hiding**:
   - Scaffold uses `NestedScrollConnection` reacting to WebView scrolling deltas (`isNestedScrollingEnabled = true`).
   - Scrolling down slides Top Bar and Bottom Bar off-screen to award 100% vertical screen estate to web pages.
   - Scrolling up instantly snaps bars back into view. Bars stay permanently fixed on `LandingRoute`.
5. **AI Context Awareness**:
   - WebView JavaScript extraction automatically pulls `document.title`, `window.location.href`, and `document.body.innerText`.
   - Context is piped into `ChatDrawer` and `ChatViewModel`, displaying live connection status and providing ground-truth website context to the LLM.
6. **Three-Dots Menu**:
   - Categorized card-based layout with horizontal dividers.
   - 🛡️ Privacy Shield toggle.
   - 🔒 Private Browsing toggle.
   - 🕒 History screen (`HistoryScreen.kt` backed by SQLDelight `HistoryEntity`).
   - ⭐ Bookmarks screen (`BookmarksScreen.kt` backed by SQLDelight `BookmarkEntity`).
   - 📥 Downloads screen (`DownloadsScreen.kt`).
   - 📤 Native Share Sheet (`rememberShareLauncher` invoking Android `Intent.ACTION_SEND`).
   - ⚙️ Settings (`SettingsScreen.kt` with Brave background streaming toggle).
   - ✨ AI Settings (`AiSettingsScreen.kt` with backend server URL configuration).
7. **Server**:
   - Native Ktor server in `:server` providing `/api/chat`, `/api/summarize`, `/api/search`, and `/api/health`.
8. **Build & Deployment**:
   - Android Build: `./gradlew :app:androidApp:assembleDebug` (Verified: `BUILD SUCCESSFUL`).
   - Server Build: `./gradlew :server:assemble` (Verified: `BUILD SUCCESSFUL`).
   - APK Location: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Change History
- **2026-10-10**:
  - Initialized `AGENTS.md` central memory.
  - Resolved infinite reload loop by tracking `lastLoadedUrl` in `PikachuWebView`.
  - Removed arrow controlling buttons from bottom navbar; added center "Ask AI" pill.
  - Rebuilt `TabsListScreen` into a modern 2-column card grid with tab title and URL tracking.
  - Fixed tab switching and backstack routing across `LandingRoute` and `BrowserRoute`.
  - Created and committed KMP codebase to dedicated `kmp-browser` git branch.
  - Verified clean builds for both Android and Server targets.
