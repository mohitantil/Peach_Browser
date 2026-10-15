package com.example.peachbrowser.presentation.mvi

import com.example.peachbrowser.domain.model.Tab
import kotlinx.serialization.Serializable

val DEFAULT_SITE_EXCEPTIONS = setOf(
    "gemini.google.com",
    "aistudio.google.com",
    "chatgpt.com",
    "claude.ai",
    "deepseek.com",
    "z.ai",
    "qwen.ai",
    "openrouter.ai"
)

const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L

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

@Serializable
data class SiteShieldConfig(
    val shieldsUp: Boolean = true,
    val blockTrackersAndAds: Boolean = true,
    val blockScripts: Boolean = false,
    val blockFingerprinting: Boolean = true,
    val blockCookies: Boolean = false,
    val blockedCount: Int = 0
)

@Serializable
data class DownloadItem(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String = "",
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val localUri: String? = null,
    val filePath: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val timestamp: Long = 0L
)

sealed interface BrowserIntent {
    data class LoadUrl(val url: String) : BrowserIntent
    data class SearchQuery(val query: String) : BrowserIntent
    object GoHome : BrowserIntent
    object ResetToCleanSession : BrowserIntent
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

    // Shield, Site Exceptions & Real-Time Reload Intents
    data class ReloadActiveTab(val forced: Boolean = false) : BrowserIntent
    data class ToggleSiteShield(val host: String, val config: SiteShieldConfig) : BrowserIntent
    data class AddSiteException(val host: String) : BrowserIntent
    data class RemoveSiteException(val host: String) : BrowserIntent
    object ResetDefaultSiteExceptions : BrowserIntent
    data class ShredSiteData(val host: String) : BrowserIntent

    // Sleep Timer Intents
    data class SetAudioPlaying(val isPlaying: Boolean) : BrowserIntent
    data class StartSleepTimer(val durationMinutes: Int, val action: SleepTimerAction = SleepTimerAction.PAUSE_PLAYBACK) : BrowserIntent
    object CancelSleepTimer : BrowserIntent
    data class ExtendSleepTimer(val additionalMinutes: Int) : BrowserIntent
    data class UpdateSleepTimerRemaining(val remainingSeconds: Int) : BrowserIntent

    // Download Intents
    data class AddDownload(val item: DownloadItem) : BrowserIntent
    data class UpdateDownloadProgress(
        val id: Long,
        val progress: Int,
        val isCompleted: Boolean,
        val localUri: String? = null
    ) : BrowserIntent
    data class RemoveDownloadItem(val id: Long, val deleteFromStorage: Boolean = false) : BrowserIntent
    object ClearSessionDownloads : BrowserIntent
    object SyncDownloadsWithStorage : BrowserIntent
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
    val sessionDownloads: List<DownloadItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val siteShieldOverrides: Map<String, SiteShieldConfig> = emptyMap(),
    val siteExceptions: Set<String> = DEFAULT_SITE_EXCEPTIONS,
    val shouldReloadTrigger: Int = 0,
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
