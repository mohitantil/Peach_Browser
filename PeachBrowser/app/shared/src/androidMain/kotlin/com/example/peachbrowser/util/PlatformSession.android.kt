package com.example.peachbrowser.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

actual fun clearBrowserSession() {
    try {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.removeSessionCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()

        AndroidContextHolder.context?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                try {
                    val dummyWv = WebView(ctx)
                    dummyWv.clearCache(true)
                    dummyWv.clearFormData()
                    dummyWv.destroy()
                } catch (_: Exception) {}
            }
            try {
                val webViewCacheDir = File(ctx.cacheDir, "org.chromium.android_webview")
                if (webViewCacheDir.exists()) webViewCacheDir.deleteRecursively()
                val defaultWvCache = File(ctx.cacheDir, "WebView")
                if (defaultWvCache.exists()) defaultWvCache.deleteRecursively()
            } catch (_: Exception) {}
        }
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
        clearBrowserSession()
        val activity = AndroidContextHolder.context?.findActivity()
        if (activity != null && !activity.isFinishing) {
            activity.finishAffinity()
        } else {
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
        val rootHost = cleanHost.removePrefix("www.")
        val origins = listOf(
            "https://$rootHost",
            "http://$rootHost",
            "https://www.$rootHost",
            "http://www.$rootHost"
        )
        for (origin in origins) {
            webStorage.deleteOrigin(origin)
        }

        val cookieManager = CookieManager.getInstance()
        for (url in origins) {
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
