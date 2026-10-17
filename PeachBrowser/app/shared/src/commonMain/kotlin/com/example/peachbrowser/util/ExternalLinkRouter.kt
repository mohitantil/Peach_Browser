package com.example.peachbrowser.util

import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel

object ExternalLinkRouter {

    @Volatile
    var activeViewModel: BrowserViewModel? = null

    var onNavigateToBrowserHome: (() -> Unit)? = null

    private val URL_REGEX = Regex(
        """https?://[a-zA-Z0-9.\-_~:/?#\[\]@!$&'()*+,;=%]+""",
        RegexOption.IGNORE_CASE
    )

    fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        val match = URL_REGEX.find(trimmed)
        return match?.value
    }

    /**
     * Handles raw shared text (e.g. from Android ACTION_SEND or share sheets).
     * Extracts an HTTP/HTTPS link if present, or treats text as a search query.
     */
    fun handleSharedText(sharedText: String, viewModel: BrowserViewModel? = activeViewModel) {
        val vm = viewModel ?: activeViewModel ?: return
        val trimmed = sharedText.trim()
        if (trimmed.isBlank()) return

        val extracted = extractUrl(trimmed)
        if (extracted != null) {
            handleIncomingUrl(extracted, vm)
        } else {
            vm.processIntent(BrowserIntent.SearchQuery(trimmed))
            onNavigateToBrowserHome?.invoke()
        }
    }

    /**
     * Handles incoming URLs or local document URIs from ACTION_VIEW, custom schemes, or external files.
     */
    fun handleIncomingUrl(rawUri: String, viewModel: BrowserViewModel? = activeViewModel) {
        val vm = viewModel ?: activeViewModel ?: return
        val trimmed = rawUri.trim()
        if (trimmed.isBlank()) return

        // Clean & de-amp web links
        val targetUrl = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            var url = UrlSanitizer.stripTrackingParams(trimmed)
            if (vm.state.value.isDeAmpEnabled) {
                url = UrlSanitizer.deAmp(url)
            }
            url
        } else {
            trimmed
        }

        val state = vm.state.value
        val activeTab = state.currentTab

        // If current tab is empty / on landing page, load within this tab;
        // Otherwise, spawn a new tab so user's ongoing reading/playback session is not wiped.
        if (activeTab == null || activeTab.url.isBlank()) {
            vm.processIntent(BrowserIntent.LoadUrl(targetUrl))
        } else {
            vm.processIntent(BrowserIntent.AddTab(targetUrl))
        }

        onNavigateToBrowserHome?.invoke()
    }

    fun handleSharedText(sharedText: String) {
        handleSharedText(sharedText, activeViewModel)
    }

    fun handleIncomingUrl(rawUri: String) {
        handleIncomingUrl(rawUri, activeViewModel)
    }
}
