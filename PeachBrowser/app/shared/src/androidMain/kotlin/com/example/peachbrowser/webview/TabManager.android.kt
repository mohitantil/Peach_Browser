package com.example.peachbrowser.webview

import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import java.util.concurrent.ConcurrentHashMap

object AppBackgroundTracker {
    @Volatile
    var isBackgrounded: Boolean = false
        private set

    fun updateBackgroundState(flag: Boolean) {
        isBackgrounded = flag
        AndroidTabWebViewPool.setAppBackgrounded(flag)
    }
}

object AndroidTabWebViewPool {
    private val pool = ConcurrentHashMap<String, PeachNativeWebView>()

    fun getOrCreate(
        tabId: String,
        context: Context,
        factory: (Context) -> PeachNativeWebView
    ): PeachNativeWebView {
        val existing = pool[tabId]
        if (existing != null) {
            (existing.context as? MutableContextWrapper)?.baseContext = context
            (existing.parent as? ViewGroup)?.removeView(existing)
            existing.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return existing
        }
        val wrapper = MutableContextWrapper(context)
        val created = factory(wrapper).apply {
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
            webView.dismissOAuthPopupIfShowing()
            (webView.context as? MutableContextWrapper)?.baseContext = webView.context.applicationContext
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

    fun setAppBackgrounded(flag: Boolean) {
        pool.values.forEach { webView ->
            try {
                webView.post {
                    webView.evaluateJavascript(
                        "window.__PEACH_APP_BACKGROUNDED__ = $flag;",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dispatchMediaControl(action: String) {
        pool.values.forEach { webView ->
            try {
                webView.post {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            if (typeof window.__PEACH_MEDIA_CONTROL__ === 'function') {
                                window.__PEACH_MEDIA_CONTROL__('$action');
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pauseAllMedia() {
        dispatchMediaControl("pause")
    }

    fun resumeActiveMedia() {
        dispatchMediaControl("play")
    }

    fun skipToNextMedia() {
        dispatchMediaControl("next")
    }

    fun skipToPreviousMedia() {
        dispatchMediaControl("previous")
    }

    fun duckAllMedia() {
        pool.values.forEach { webView ->
            try {
                webView.post {
                    webView.evaluateJavascript(
                        "if (typeof window.__PEACH_DUCK_MEDIA__ === 'function') window.__PEACH_DUCK_MEDIA__(true);",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unduckAllMedia() {
        pool.values.forEach { webView ->
            try {
                webView.post {
                    webView.evaluateJavascript(
                        "if (typeof window.__PEACH_DUCK_MEDIA__ === 'function') window.__PEACH_DUCK_MEDIA__(false);",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
