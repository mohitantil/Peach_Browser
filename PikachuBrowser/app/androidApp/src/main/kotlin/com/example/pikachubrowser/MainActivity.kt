package com.example.pikachubrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.pikachubrowser.presentation.viewmodel.BrowserViewModel
import com.example.pikachubrowser.service.PikachuMediaService
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
        // If background playback is enabled, start the foreground service before entering background
        try {
            if (browserViewModel.state.value.isBackgroundPlayEnabled) {
                PikachuMediaService.start(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // When the user brings the browser back into the foreground, dismiss the background notification
        try {
            PikachuMediaService.stop(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            PikachuMediaService.stop(this)
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