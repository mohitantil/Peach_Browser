package com.example.peachbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.service.PeachMediaService
import com.example.peachbrowser.util.ExternalLinkRouter
import com.example.peachbrowser.util.MediaDetector
import com.example.peachbrowser.util.PeachAudioFocusManager
import com.example.peachbrowser.webview.AndroidTabWebViewPool
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by inject()
    private val audioFocusManager: PeachAudioFocusManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null &&
            (browserViewModel.state.value.isClearSessionOnExitEnabled || browserViewModel.state.value.isPrivateMode)
        ) {
            browserViewModel.resetToCleanSession()
        }

        handleIncomingIntent(intent)
        observeMediaStateForPersistentNotification()

        setContent {
            App()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    9001
                )
            }
        }
    }

    private fun observeMediaStateForPersistentNotification() {
        lifecycleScope.launch {
            browserViewModel.state
                .map { Triple(it.isBackgroundPlayEnabled, it.isAudioPlaying, it.mediaMetadata) }
                .distinctUntilChanged()
                .collect { (bgEnabled, isPlaying, meta) ->
                    if (bgEnabled && (isPlaying || meta.title.isNotBlank())) {
                        if (isPlaying) {
                            audioFocusManager.requestAudioFocus()
                        }
                        PeachMediaService.startOrUpdate(
                            context = this@MainActivity,
                            title = meta.title.ifBlank { browserViewModel.state.value.activePageTitle.ifBlank { "Peach Browser" } },
                            artist = meta.artist.ifBlank { browserViewModel.state.value.currentUrl },
                            isPlaying = isPlaying
                        )
                    } else if (!bgEnabled) {
                        PeachMediaService.stop(this@MainActivity)
                    }
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action ?: return
        val type = incomingIntent.type

        when (action) {
            Intent.ACTION_VIEW -> {
                val dataUri = incomingIntent.data
                if (dataUri != null) {
                    ExternalLinkRouter.handleIncomingUrl(
                        rawUri = dataUri.toString(),
                        viewModel = browserViewModel
                    )
                }
            }
            Intent.ACTION_SEND -> {
                if (type == "text/plain") {
                    val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: incomingIntent.getStringExtra(Intent.EXTRA_STREAM)
                    if (!sharedText.isNullOrBlank()) {
                        ExternalLinkRouter.handleSharedText(
                            sharedText = sharedText,
                            viewModel = browserViewModel
                        )
                    }
                }
            }
        }
        incomingIntent.data = null
        incomingIntent.removeExtra(Intent.EXTRA_TEXT)
        incomingIntent.removeExtra(Intent.EXTRA_STREAM)
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
                PeachMediaService.startOrUpdate(
                    context = this,
                    title = state.mediaMetadata.title.ifBlank { state.activePageTitle.ifBlank { "Peach Browser" } },
                    artist = state.mediaMetadata.artist.ifBlank { activeUrl },
                    isPlaying = state.isAudioPlaying
                )
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
