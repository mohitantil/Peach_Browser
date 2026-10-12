package com.example.peachbrowser.util

import android.webkit.CookieManager
import android.webkit.WebStorage

actual fun clearBrowserSession() {
    try {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.removeSessionCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun stopPlatformMediaService() {
    try {
        AndroidContextHolder.context?.let { ctx ->
            val intent = android.content.Intent().apply {
                component = android.content.ComponentName(ctx, "com.example.peachbrowser.service.PeachMediaService")
                action = "com.example.peachbrowser.action.STOP_BG_PLAY"
            }
            ctx.startService(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun exitBrowserApp() {
    try {
        clearBrowserSession()
        stopPlatformMediaService()
        android.os.Process.killProcess(android.os.Process.myPid())
    } catch (e: Exception) {
        kotlin.system.exitProcess(0)
    }
}
