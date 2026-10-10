---
name: pikachu-browser-dev
description: Master architectural guide, implementation recipes, IPC protocols, and development workflows for the Pikachu AI Agentic Browser.
---

# ⚡ Pikachu Browser Developer Skill & Architectural Reference

This skill equips any AI agent with complete context, code conventions, IPC communication schemas, and module specifications for developing and extending **Pikachu Browser**.

---

## 🏛️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                       PIKACHU FRONTEND (Compose Multiplatform — KMP-CMP)                    │
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
│                     SELF-HOSTED PIKACHU BACKEND SERVICE (FastAPI Python)                    │
│                        [Localhost:9742  |  Private Dedicated Cloud VPS]                     │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│  1. SEARCH ORCHESTRATOR (100% Client-Side for Normal Browsing)                              │
│     • Default Primary: DuckDuckGo HTML Search parsed client-side via Ksoup in KMP           │
│     • Opt-In Fallback: Self-Hosted SearXNG Docker (configured via Browser Settings)         │
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
│       └── pikachu-browser-dev\
│           └── SKILL.md                           <-- You are here
│
├── composeApp\                                    <-- UNIFIED CROSS-PLATFORM FRONTEND (KMP-CMP)
│   ├── build.gradle.kts
│   └── src\
│       ├── commonMain\kotlin\ai\pikachu\browser\  <-- 90%+ SHARED CODE
│       │   ├── App.kt                             <-- Root Composable entrypoint
│       │   ├── ui\                                <-- Shell, Tabs, Chat Drawer, Voice Orb, Theme
│       │   ├── webview\                           <-- expect fun PikachuWebView(...)
│       │   ├── voice\                             <-- expect class AudioStreamer
│       │   ├── network\                           <-- Ktor HTTP & WebSocket Client
│       │   └── privacy\                           <-- EphemeralSessionManager (zero persistence)
│       ├── androidMain\kotlin\ai\pikachu\browser\ <-- ANDROID TUNING (Google Play Store)
│       │   ├── webview\PikachuWebView.android.kt  <-- actual: WebViewCompat + JS Injection
│       │   └── voice\AudioStreamer.android.kt     <-- actual: AudioRecord PCM 16kHz Streamer
│       ├── iosMain\kotlin\ai\pikachu\browser\     <-- IOS TUNING (Apple App Store)
│       │   ├── webview\PikachuWebView.ios.kt      <-- actual: WKWebView + WKUserScript (WebKit)
│       │   └── voice\AudioStreamer.ios.kt         <-- actual: AVAudioEngine Input Tap Streamer
│       └── desktopMain\kotlin\ai\pikachu\browser\ <-- DESKTOP TUNING (macOS / Windows / Linux)
│           ├── webview\PikachuWebView.desktop.kt  <-- actual: KCEF (Chromium Embedded)
│           └── voice\AudioStreamer.desktop.kt     <-- actual: JavaSound PCM Streamer
│
├── src\backend\                                   <-- SELF-HOSTED FASTAPI BACKEND SERVICE
│   ├── app.py                                     <-- FastAPI application factory
│   ├── services\
│   │   ├── agent_service.py                       <-- Autonomous browsing orchestrator
│   │   ├── voice_service.py                       <-- STT/TTS pipeline (Whisper + Edge-TTS)
│   │   └── export_service.py                      <-- PPTX/DOCX/PDF generators
│   └── api\v1\                                    <-- OpenAPI endpoints consumed by Ktor
│
├── searxng\                                       <-- SELF-HOSTED SEARXNG CONFIG
│   ├── settings.yml                               <-- format=json enabled, zero logging
│   └── limiter.toml                               <-- Rate limiting disabled for local cluster
├── docker-compose.yml                             <-- Self-Hosted SearXNG + Redis stack
└── src\scripts\privacy_shield.bundle.js           <-- Compiled TypeScript Ad & Tracker Shield
```

---

## 🔒 Brave-Grade Privacy & Security Rules

1. **Off-The-Record WebEngine & Session Shredding**:
   - `QWebEngineProfile` must never have a persistent storage name.
   - Always enforce `NoPersistentCookies` and `HttpCacheType.NoCache`.
   - On browser close, explicitly trigger **Absolute Data Shredding** (Auto-Logout):
     ```python
     profile.cookieStore().deleteAllCookies()
     profile.clearHttpCache()
     profile.clearAllVisitedLinks()
     ```
   - *Android*: `CookieManager.getInstance().removeAllCookies(null)` and `webView.clearCache(true)` on exit.
   - *iOS 20*: `WKWebsiteDataStore.nonPersistentDataStore()` used strictly.
2. **Hardware Permissions Sandboxing**:
   - Hook `QWebEnginePage.permissionRequested(QWebEnginePermission)` (Qt 6.8+ API).
   - Backward-compat shim: If PySide6 < 6.8, fall back to deprecated `featurePermissionRequested`.
   - Call `permission.grant()` / `permission.deny()` on the `QWebEnginePermission` object.
   - Never auto-grant access. Present the Brave-grade modal prompt (`src/ui/permission_dialog.py`).
   - Store permission decisions strictly in an in-memory dictionary keyed by `(origin, perm_type)` for the **active session only**. Wipe upon app exit.
3. **URL Parameter Stripping**:
   - Strip tracking query parameters before navigating:
     `utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `fbclid`, `gclid`, `mc_eid`, `msclkid`, `yclid`.
4. **WebRTC Leak Shield**:
   - Use `QWebEngineSettings.WebAttribute.WebRTCPublicInterfacesOnly = True` to restrict WebRTC to public IPs only.
   - Note: Qt does NOT expose Chromium's `--force-webrtc-ip-handling-policy` flag directly. The `WebRTCPublicInterfacesOnly` attribute is the correct and reliable API.

---

## 🤖 Decoupled Agentic Service & Auto-Browsing

### CDP Connection Pattern
PySide6 exposes the Chrome DevTools Protocol when initialized with:
```python
import sys
import os

# Must be set BEFORE QApplication is created
os.environ["QTWEBENGINE_REMOTE_DEBUGGING"] = "9222"

# CRITICAL: Modern Chromium rejects WebSocket connections without origin allowlist
sys.argv += ["--webEngineArgs", "--remote-allow-origins=http://127.0.0.1:9222"]
```

### Agent DOM Interaction — Primary Path (Native Qt JS Injection)
> **WARNING**: Playwright `connect_over_cdp` is NOT officially supported for QtWebEngine
> and is unreliable in production. QtWebEngine exposes only a partial CDP subset,
> causing protocol mismatches and context discovery failures.

Use native `QWebEnginePage.runJavaScript()` as the primary agent interaction layer:
```python
def agent_click(page, selector: str, callback=None):
    """Click an element via native Qt JS injection"""
    js = f"""
    (function() {{
        const el = document.querySelector('{selector}');
        if (el) {{
            el.scrollIntoView({{behavior: 'smooth', block: 'center'}});
            el.click();
            return true;
        }}
        return false;
    }})();
    """
    page.runJavaScript(js, 0, callback or (lambda r: None))

def agent_screenshot(web_view):
    """Capture viewport via Qt's native grab() — no CDP needed"""
    from PySide6.QtCore import QBuffer, QIODevice
    pixmap = web_view.grab()
    buffer = QBuffer()
    buffer.open(QIODevice.WriteOnly)
    pixmap.save(buffer, "PNG")
    return buffer.data().toBase64().data()
```

### Agent DOM Interaction — Fallback Path (Lightweight CDP via aiohttp)
For advanced operations (network interception, performance tracing), use raw CDP WebSocket:
```python
import aiohttp
import json

async def cdp_send(ws_url: str, method: str, params: dict = None):
    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(ws_url) as ws:
            msg = {"id": 1, "method": method, "params": params or {}}
            await ws.send_json(msg)
            response = await ws.receive_json()
            return response.get("result")
```

---

## 🎙️ Siri-Like Voice Pipeline

1. **Speech-to-Text (STT)**:
   - Default: `groq.audio.transcriptions.create(model="whisper-large-v3-turbo", file=audio_bytes)` (~150ms).
   - Also available: `whisper-large-v3` for maximum accuracy.
   - Local fallback: `faster_whisper.WhisperModel("large-v3-turbo", device="cpu", compute_type="int8")` with `vad_filter=True`.
   - **Note**: Do NOT use `"base.en"` — too small for production. `large-v3-turbo` is the recommended 2026 model.
2. **Text-to-Speech (TTS)**:
   - `edge-tts` v7.x — real-time streaming (no temp file needed):
     ```python
     import edge_tts
     
     async def stream_speech(text: str, voice: str = "en-US-AriaNeural"):
         communicate = edge_tts.Communicate(text, voice)
         async for chunk in communicate.stream():
             if chunk["type"] == "audio":
                 # Feed chunk["data"] directly to QAudioSink or PyAudio
                 yield chunk["data"]
     ```
   - **Note**: Custom SSML injection is removed in edge-tts 7.x. Use library args for rate/volume/pitch.
3. **Voice Activity Detection (VAD)**:
   - **Primary**: Silero VAD (`silero-vad-lite`) — deep-learning ONNX model, vastly more accurate than legacy WebRTC VAD:
     ```python
     from silero_vad_lite import SileroVAD
     vad = SileroVAD(sample_rate=16000)
     is_speech = vad(audio_chunk) > 0.5
     ```
   - **Secondary**: RMS energy as fast-path silence gate.
   - When the user starts speaking, immediately emit `stop_speaking` signal to cut TTS playback instantly.

---

## 📋 Ephemeral Form Autofill (Zero Persistence)

1. **Detection**: Script scans `<form>`, `<input>`, `<select>`, `<textarea>`, extracting field semantic types (`name`, `email`, `address`, `phone`, etc.).
2. **Prompting**: The Pikachu Chat Drawer renders a confirmation card asking the user to confirm/fill missing fields.
3. **DOM Injection**:
   ```javascript
   function setNativeValue(element, value) {
     element.value = value;
     element.dispatchEvent(new Event('input', { bubbles: true }));
     element.dispatchEvent(new Event('change', { bubbles: true }));
   }
   ```
4. **Zero-Storage Cleanup**: Values exist solely in temporary function arguments in RAM and are overwritten with zeroes upon submission.

---

## 📊 Research & Infographic Exporter (PPTX, DOCX, PDF)

1. **Visual Harvester**:
   - Finds all `<img>` where `naturalWidth >= 250 && naturalHeight >= 150`.
   - Converts `<svg>` diagrams and `<canvas>` to base64 PNG data.
   - Fetches images in-memory via PySide6 network session headers.
2. **PPTX Generation (`python-pptx`)**:
   - 16:9 widescreen layout (`13.333 x 7.5` inches).
   - Bespoke dark editorial styling, high-contrast cards, and proportional image scaling.
3. **DOCX Generation (`python-docx`)**:
   - Executive report template: Title page, Executive Summary, numbered sections, embedded figures with captions, and hyperlinked citations.
4. **PDF Generation**:
   - Uses `QWebEnginePage.printToPdf(path, QPageLayout)` for pixel-perfect document export.

---

## ⚙️ Settings & Configuration Specifications

### Default vs BYOK:
- Default: Managed Pikachu AI Engine (Groq / OpenRouter cloud proxy).
- BYOK Toggle: User can input their own keys for Groq, Mistral, OpenRouter, OpenAI, Anthropic, Gemini, or point to local Ollama (`http://localhost:11434/v1`).

### Address Bar Ribbon:
- SVG Settings Icon placed in toolbar.
- Tooltip: `Settings & Shields (Ctrl+,)`.
- Triggers modern tabbed settings dialog or new tab `pikachu://settings`.

---

## 📦 Required Dependencies (`requirements.txt`)

```
# Core GUI & Engine
PySide6>=6.8.0                  # 6.8+ required for QWebEnginePermission API

# Vector DB & Embeddings
chromadb>=0.5.0
sentence-transformers>=3.0.0

# Search & Extraction
trafilatura>=1.8.0
beautifulsoup4>=4.12.0
httpx>=0.27.0

# LLM Providers
groq>=0.9.0
openai>=1.30.0
python-dotenv>=1.0.0
pydantic>=2.0.0

# Autonomous Agent & Sidecar
aiohttp>=3.9.0                  # Lightweight CDP client (NOT playwright)
fastapi>=0.111.0
uvicorn>=0.30.0
websockets>=12.0

# Voice Engine
edge-tts>=7.0.0                 # v7.x required (v6.x is outdated)
sounddevice>=0.4.6
silero-vad-lite>=1.0.0          # Replaces webrtcvad-wheels (DL-based, more accurate)
numpy>=1.26.0

# Multi-Format Research Exporters
python-pptx>=1.0.0              # v1.x (was 0.6.x)
python-docx>=1.1.0
Pillow>=10.3.0
svglib>=0.19.0                  # Replaces cairosvg (pure Python, no C deps on Windows)
reportlab>=4.1.0                # Required by svglib for rendering

# UI Rendering & Formatting (existing)
markdown>=3.6
pygments>=2.17.0

# Compilation (dev-only)
# nuitka>=2.2.0
# zstandard>=0.22.0
```

---

## 🔍 Client-Side Search Architecture (Zero Server Cost)

Search operations must run entirely client-side to ensure maximum privacy and zero backend server costs for normal browsing:
1. **Default Primary**: DuckDuckGo HTML Search (`html.duckduckgo.com`). Fetched and parsed client-side via Kotlin Ksoup in the shared KMP layer.
2. **Opt-In Fallback**: Self-Hosted SearXNG Docker (`http://localhost:8888/search?format=json`). Configured by the user in Browser Settings.

---

## 🚀 App Store & Google Play Store Compliance Standards

### Google Play Store
- **Webview Policy**: App must provide substantive browser features (multi-tab, custom omnibox, ad-neutralizing shield, AI assistant).
- **Data Safety**: Declare "No Data Collected". Zero user telemetry, zero analytics SDKs.
- **Microphone**: Just-in-time runtime permission dialog explaining ephemeral audio processing.
- **Targets**: Target SDK 35 (Android 15), 64-bit architectures (`arm64-v8a`).

### Apple App Store
- **Guideline 2.5.6 (Webkit Mandate)**: iOS builds must strictly use Apple's native `WKWebView` framework with `WKUserScript`.
- **Guideline 1.2 (AI Content Guardrails)**: Mandatory report/flag button on AI chat bubbles + accuracy disclaimer.
- **Guideline 5.1.1 (Privacy)**: "Data Not Collected" nutrition label; use `WKWebsiteDataStore.nonPersistentDataStore()`.

