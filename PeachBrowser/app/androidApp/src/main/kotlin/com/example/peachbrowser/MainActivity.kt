package com.example.peachbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.service.PeachMediaService
import com.example.peachbrowser.util.MediaDetector
import com.example.peachbrowser.util.PeachAudioFocusManager
import com.example.peachbrowser.webview.AndroidTabWebViewPool
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by inject()
    private val audioFocusManager: PeachAudioFocusManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null &&
            (browserViewModel.state.value.isClearSessionOnExitEnabled || browserViewModel.state.value.isPrivateMode)
        ) {
            browserViewModel.resetToCleanSession()
        }

        setContent {
            App()
        }
    }

    override fun onPause() {
        com.example.peachbrowser.webview.AppBackgroundTracker.updateBackgroundState(true)
        super.onPause()
        ensureBackgroundMediaServiceState()
    }

    override fun onStop() {
        super.onStop()
        ensureBackgroundMediaServiceState()
    }

    private fun ensureBackgroundMediaServiceState() {
        try {
            val state = browserViewModel.state.value
            val activeUrl = state.currentTab?.url ?: ""
            val isStreamingSite = MediaDetector.isKnownStreamingSite(activeUrl)
            if (state.isBackgroundPlayEnabled && (state.isAudioPlaying || isStreamingSite)) {
                PeachMediaService.start(this)
                // Ensure active WebView timers remain unthrottled while backgrounded
                state.currentTab?.id?.let { tabId ->
                    AndroidTabWebViewPool.get(tabId)?.resumeTimers()
                }
            } else if (!state.isBackgroundPlayEnabled) {
                PeachMediaService.stop(this)
                AndroidTabWebViewPool.pauseAllMedia()
                audioFocusManager.abandonAudioFocus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            com.example.peachbrowser.webview.AppBackgroundTracker.updateBackgroundState(false)
            PeachMediaService.stop(this)
            val activeTabId = browserViewModel.state.value.activeTabId
            AndroidTabWebViewPool.get(activeTabId)?.let { webView ->
                webView.onResume()
                webView.resumeTimers()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            if (isFinishing) {
                audioFocusManager.abandonAudioFocus()
                audioFocusManager.onPauseRequested = null
                audioFocusManager.onResumeRequested = null
                PeachMediaService.stop(this)
                com.example.peachbrowser.util.SessionPermissionManager.clearAllPermissions()
                if (browserViewModel.state.value.isClearSessionOnExitEnabled || browserViewModel.state.value.isPrivateMode) {
                    browserViewModel.resetToCleanSession()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}