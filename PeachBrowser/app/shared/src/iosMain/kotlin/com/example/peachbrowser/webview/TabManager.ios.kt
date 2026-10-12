package com.example.peachbrowser.webview

import platform.WebKit.WKWebView

object IosTabWebViewPool {
    private val pool = mutableMapOf<String, WKWebView>()

    fun getOrCreate(tabId: String, factory: () -> WKWebView): WKWebView {
        val existing = pool[tabId]
        if (existing != null) {
            existing.removeFromSuperview()
            return existing
        }
        val created = factory()
        pool[tabId] = created
        return created
    }

    fun get(tabId: String): WKWebView? = pool[tabId]

    fun remove(tabId: String) {
        val wk = pool.remove(tabId) ?: return
        try {
            wk.stopLoading()
            wk.removeFromSuperview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAll() {
        val keys = pool.keys.toList()
        for (key in keys) {
            remove(key)
        }
    }
}

actual fun releaseTabWebView(tabId: String) {
    IosTabWebViewPool.remove(tabId)
}

actual fun clearAllTabWebViews() {
    IosTabWebViewPool.clearAll()
}
