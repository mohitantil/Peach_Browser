package com.example.pikachubrowser.util

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
