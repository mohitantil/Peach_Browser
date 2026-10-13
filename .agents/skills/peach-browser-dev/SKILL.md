---
name: peach-browser-dev
description: Master architectural guide, implementation recipes, IPC protocols, and development workflows for the Peach AI Agentic Browser.
---

# 🍑 Peach Browser Developer Skill & Architectural Reference

This skill equips any AI agent with complete context, code conventions, IPC communication schemas, and module specifications for developing and extending **Peach Browser**.

---

## 🏛️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                        PEACH FRONTEND (Compose Multiplatform — KMP-CMP)                     │
│                 Targeting Android 11+ (Play Store), iOS 20+ (App Store), Desktop            │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│  • 90%+ Shared Kotlin Code (commonMain): ViewModels, Omnibox, Tabs, Chat Drawer, Theme      │
│  • Expect/Actual Platform Tuning:                                                           │
│    - Android 11+: Android WebViewCompat + addDocumentStartJavaScript() + AudioRecord        │
│    - iOS 20+: Apple WKWebView (WebKit Guideline 2.5.6) + WKContentRuleList + AVAudioEngine  │
│    - Desktop: KCEF (Chromium Embedded Framework) + JavaSound                                │
└───────────────────────────────────────────────┬─────────────────────────────────────────────┘
                                                │
                                                ▼ (Ktor REST / WebSocket / SSE)
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                      SELF-HOSTED PEACH BACKEND SERVICE (FastAPI Python)                     │
│                        [Localhost:9742  |  Private Dedicated Cloud VPS]                     │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│  1. SEARCH ORCHESTRATOR (100% Client-Side for Normal Browsing)                              │
│     • Default Primary: DuckDuckGo HTML Search parsed client-side via Ksoup in KMP           │
│     • Configurable Engines: DuckDuckGo, Brave Search, Google, Bing, SearXNG                 │
│                                                                                             │
│  2. AUTONOMOUS AGENT SERVICE                                                                │
│     • ReAct Observe-Think-Act Loop, DOM Inspector, Ephemeral Form Autofill                  │
│                                                                                             │
│  3. DUPLEX REAL-TIME VOICE PIPELINE                                                         │
│     • Groq Whisper Large v3 / Faster-Whisper STT + Edge-TTS Neural Voices                   │
│                                                                                             │
│  4. MULTI-FORMAT DOCUMENT EXPORTER                                                          │
│     • PPTX Widescreen Slides, DOCX Executive Whitepapers, Vector PDF with web figures       │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 Directory Structure & File Responsibilities

```
d:\softwares\SearXNG\
├── .agents\
│   └── skills\
│       ├── peach-browser-dev\
│       │   └── SKILL.md                           <-- You are here
│       └── peach-kmp-architecture\
│           └── SKILL.md
│
├── PeachBrowser\                                  <-- UNIFIED CROSS-PLATFORM FRONTEND (KMP-CMP)
│   ├── build.gradle.kts
│   ├── app\
│   │   ├── shared\src\commonMain\kotlin\com\example\peachbrowser\
│   │   │   ├── App.kt                             <-- Root Composable entrypoint & Docked Toolbars
│   │   │   ├── domain\                            <-- Models, UseCases, Repositories
│   │   │   ├── presentation\                      <-- MVI BrowserViewModel, ChatViewModel
│   │   │   ├── ui\                                <-- LandingPage, Omnibox, TabsList, Settings, Sheets
│   │   │   ├── webview\                           <-- expect fun PeachWebView, Controller
│   │   │   └── util\                              <-- UrlSanitizer, MediaDetector, SearchEngine
│   │   ├── androidApp\src\main\
│   │   │   ├── kotlin\com\example\peachbrowser\
│   │   │   │   ├── MainActivity.kt                <-- Android Entrypoint & Edge-to-Edge
│   │   │   │   ├── PeachApplication.kt             <-- Koin initialization
│   │   │   │   └── service\PeachMediaService.kt   <-- Foreground Service for Background Playback
│   │   │   └── assets\                            <-- background_playback, farbling, privacy_shield
│   │   └── iosApp\                                <-- Apple WKWebView & Xcode Target
│   └── server\                                    <-- Native Ktor backend server
│
├── Peach-Python\                                  <-- DESKTOP PYTHON BROWSER REFERENCE
│   ├── src\
│   └── run_browser.py
└── searxng\                                       <-- SELF-HOSTED SEARXNG CONFIG
```

---

## 🔒 Brave-Grade Privacy & Security Rules

1. **Off-The-Record WebEngine & Session Shredding**:
   - On browser close or exit, explicitly trigger **Absolute Data Shredding** (Auto-Logout).
   - *Android*: `CookieManager.getInstance().removeAllCookies(null)`, `WebStorage.getInstance().deleteAllData()`, and `webView.clearCache(true)`.
   - *iOS 20*: `WKWebsiteDataStore.nonPersistentDataStore()` used strictly.
2. **Ephemeral Sensor Permissions**:
   - Session-based RAM storage (`SessionPermissionManager.kt`), never persisted to database or disk.
   - Handled via `WebChromeClient.onPermissionRequest` on Android and WKWebView permission handlers on iOS.
   - Auto-purged on navigation (`onPageStarted`), tab closure (`CloseTab`), or app exit.
3. **Tracking Parameter Stripping & De-AMPing**:
   - Strips `fbclid`, `gclid`, `utm_*`, `si`, `igshid`, `msclkid`, etc., via pure KMP `UrlSanitizer.kt`.
   - Automatically De-AMPs Google cache URLs to canonical publisher links.
4. **Anti-Fingerprinting Farbling**:
   - Deterministic pseudo-random micro-noise injected into Canvas, AudioContext, and WebGL parameters via `farbling.bundle.js`.
   - Emits `Sec-GPC: 1` and `DNT: 1` signals.
5. **Background Media Streaming Engine**:
   - Native `PeachMediaService` (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`) with wake lock.
   - `PeachNativeWebView` overrides window visibility callbacks to keep Chromium media decoders active.
   - `background_playback.bundle.js` bypasses Page Visibility API.
