package com.example.pikachubrowser.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pikachubrowser.currentTimeMillis
import com.example.pikachubrowser.db.PikachuDatabase
import com.example.pikachubrowser.domain.model.Tab
import com.example.pikachubrowser.presentation.mvi.BrowserIntent
import com.example.pikachubrowser.presentation.mvi.BrowserState
import com.example.pikachubrowser.presentation.mvi.QuickLink
import com.example.pikachubrowser.util.UrlSanitizer
import com.example.pikachubrowser.util.clearBrowserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val database: PikachuDatabase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    init {
        loadPersistedSettings()
    }

    private fun loadPersistedSettings() {
        try {
            val settingsList = database?.settingsQueries?.getAllSettings()?.executeAsList() ?: emptyList()
            var bgPlay = _state.value.isBackgroundPlayEnabled
            var adBlock = _state.value.isAdBlockEnabled
            var privateMode = _state.value.isPrivateMode
            var clearSession = _state.value.isClearSessionOnExitEnabled
            var trackingProtection = _state.value.isTrackingProtectionEnabled
            var farbling = _state.value.isFarblingEnabled
            var deAmp = _state.value.isDeAmpEnabled
            var searchEngine = _state.value.searchEngine

            for (setting in settingsList) {
                when (setting.key) {
                    "background_play_enabled" -> bgPlay = setting.setting_value.toBooleanStrictOrNull() ?: bgPlay
                    "ad_block_enabled" -> adBlock = setting.setting_value.toBooleanStrictOrNull() ?: adBlock
                    "private_mode" -> privateMode = setting.setting_value.toBooleanStrictOrNull() ?: privateMode
                    "clear_session_on_exit" -> clearSession = setting.setting_value.toBooleanStrictOrNull() ?: clearSession
                    "tracking_protection_enabled" -> trackingProtection = setting.setting_value.toBooleanStrictOrNull() ?: trackingProtection
                    "farbling_enabled" -> farbling = setting.setting_value.toBooleanStrictOrNull() ?: farbling
                    "deamp_enabled" -> deAmp = setting.setting_value.toBooleanStrictOrNull() ?: deAmp
                    "search_engine" -> searchEngine = setting.setting_value
                }
            }

            _state.value = _state.value.copy(
                isBackgroundPlayEnabled = bgPlay,
                isAdBlockEnabled = adBlock,
                isPrivateMode = privateMode,
                isClearSessionOnExitEnabled = clearSession,
                isTrackingProtectionEnabled = trackingProtection,
                isFarblingEnabled = farbling,
                isDeAmpEnabled = deAmp,
                searchEngine = searchEngine
            )

            // If clear session on exit is enabled or private mode is active, clear cookies/storage on startup
            if (clearSession || privateMode) {
                clearBrowserSession()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistSetting(key: String, value: String) {
        viewModelScope.launch {
            try {
                database?.settingsQueries?.setSetting(key, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun processIntent(intent: BrowserIntent) {
        when (intent) {
            is BrowserIntent.LoadUrl -> {
                var url = intent.url.trim()
                if (url.isNotBlank()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    if (_state.value.isTrackingProtectionEnabled || _state.value.isDeAmpEnabled) {
                        url = UrlSanitizer.sanitize(url)
                    }
                    updateCurrentTab { tab ->
                        tab.copy(url = url)
                    }
                }
            }
            is BrowserIntent.SearchQuery -> {
                var searchUrl = when (_state.value.searchEngine.lowercase()) {
                    "google" -> "https://www.google.com/search?q=${intent.query.trim()}"
                    "brave" -> "https://search.brave.com/search?q=${intent.query.trim()}"
                    else -> "https://html.duckduckgo.com/html/?q=${intent.query.trim()}"
                }
                if (_state.value.isTrackingProtectionEnabled) {
                    searchUrl = UrlSanitizer.sanitize(searchUrl)
                }
                updateCurrentTab { tab ->
                    tab.copy(url = searchUrl)
                }
            }
            is BrowserIntent.UrlChanged -> {
                updateCurrentTab { tab ->
                    tab.copy(url = intent.url)
                }
            }
            is BrowserIntent.PageLoaded -> {
                val url = intent.url
                val title = intent.title.ifBlank { url }
                _state.value = _state.value.copy(activePageTitle = title)
                updateCurrentTab { tab ->
                    tab.copy(url = url, title = title)
                }
                if (!_state.value.isPrivateMode && url.isNotBlank() && !url.startsWith("about:")) {
                    viewModelScope.launch {
                        try {
                            database?.historyQueries?.insertHistory(
                                url = url,
                                title = title,
                                visitedAt = currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            is BrowserIntent.UpdatePageContent -> {
                _state.value = _state.value.copy(activePageContent = intent.content)
            }
            is BrowserIntent.GoHome -> {
                updateCurrentTab { tab ->
                    tab.copy(url = "", title = "New Tab")
                }
                _state.value = _state.value.copy(activePageTitle = "New Tab")
            }
            is BrowserIntent.AddTab -> {
                val newId = "tab_${currentTimeMillis()}_${_state.value.tabs.size + 1}"
                val initialTitle = if (intent.url.isNotBlank()) intent.url else "New Tab"
                val newTab = Tab(
                    id = newId,
                    title = initialTitle,
                    url = intent.url
                )
                _state.value = _state.value.copy(
                    tabs = _state.value.tabs + newTab,
                    activeTabId = newTab.id,
                    activePageTitle = initialTitle
                )
            }
            is BrowserIntent.SwitchTab -> {
                val targetTab = _state.value.tabs.find { it.id == intent.tabId }
                if (targetTab != null) {
                    _state.value = _state.value.copy(
                        activeTabId = intent.tabId,
                        activePageTitle = targetTab.title
                    )
                }
            }
            is BrowserIntent.CloseTab -> {
                val remainingTabs = _state.value.tabs.filter { it.id != intent.tabId }
                if (remainingTabs.isNotEmpty()) {
                    val nextActiveId = if (_state.value.activeTabId == intent.tabId) {
                        remainingTabs.last().id
                    } else {
                        _state.value.activeTabId
                    }
                    val nextActiveTab = remainingTabs.find { it.id == nextActiveId }
                    _state.value = _state.value.copy(
                        tabs = remainingTabs,
                        activeTabId = nextActiveId,
                        activePageTitle = nextActiveTab?.title ?: "New Tab"
                    )
                } else {
                    val defaultTab = Tab(
                        id = "tab_${currentTimeMillis()}_1",
                        title = "New Tab",
                        url = ""
                    )
                    _state.value = _state.value.copy(
                        tabs = listOf(defaultTab),
                        activeTabId = defaultTab.id,
                        activePageTitle = "New Tab"
                    )
                }
            }
            is BrowserIntent.ToggleBackgroundPlay -> {
                _state.value = _state.value.copy(isBackgroundPlayEnabled = intent.enabled)
                persistSetting("background_play_enabled", intent.enabled.toString())
            }
            is BrowserIntent.ToggleAdBlock -> {
                _state.value = _state.value.copy(isAdBlockEnabled = intent.enabled)
                persistSetting("ad_block_enabled", intent.enabled.toString())
            }
            is BrowserIntent.TogglePrivateMode -> {
                _state.value = _state.value.copy(isPrivateMode = intent.enabled)
                persistSetting("private_mode", intent.enabled.toString())
                if (intent.enabled) {
                    clearBrowserSession()
                }
            }
            is BrowserIntent.ToggleClearSessionOnExit -> {
                _state.value = _state.value.copy(isClearSessionOnExitEnabled = intent.enabled)
                persistSetting("clear_session_on_exit", intent.enabled.toString())
            }
            is BrowserIntent.ToggleTrackingProtection -> {
                _state.value = _state.value.copy(isTrackingProtectionEnabled = intent.enabled)
                persistSetting("tracking_protection_enabled", intent.enabled.toString())
            }
            is BrowserIntent.ToggleFarbling -> {
                _state.value = _state.value.copy(isFarblingEnabled = intent.enabled)
                persistSetting("farbling_enabled", intent.enabled.toString())
            }
            is BrowserIntent.ToggleDeAmp -> {
                _state.value = _state.value.copy(isDeAmpEnabled = intent.enabled)
                persistSetting("deamp_enabled", intent.enabled.toString())
            }
            is BrowserIntent.SetSearchEngine -> {
                _state.value = _state.value.copy(searchEngine = intent.engine)
                persistSetting("search_engine", intent.engine)
            }
            is BrowserIntent.ClearAllBrowsingData -> {
                clearBrowserSession()
                viewModelScope.launch {
                    try {
                        database?.historyQueries?.clearAllHistory()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            is BrowserIntent.AddQuickLink -> {
                val link = QuickLink(title = intent.title, url = intent.url)
                _state.value = _state.value.copy(quickLinks = _state.value.quickLinks + link)
            }
            is BrowserIntent.RemoveQuickLink -> {
                _state.value = _state.value.copy(quickLinks = _state.value.quickLinks.filter { it.url != intent.url })
            }
            is BrowserIntent.SaveBookmark -> {
                viewModelScope.launch {
                    try {
                        database?.bookmarksQueries?.insertBookmark(
                            url = intent.url,
                            title = intent.title.ifBlank { intent.url },
                            createdAt = currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            is BrowserIntent.DeleteBookmark -> {
                viewModelScope.launch {
                    try {
                        database?.bookmarksQueries?.deleteBookmark(intent.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            is BrowserIntent.DeleteHistoryItem -> {
                viewModelScope.launch {
                    try {
                        database?.historyQueries?.deleteHistoryById(intent.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            is BrowserIntent.ClearHistory -> {
                viewModelScope.launch {
                    try {
                        database?.historyQueries?.clearAllHistory()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun updateCurrentTab(update: (Tab) -> Tab) {
        val currentState = _state.value
        val updatedTabs = currentState.tabs.map {
            if (it.id == currentState.activeTabId) update(it) else it
        }
        _state.value = currentState.copy(tabs = updatedTabs)
    }
}
