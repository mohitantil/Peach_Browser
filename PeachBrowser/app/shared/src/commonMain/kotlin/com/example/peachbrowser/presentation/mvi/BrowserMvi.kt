package com.example.peachbrowser.presentation.mvi

import com.example.peachbrowser.domain.model.Tab
import kotlinx.serialization.Serializable

@Serializable
data class QuickLink(
    val title: String,
    val url: String,
    val iconEmoji: String = "🌐"
)

enum class SleepTimerAction {
    PAUSE_PLAYBACK,
    CLOSE_TAB,
    CLOSE_APP
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val targetEndTimeMillis: Long = 0L,
    val action: SleepTimerAction = SleepTimerAction.PAUSE_PLAYBACK
) {
    val formattedRemaining: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
}

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val url: String,
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val localUri: String? = null,
    val timestamp: Long = 0L
)

sealed interface BrowserIntent {
    data class LoadUrl(val url: String) : BrowserIntent
    data class SearchQuery(val query: String) : BrowserIntent
    object GoHome : BrowserIntent
    data class UrlChanged(val url: String) : BrowserIntent
    data class PageLoaded(val url: String, val title: String) : BrowserIntent
    data class UpdatePageContent(val content: String) : BrowserIntent
    data class AddTab(val url: String = "", val parentTabId: String? = null) : BrowserIntent
    data class SwitchTab(val tabId: String) : BrowserIntent
    data class CloseTab(val tabId: String) : BrowserIntent
    object CloseCurrentTab : BrowserIntent
    object PopTab : BrowserIntent
    data class UpdateTabNavState(
        val tabId: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val url: String,
        val title: String
    ) : BrowserIntent
    data class ToggleBackgroundPlay(val enabled: Boolean) : BrowserIntent
    data class ToggleAdBlock(val enabled: Boolean) : BrowserIntent
    data class TogglePrivateMode(val enabled: Boolean) : BrowserIntent
    data class ToggleClearSessionOnExit(val enabled: Boolean) : BrowserIntent
    data class ToggleTrackingProtection(val enabled: Boolean) : BrowserIntent
    data class ToggleFarbling(val enabled: Boolean) : BrowserIntent
    data class ToggleDeAmp(val enabled: Boolean) : BrowserIntent
    data class SetSearchEngine(val engine: String) : BrowserIntent
    object ClearAllBrowsingData : BrowserIntent
    data class AddQuickLink(val title: String, val url: String) : BrowserIntent
    data class RemoveQuickLink(val url: String) : BrowserIntent
    data class SaveBookmark(val url: String, val title: String) : BrowserIntent
    data class DeleteBookmark(val id: Long) : BrowserIntent
    data class DeleteHistoryItem(val id: Long) : BrowserIntent
    object ClearHistory : BrowserIntent

    // Sleep Timer Intents
    data class SetAudioPlaying(val isPlaying: Boolean) : BrowserIntent
    data class StartSleepTimer(val durationMinutes: Int, val action: SleepTimerAction = SleepTimerAction.PAUSE_PLAYBACK) : BrowserIntent
    object CancelSleepTimer : BrowserIntent
    data class ExtendSleepTimer(val additionalMinutes: Int) : BrowserIntent
    data class UpdateSleepTimerRemaining(val remainingSeconds: Int) : BrowserIntent

    // Download Intents
    data class AddDownload(val item: DownloadItem) : BrowserIntent
    data class UpdateDownloadProgress(val id: Long, val progress: Int, val isCompleted: Boolean, val localUri: String? = null) : BrowserIntent
}

data class BrowserState(
    val tabs: List<Tab> = listOf(Tab(id = "tab_1")),
    val activeTabId: String = "tab_1",
    val tabBackStack: List<String> = listOf("tab_1"),
    val isBackgroundPlayEnabled: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isPrivateMode: Boolean = false,
    val isClearSessionOnExitEnabled: Boolean = true,
    val isTrackingProtectionEnabled: Boolean = true,
    val isFarblingEnabled: Boolean = true,
    val isDeAmpEnabled: Boolean = true,
    val searchEngine: String = "DuckDuckGo",
    val activePageContent: String = "",
    val activePageTitle: String = "",
    val isAudioPlaying: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val downloads: List<DownloadItem> = emptyList(),
    val quickLinks: List<QuickLink> = listOf(
        QuickLink("DuckDuckGo", "duckduckgo.com", "🔍"),
        QuickLink("YouTube", "youtube.com", "▶️"),
        QuickLink("Wikipedia", "wikipedia.org", "🌐"),
        QuickLink("Reddit", "reddit.com", "💬")
    )
) {
    val currentTab: Tab? get() = tabs.find { it.id == activeTabId }
    val currentUrl: String get() = currentTab?.url ?: ""
    val canGoBackInTab: Boolean get() = currentTab?.canGoBack == true
    val canGoForwardInTab: Boolean get() = currentTab?.canGoForward == true
    val hasTabBackHistory: Boolean get() = tabBackStack.size > 1
    val previousTabId: String? get() = if (tabBackStack.size > 1) tabBackStack[tabBackStack.size - 2] else null
}
