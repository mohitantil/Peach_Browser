package com.example.peachbrowser.util

actual fun clearBrowserSession() {
    // Desktop stub
}

actual fun stopPlatformMediaService() {
}

actual fun exitBrowserApp() {
    kotlin.system.exitProcess(0)
}

actual fun shredSiteData(host: String) {
}

