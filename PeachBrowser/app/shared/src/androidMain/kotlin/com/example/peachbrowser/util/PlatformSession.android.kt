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
        com.example.peachbrowser.webview.AndroidTabWebViewPool.pauseAllMedia()
        AndroidContextHolder.context?.let { ctx ->
            val intent = android.content.Intent().apply {
                component = android.content.ComponentName(
                    ctx,
                    "com.example.peachbrowser.service.PeachMediaService"
                )
            }
            ctx.stopService(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun exitBrowserApp() {
    try {
        stopPlatformMediaService()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            try {
                cookieManager.flush()
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    } catch (e: Exception) {
        kotlin.system.exitProcess(0)
    }
}

actual fun shredSiteData(host: String) {
    try {
        val cleanHost = host.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore(":")
            .trim()
        if (cleanHost.isBlank()) return

        val webStorage = WebStorage.getInstance()
        webStorage.deleteOrigin("https://$cleanHost")
        webStorage.deleteOrigin("http://$cleanHost")

        val cookieManager = CookieManager.getInstance()
        val schemes = listOf("https://$cleanHost", "http://$cleanHost")
        for (url in schemes) {
            val cookies = cookieManager.getCookie(url)
            if (!cookies.isNullOrBlank()) {
                val cookieList = cookies.split(";")
                for (cookie in cookieList) {
                    val cookieName = cookie.substringBefore("=").trim()
                    if (cookieName.isNotEmpty()) {
                        val isHostPrefixed = cookieName.startsWith("__Host-", ignoreCase = true)
                        val isSecurePrefixed = cookieName.startsWith("__Secure-", ignoreCase = true)
                        val secureFlag = if (isHostPrefixed || isSecurePrefixed || url.startsWith("https://")) "; Secure" else ""

                        if (isHostPrefixed) {
                            cookieManager.setCookie(url, "$cookieName=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureFlag")
                        } else {
                            cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=$cleanHost; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureFlag")
                            cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=.$cleanHost; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureFlag")
                            cookieManager.setCookie(url, "$cookieName=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT$secureFlag")
                        }
                    }
                }
            }
        }
        cookieManager.flush()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
