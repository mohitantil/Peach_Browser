package com.example.peachbrowser.webview

import android.content.Context
import android.view.ViewGroup
import java.util.concurrent.ConcurrentHashMap

object AndroidTabWebViewPool {
    private val pool = ConcurrentHashMap<String, PeachNativeWebView>()

    fun getOrCreate(
        tabId: String,
        context: Context,
        factory: (Context) -> PeachNativeWebView
    ): PeachNativeWebView {
        val existing = pool[tabId]
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val created = factory(context)
        pool[tabId] = created
        return created
    }

    fun get(tabId: String): PeachNativeWebView? = pool[tabId]

    fun remove(tabId: String) {
        val webView = pool.remove(tabId) ?: return
        try {
            webView.hideCustomViewIfShowing()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAll() {
        val keys = pool.keys().toList()
        for (key in keys) {
            remove(key)
        }
    }
}

actual fun releaseTabWebView(tabId: String) {
    AndroidTabWebViewPool.remove(tabId)
    com.example.peachbrowser.util.SessionPermissionManager.clearTabPermissions(tabId)
}

actual fun clearAllTabWebViews() {
    AndroidTabWebViewPool.clearAll()
    com.example.peachbrowser.util.SessionPermissionManager.clearAllPermissions()
}
