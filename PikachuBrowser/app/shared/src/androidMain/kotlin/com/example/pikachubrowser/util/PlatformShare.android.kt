package com.example.pikachubrowser.util

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareLauncher(): (url: String, title: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url, title ->
            try {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, if (title.isNotBlank()) "$title\n$url" else url)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share with Pikachu")
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
