# PRD: Peach Browser Architectural Remediation & Feature Upgrade Plan

## Problem Statement
Peach Browser suffers from several regressions and architectural bottlenecks that degrade core browsing and streaming functionality:
1. **SSO / OAuth Failure**: Sign-in with Google, Apple, Microsoft, GitHub, or Clerk fails even when Privacy Shields are disabled due to query parameter stripping in `UrlSanitizer.kt`, destructive child window destruction in `WebChromeClient.onCreateWindow`, and cross-origin auth frame shield evaluation.
2. **Broken Viewport & Distorted Layouts**: Web pages distort when keyboard opens or under certain viewport ratios due to missing `adjustResize` window soft input mode, rigid layout measurement in `PeachNativeWebView.onMeasure`, and broad CSS selectors in `privacy_shield.bundle.js`.
3. **SPA & AI Chat Freezes (`chatgpt.com`, `claude.ai`)**: Recomposition storms in the Compose `AndroidView.update` block repeatedly invoke heavy JNI settings recalculations and synchronous `copyBackForwardList()` history copies, while anti-bot challenges fail due to detectable `Function.prototype.toString` monkey-patching and WebGL spoofing in `farbling.bundle.js`.
4. **Memory Leaks & CPU Polling Waste**: Pooled WebViews retain raw Activity contexts in a static map, leading to Activity context leaks across config changes; simultaneous 1-second polling loops run in `BrowserViewModel` and `DownloadsScreen` even when zero downloads are active.
5. **Missing Media Controls**: The foreground media notification lacks an Android `MediaSession` and `Notification.MediaStyle`, offering no playback controls (play/pause/next/prev) and being abruptly killed when returning to the app in `MainActivity.onResume()`.

---

## Users
- Mobile users relying on Peach Browser as their daily driver browser for web navigation and modern Single-Page Applications (ChatGPT, Claude, YouTube).
- Users authenticating into online services via standard OAuth 2.0 / OpenID Connect popups and redirect flows.
- Users listening to background streaming audio/video with persistent notification and lock-screen media controls.

---

## Current State & Evidence
Ground-truth codebase evidence gathered by independent readers:
- **Auth & Query Sanitization**: `UrlSanitizer.kt:43-74` blindly strips query parameters matching `TRACKING_PARAMS` without checking if the URL is an OAuth or SSO authentication callback.
- **Child WebView Destruction**: `PeachWebView.android.kt:702, 1007-1041` sets `setSupportMultipleWindows(false)`, captures popups with a throwaway `WebView`, forces `view.loadUrl(targetUrl)`, and destroys the popup (`v?.destroy()`), breaking `window.opener.postMessage()`.
- **Recomposition Storm in `update`**: `PeachWebView.android.kt:569, 1191-1282` collects `browserState` directly; every tick triggers `settings.javaScriptEnabled = !shouldBlockScripts` and synchronous `canGoBackSmartly()` via `copyBackForwardList()`.
- **Context Leaks in WebView Pool**: `TabManager.android.kt:21-43` stores raw Activity context in `AndroidTabWebViewPool.pool` without `MutableContextWrapper`.
- **Static Notification & Abrupt Shutdown**: `PeachMediaService.kt:144-164` builds a static notification without `MediaSession` or playback buttons. `MainActivity.kt:112-125` unconditionally stops the service on `onResume()`.
- **Defaults & Unconditional Polling**: `BrowserMvi.kt:141, 144` sets `isBackgroundPlayEnabled = false` and `isClearSessionOnExitEnabled = true`. `BrowserViewModel.kt:142-150` runs `while (true) delay(1000L)` indefinitely.
- **Anti-Bot Anomaly**: `farbling.bundle.js:62-72, 158-180` attaches synthetic `toString` properties to patched functions and returns static `'Qualcomm'` / `'Adreno (TM) 740'`, failing Cloudflare Turnstile and OpenAI Sentinel PoW checks.

---

## Proposal
Implement an end-to-end remediation plan across 6 independent, acyclic tasks to resolve authentication popups, layout viewport distortion, SPA recomposition bottlenecks, anti-bot script conformance, memory leaks, and persistent MediaSession notification controls.

---

## In Scope vs. Out of Scope

### In Scope
- OAuth/SSO URL whitelist and redirection preservation in `UrlSanitizer.kt` and `PeachWebView.android.kt`.
- Support multiple windows in `WebChromeClient.onCreateWindow` preserving popup lifecycle and `window.opener`.
- Viewport and soft-keyboard edge-to-edge support with `adjustResize` in `AndroidManifest.xml` and flexible measuring.
- Compose `AndroidView.update` debouncing/memoization to prevent UI thread blocking.
- Elimination of detectable `toString` masking in `farbling.bundle.js` and safe cosmetic CSS attachment in `privacy_shield.bundle.js`.
- `MutableContextWrapper` integration in `AndroidTabWebViewPool` to prevent Activity leaks.
- Android `MediaSession` + `Notification.MediaStyle` with Previous, Play/Pause, Next, and Stop controls in `PeachMediaService.kt`.
- Cleanup of duplicate assets and unused template files.

### Out of Scope
- Rewriting the iOS WKWebView backend (remediation focuses on shared core and Android platform layer).
- Changing database schema or SQLDelight migrations.
- Altering the MVI architecture or replacing Koin DI.

---

## Functional & Non-Functional Requirements
- **FR-1**: Users must be able to log into Google, Apple, Microsoft, GitHub, and Cloudflare-protected accounts without OAuth popups failing or redirect tokens being stripped.
- **FR-2**: Soft keyboard appearance must resize the web viewport without clipping docked toolbars or collapsing SPA viewport heights.
- **FR-3**: Web pages streaming tokens (ChatGPT, Claude) must not stutter, hang, or trigger synchronous layout/history operations during recomposition.
- **FR-4**: Background and foreground audio playback must be controllable via an OS-level persistent media notification bar and lock-screen widget.
- **NFR-1**: Zero Activity memory leaks in `AndroidTabWebViewPool`.
- **NFR-2**: Zero idle background polling on CPU when downloads list is empty.

---

## Acceptance Criteria
1. Successful Google and GitHub OAuth sign-in flow completes through `window.open` popup or redirect without breaking parent session.
2. Viewport height adapts smoothly when soft keyboard appears on text inputs.
3. `chatgpt.com` streaming responses complete with no UI thread freezing and no Cloudflare Turnstile blocks.
4. Media notification bar displays track title, artist/domain, and functional Play/Pause/Skip buttons that directly manipulate web audio.
5. All tests and Gradle debug builds pass cleanly (`./gradlew :app:androidApp:assembleDebug`).

---

## Success Measures
- 100% success rate on top SSO providers (Google, Apple, Microsoft, GitHub, Clerk).
- Zero Main Thread frame drops or ANRs during text streaming in SPAs.
- Seamless background and foreground media playback control without notification flicker or accidental teardown.

---

## Risks & Mitigation
- **Risk**: Multiple window support in WebView can cause rogue tab proliferation if not managed.
  - *Mitigation*: Restrict child window creation to authenticated popup lifecycles with dismiss handlers.
- **Risk**: Preserving tracking parameters on auth URLs might allow some analytics tokens through.
  - *Mitigation*: Strictly scope auth parameter exemptions to verified SSO domains (`accounts.google.com`, `appleid.apple.com`, etc.) and auth endpoints (`/oauth`, `/authorize`, `/callback`).

---

## Open Questions
- None. All issues have concrete root-cause reproductions, verified citations, and approved fixes.

---

## Sources
- Source Audit Document: `PeachBrowser/gemini-code-1794201199706.md`
- Codebase Surfaces: `PeachWebView.android.kt`, `UrlSanitizer.kt`, `TabManager.android.kt`, `PeachMediaService.kt`, `MainActivity.kt`, `BrowserMvi.kt`, `BrowserViewModel.kt`, `farbling.bundle.js`, `privacy_shield.bundle.js`.

---

## Implementation roadmap

### T1: Core Sanitization, OAuth/SSO & URL Redirection Bypass
**Status:** Not started
**Depends on:** None
**In scope:**
- Add `isAuthOrOAuthUrl()` detection to `UrlSanitizer.kt` covering major auth hosts (`accounts.google.com`, `appleid.apple.com`, `login.microsoftonline.com`, `github.com`, `auth0.com`, `clerk.com`) and auth paths/query keys (`client_id`, `redirect_uri`, `response_type`, `code_challenge`, `id_token`).
- Bypass parameter stripping and De-AMPing in `UrlSanitizer.kt` for identified auth URLs.
- Update `BrowserMvi.kt` defaults: set `isClearSessionOnExitEnabled = false` and `isBackgroundPlayEnabled = true`. Add Google and Apple auth domains to `DEFAULT_SITE_EXCEPTIONS`.
- Fix escaped string interpolation in `LlmApiService.kt` (`"Bearer $apiKey"`).
- Fix cookie shredding in `PlatformSession.android.kt` to prepend `https://` to hostname.
**Out of scope:**
- Modifying WebView multi-window handling or MediaService.
**Surfaces touched:**
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/util/UrlSanitizer.kt`
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/presentation/mvi/BrowserMvi.kt`
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/data/network/LlmApiService.kt`
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/util/PlatformSession.android.kt`
**Interfaces:**
- `UrlSanitizer.isAuthOrOAuthUrl(url: String): Boolean`
- `UrlSanitizer.stripTrackingParams(url: String): String`
**Verification:**
- Unit validation of auth URL preservation.
- Verify `assembleDebug` passes.
**Next command:**
- `eg-new-feature implement T1 from docs/prds/audit_remediation_and_upgrade.md`

---

### T2: WebView Multi-Window Popup & Viewport/Layout Engine Overhaul
**Status:** Not started
**Depends on:** T1
**In scope:**
- Configure `android:windowSoftInputMode="adjustResize"` in `AndroidManifest.xml` on `MainActivity`.
- Overhaul `WebChromeClient.onCreateWindow` in `PeachWebView.android.kt` with popup dialog / overlay support so `window.opener.postMessage()` remains intact instead of abruptly destroying the child WebView.
- Align viewport settings in `PeachWebView.android.kt` (`useWideViewPort`, `loadWithOverviewMode`, layout algorithms) and relax rigid `MeasureSpec.EXACTLY` in `PeachNativeWebView.onMeasure` to allow smooth keyboard resizing.
- Update `privacy_shield.bundle.js` cosmetic style injection to attach only when `<head>` is available, removing layout-collapsing rules.
**Out of scope:**
- Compose recomposition throttling or MediaSession notifications.
**Surfaces touched:**
- `PeachBrowser/app/androidApp/src/main/AndroidManifest.xml`
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/webview/PeachWebView.android.kt`
- `PeachBrowser/app/androidApp/src/main/assets/privacy_shield.bundle.js`
- `PeachBrowser/app/iosApp/iosApp/privacy_shield.bundle.js`
**Interfaces:**
- `PeachNativeWebView.dismissOAuthPopupIfShowing()`
- `WebChromeClient.onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean`
**Verification:**
- Verify popups load without destroying parent context.
- Verify viewport resizes when keyboard opens without content distortion.
**Next command:**
- `eg-new-feature implement T2 from docs/prds/audit_remediation_and_upgrade.md`

---

### T3: Recomposition Storm Mitigation & Anti-Bot Conformance
**Status:** Not started
**Depends on:** T2
**In scope:**
- Memoize and debounce updates in the Compose `AndroidView.update` block in `PeachWebView.android.kt` (only update `javaScriptEnabled` when setting changes; avoid synchronous `copyBackForwardList()` on every state change).
- Remove forced synchronous DOM innerText reflow during page streaming.
- Overhaul `farbling.bundle.js`: remove anomalous `Function.prototype.toString` property overrides that trigger anti-bot detection; avoid hardcoded WebGL vendor/renderer spoofing that mismatches device GPU.
- Ensure ServiceWorker interception in `AdBlocker.kt` exempts streaming fetch/SSE responses on AI chat domains.
**Out of scope:**
- Memory leaks and background media playback controls.
**Surfaces touched:**
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/webview/PeachWebView.android.kt`
- `PeachBrowser/app/androidApp/src/main/assets/farbling.bundle.js`
- `PeachBrowser/app/iosApp/iosApp/farbling.bundle.js`
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/adblock/AdBlocker.kt`
**Interfaces:**
- Non-monkeypatched `maskNativeFunction` or native prototype preservation.
- Debounced navigation state updates in `PeachWebView.android.kt`.
**Verification:**
- `chatgpt.com` and `claude.ai` chat interactions stream cleanly without UI stutter or bot challenge failures.
**Next command:**
- `eg-new-feature implement T3 from docs/prds/audit_remediation_and_upgrade.md`

---

### T4: Memory Leak Prevention & Polling Resource Optimization
**Status:** Not started
**Depends on:** T1
**In scope:**
- Refactor `AndroidTabWebViewPool` in `TabManager.android.kt` to use `MutableContextWrapper` for all cached WebViews, rebinding `baseContext` on tab retrieval to eliminate `MainActivity` context leaks.
- Deduplicate `setDownloadListener` in `PeachWebView.android.kt` to preserve `enqueuePeachDownload` for `data:` and `blob:` URLs.
- Restrict `@JavascriptInterface` in `PeachNativeBridge` from exposing global site exception lists to untrusted third-party iframe origins.
- Throttle `startRealTimeDownloadsSync()` in `BrowserViewModel.kt` and `DownloadsScreen.kt` so polling only runs when an active (`!isCompleted && !isFailed`) download is progressing.
**Out of scope:**
- MediaSession and lock-screen notification controls.
**Surfaces touched:**
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/webview/TabManager.android.kt`
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/webview/PeachWebView.android.kt`
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/presentation/viewmodel/BrowserViewModel.kt`
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/ui/screens/downloads/DownloadsScreen.kt`
**Interfaces:**
- `AndroidTabWebViewPool.getOrCreate(tabId: String, context: Context, factory: (Context) -> PeachNativeWebView)`
- `BrowserViewModel.startRealTimeDownloadsSync()`
**Verification:**
- Rotate device and ensure `MainActivity` is garbage collected without leak warnings.
- Confirm background CPU usage stays idle when no downloads are active.
**Next command:**
- `eg-new-feature implement T4 from docs/prds/audit_remediation_and_upgrade.md`

---

### T5: Persistent Android MediaSession & Playback Notification Bar
**Status:** Not started
**Depends on:** T1, T4
**In scope:**
- Overhaul `PeachMediaService.kt` with Android `MediaSession` and `Notification.MediaStyle` (Previous, Play/Pause, Next, Stop).
- Add media dispatch actions (`dispatchMediaControl`, `skipToNextMedia`, `skipToPreviousMedia`) to `AndroidTabWebViewPool`.
- Update `MainActivity.kt` to observe `browserViewModel.state.mediaMetadata` and keep notification updated with real-time track and playback state.
- Prevent `MainActivity.onResume()` from unconditionally destroying `PeachMediaService` while audio is active.
- Support runtime `POST_NOTIFICATIONS` permission prompt on Android 13+.
- Update `media_detector.bundle.js` with `window.__PEACH_MEDIA_CONTROL__` handler to support remote play/pause/skip triggers from notification bar.
**Out of scope:**
- Standalone iOS Control Center lock-screen integration.
**Surfaces touched:**
- `PeachBrowser/app/androidApp/src/main/kotlin/com/example/peachbrowser/service/PeachMediaService.kt`
- `PeachBrowser/app/androidApp/src/main/kotlin/com/example/peachbrowser/MainActivity.kt`
- `PeachBrowser/app/shared/src/androidMain/kotlin/com/example/peachbrowser/webview/TabManager.android.kt`
- `PeachBrowser/app/androidApp/src/main/assets/media_detector.bundle.js`
- `PeachBrowser/app/iosApp/iosApp/media_detector.bundle.js`
**Interfaces:**
- `MediaSession.Callback` implementation in `PeachMediaService`.
- `AndroidTabWebViewPool.dispatchMediaControl(action: String)`.
**Verification:**
- Play audio in YouTube or YouTube Music.
- Verify persistent notification bar appears with functional Play/Pause/Skip buttons and lock screen controls.
**Next command:**
- `eg-new-feature implement T5 from docs/prds/audit_remediation_and_upgrade.md`

---

### T6: Repository Cleanup & Dead Code Removal
**Status:** Not started
**Depends on:** T1, T2, T3, T4, T5
**In scope:**
- Delete duplicate generated drawables in `app/androidApp/src/main/assets/composeResources/` (already canonically provided by `app/shared/src/commonMain/composeResources/`).
- Remove unused template source files: `app/shared/src/commonMain/kotlin/com/example/peachbrowser/Greeting.kt` and `core/src/commonMain/kotlin/com/example/peachbrowser/GreetingUtil.kt`.
- Clean up ProGuard rules in `app/androidApp/proguard-rules.pro` to preserve JavascriptInterfaces and reflection-safe methods.
- Complete full end-to-end Gradle build and test verification.
**Out of scope:**
- Modifying project core libraries or build scripts.
**Surfaces touched:**
- `PeachBrowser/app/androidApp/src/main/assets/composeResources/`
- `PeachBrowser/app/shared/src/commonMain/kotlin/com/example/peachbrowser/Greeting.kt`
- `PeachBrowser/core/src/commonMain/kotlin/com/example/peachbrowser/GreetingUtil.kt`
- `PeachBrowser/app/androidApp/proguard-rules.pro`
**Interfaces:**
- None (cleanup only).
**Verification:**
- Clean repository tree, `./gradlew :app:androidApp:assembleDebug` succeeds with 0 errors.
**Next command:**
- `eg-new-feature implement T6 from docs/prds/audit_remediation_and_upgrade.md`

