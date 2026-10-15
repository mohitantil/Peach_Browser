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
            existing.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return existing
        }
        val created = factory(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
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

    fun pauseAllMedia() {
        pool.values.forEach { webView ->
            webView.post {
                webView.evaluateJavascript(
                    """
                    (function() {
                        window.__PEACH_EXTERNAL_PAUSED__ = true;
                        if (typeof window.__PEACH_PAUSE_ALL_MEDIA__ === 'function') {
                            window.__PEACH_PAUSE_ALL_MEDIA__();
                        } else {
                            document.querySelectorAll('audio, video').forEach(function(el) {
                                try {
                                    if (!el.paused) el.__peach_paused_by_focus = true;
                                    el.__peach_sleep_timer_paused = true;
                                    el.__peach_user_paused = true;
                                    HTMLMediaElement.prototype.pause.call(el);
                                } catch(e) {}
                            });
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
    }

    fun resumeActiveMedia() {
        pool.values.forEach { webView ->
            webView.post {
                webView.evaluateJavascript(
                    """
                    (function() {
                        window.__PEACH_EXTERNAL_PAUSED__ = false;
                        if (typeof window.__PEACH_RESUME_ALL_MEDIA__ === 'function') {
                            window.__PEACH_RESUME_ALL_MEDIA__();
                        } else {
                            document.querySelectorAll('audio, video').forEach(function(el) {
                                try {
                                    if (el.__peach_paused_by_focus) {
                                        el.__peach_paused_by_focus = false;
                                        el.__peach_sleep_timer_paused = false;
                                        el.__peach_user_paused = false;
                                        el.__peach_was_playing = true;
                                        el.play();
                                    }
                                } catch(e) {}
                            });
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }
    }

    fun unduckAllMedia() {
        pool.values.forEach { webView ->
            webView.post {
                webView.evaluateJavascript(
                    "if (typeof window.__PEACH_DUCK_MEDIA__ === 'function') window.__PEACH_DUCK_MEDIA__(false);",
                    null
                )
            }
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
