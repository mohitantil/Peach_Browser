package com.example.pikachubrowser.presentation.mvi

import com.example.pikachubrowser.domain.model.Tab
import kotlinx.serialization.Serializable

@Serializable
data class QuickLink(
    val title: String,
    val url: String,
    val iconEmoji: String = "🌐"
)

sealed interface BrowserIntent {
    data class LoadUrl(val url: String) : BrowserIntent
    data class SearchQuery(val query: String) : BrowserIntent
    object GoBack : BrowserIntent
    object GoForward : BrowserIntent
    object GoHome : BrowserIntent
    object Reload : BrowserIntent
    data class UrlChanged(val url: String) : BrowserIntent
    data class PageLoaded(val url: String, val title: String) : BrowserIntent
    data class UpdatePageContent(val content: String) : BrowserIntent
    data class AddTab(val url: String = "") : BrowserIntent
    data class SwitchTab(val tabId: String) : BrowserIntent
    data class CloseTab(val tabId: String) : BrowserIntent
    data class ToggleBackgroundPlay(val enabled: Boolean) : BrowserIntent
    data class ToggleAdBlock(val enabled: Boolean) : BrowserIntent
    data class TogglePrivateMode(val enabled: Boolean) : BrowserIntent
    data class ToggleClearSessionOnExit(val enabled: Boolean) : BrowserIntent
    data class SetSearchEngine(val engine: String) : BrowserIntent
    object ClearAllBrowsingData : BrowserIntent
    data class AddQuickLink(val title: String, val url: String) : BrowserIntent
    data class RemoveQuickLink(val url: String) : BrowserIntent
    data class SaveBookmark(val url: String, val title: String) : BrowserIntent
    data class DeleteBookmark(val id: Long) : BrowserIntent
    data class DeleteHistoryItem(val id: Long) : BrowserIntent
    object ClearHistory : BrowserIntent
}

data class BrowserState(
    val tabs: List<Tab> = listOf(Tab(id = "tab_1")),
    val activeTabId: String = "tab_1",
    val isBackgroundPlayEnabled: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isPrivateMode: Boolean = false,
    val isClearSessionOnExitEnabled: Boolean = true,
    val searchEngine: String = "DuckDuckGo",
    val reloadTrigger: Int = 0,
    val activePageContent: String = "",
    val activePageTitle: String = "",
    val quickLinks: List<QuickLink> = listOf(
        QuickLink("DuckDuckGo", "duckduckgo.com", "🔍"),
        QuickLink("YouTube", "youtube.com", "▶️"),
        QuickLink("Wikipedia", "wikipedia.org", "🌐"),
        QuickLink("Reddit", "reddit.com", "💬")
    )
) {
    val currentTab: Tab? get() = tabs.find { it.id == activeTabId }
    val currentUrl: String get() = currentTab?.url ?: ""
    val canGoBack: Boolean get() = (currentTab?.historyIndex ?: 0) > 0
    val canGoForward: Boolean get() = (currentTab?.historyIndex ?: 0) < ((currentTab?.history?.size ?: 1) - 1)
}
