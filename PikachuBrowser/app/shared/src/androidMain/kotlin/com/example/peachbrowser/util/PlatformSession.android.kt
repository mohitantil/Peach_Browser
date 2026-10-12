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

actual fun exitBrowserApp() {
    try {
        clearBrowserSession()
        android.os.Process.killProcess(android.os.Process.myPid())
    } catch (e: Exception) {
        kotlin.system.exitProcess(0)
    }
}
