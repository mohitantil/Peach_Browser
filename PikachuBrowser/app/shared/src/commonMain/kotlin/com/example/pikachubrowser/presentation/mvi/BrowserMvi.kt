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
