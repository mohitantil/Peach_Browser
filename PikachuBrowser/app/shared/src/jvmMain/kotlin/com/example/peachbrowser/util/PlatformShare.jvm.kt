package com.example.peachbrowser.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberShareLauncher(): (url: String, title: String) -> Unit {
    return remember {
        { url, title ->
            try {
                val clipText = if (title.isNotBlank()) "$title\n$url" else url
                val selection = StringSelection(clipText)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
