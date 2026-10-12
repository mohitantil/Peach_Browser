package com.example.peachbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.service.PeachMediaService
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    override fun onPause() {
        super.onPause()
        // If background playback is enabled AND media is actually streaming, start the foreground service
        try {
            if (browserViewModel.state.value.isBackgroundPlayEnabled && browserViewModel.state.value.isAudioPlaying) {
                PeachMediaService.start(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // When the user brings the browser back into the foreground, dismiss the background notification
        try {
            PeachMediaService.stop(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            PeachMediaService.stop(this)
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