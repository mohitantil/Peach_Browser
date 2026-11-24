# 🛡️ Privacy Policy — Peach Browser

**Effective Date:** November 24, 2026  
**Application:** Peach Browser for Android  
**Developer:** Peach Browser Open Source Community  

Peach Browser was built from the ground up on the principle of **absolute data sovereignty and user privacy**. We believe that your browsing activity belongs exclusively to you. 

---

## 1. Zero Telemetry & Zero Data Collection
- **No Analytics SDKs**: Peach Browser does not bundle Google Analytics, Firebase Analytics, Meta Pixel, Adjust, AppsFlyer, or any third-party tracking libraries.
- **No Account Requirement**: You can browse immediately without creating an account or providing an email address.
- **No Search Logging**: Your search queries are sent directly to the search provider of your choice (DuckDuckGo, Brave Search, Startpage, Google, or Bing) using direct client-side requests; no intermediate proxy records your queries.

---

## 2. On-Demand Hardware Sensor Permissions (Camera & Microphone)
- **On-Demand Only**: Access to your device's Camera (`android.permission.CAMERA`) and Microphone (`android.permission.RECORD_AUDIO`) is requested strictly on-demand when you explicitly initiate a WebRTC session (such as a video call or voice input on a website).
- **Ephemeral Session-Only Storage**: Sensor approvals are cached strictly in volatile memory (`SessionPermissionManager`) per specific tab and web origin.
- **Instant Auto-Revocation**: When you navigate away from a web page, close a tab, or exit the browser, all hardware sensor permissions are immediately purged from memory. Zero hardware permission records are ever saved to disk or persistent storage.

---

## 3. Ephemeral Browsing & Session Data Shredding
- **Automatic Shredding on Exit**: When session shredding is enabled (active by default), closing Peach Browser or swiping it from Android Recents triggers a full wipe of:
  - All web cookies (`CookieManager.removeAllCookies`)
  - All DOM Web Storage (`WebStorage.deleteAllData`)
  - Local HTTP cache and form autofill data (`webView.clearCache(true)`)
- **No Cross-Session Tracking**: The next time you open Peach Browser, you start completely fresh with zero residual cookies or tracking tokens from previous sessions.

---

## 4. Hardened Privacy Shield & Ad-Blocking
- **Network & Cosmetic Filtering**: Built-in Privacy Shields block intrusive advertisements, tracking scripts, and malicious domains directly on your device.
- **Query Parameter Sanitization**: Tracking tokens (`utm_*`, `fbclid`, `gclid`, `si`, `igshid`, `msclkid`) are stripped from web addresses before navigation occurs.
- **Anti-Fingerprinting Farbler**: Injects subtle pseudo-random micro-noise into Canvas and AudioContext readings to prevent third-party scripts from building persistent device fingerprints across the web.

---

## 5. Background Media Playback & Foreground Service
- **Audio Playback**: When playing audio or video in the background or with the screen turned off, Peach Browser runs a low-priority Android Foreground Service (`android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
- **Media Controls**: This service exists solely to maintain audio continuity and provide standard lock-screen and notification controls (Play, Pause, Skip). It transmits zero data and terminates immediately when playback stops or the sleep timer expires.

---

## 6. Permissions Summary Table

| Permission | Usage | Persistence |
|---|---|---|
| `INTERNET` | Loading web pages requested by the user. | N/A |
| `CAMERA` | WebRTC video calls on approved websites only. | Ephemeral (Wiped on tab close/exit) |
| `RECORD_AUDIO` | WebRTC voice calls on approved websites only. | Ephemeral (Wiped on tab close/exit) |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continuous background music & video streaming. | Terminated on playback stop |
| `WAKE_LOCK` | Preventing CPU freeze during background audio playback. | Released on playback stop |
| `POST_NOTIFICATIONS` | Media playback controls and download notifications. | OS standard |

---

## 7. Contact & Open Source Inquiries
If you have questions regarding this Privacy Policy or would like to inspect the open-source code:
- **Repository**: [Peach Browser on GitHub](https://github.com/example/peachbrowser)
- **Email**: privacy@peachbrowser.app
