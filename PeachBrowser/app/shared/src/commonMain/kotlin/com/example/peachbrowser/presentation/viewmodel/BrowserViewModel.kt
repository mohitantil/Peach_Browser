package com.example.peachbrowser.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peachbrowser.currentTimeMillis
import com.example.peachbrowser.db.PeachDatabase
import com.example.peachbrowser.domain.model.Tab
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.BrowserState
import com.example.peachbrowser.presentation.mvi.QuickLink
import com.example.peachbrowser.presentation.mvi.DownloadItem
import com.example.peachbrowser.presentation.mvi.SleepTimerAction
import com.example.peachbrowser.presentation.mvi.SleepTimerState
import com.example.peachbrowser.util.MediaDetector
import com.example.peachbrowser.util.SearchEngine
import com.example.peachbrowser.util.UrlSanitizer
import com.example.peachbrowser.util.clearBrowserSession
import com.example.peachbrowser.util.exitBrowserApp
import com.example.peachbrowser.util.stopPlatformMediaService
import com.example.peachbrowser.webview.clearAllTabWebViews
import com.example.peachbrowser.webview.releaseTabWebView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val database: PeachDatabase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null
    var onPausePlaybackAction: (() -> Unit)? = null

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

            // If clear session on exit is enabled or private mode is active, enforce clean session on startup
            if (clearSession || privateMode) {
                resetToCleanSession()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToCleanSession() {
        val defaultTab = Tab(
            id = "tab_${currentTimeMillis()}_1",
            title = "New Tab",
            url = "",
            initialUrl = "",
            createdAt = currentTimeMillis(),
            lastAccessedAt = currentTimeMillis()
        )
        clearAllTabWebViews()
        clearBrowserSession()
        _state.value = _state.value.copy(
            tabs = listOf(defaultTab),
            activeTabId = defaultTab.id,
            tabBackStack = listOf(defaultTab.id),
            activePageTitle = "New Tab",
            activePageContent = "",
            isAudioPlaying = false
        )
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
                        tab.copy(
                            url = url,
                            initialUrl = if (tab.url.isBlank()) url else tab.initialUrl,
                            lastAccessedAt = currentTimeMillis()
                        )
                    }
                }
            }
            is BrowserIntent.SearchQuery -> {
                val engine = SearchEngine.fromName(_state.value.searchEngine)
                var searchUrl = engine.buildSearchUrl(intent.query)
                if (_state.value.isTrackingProtectionEnabled) {
                    searchUrl = UrlSanitizer.sanitize(searchUrl)
                }
                updateCurrentTab { tab ->
                    tab.copy(
                        url = searchUrl,
                        initialUrl = if (tab.url.isBlank()) searchUrl else tab.initialUrl,
                        lastAccessedAt = currentTimeMillis()
                    )
                }
            }
            is BrowserIntent.UrlChanged -> {
                updateCurrentTab { tab ->
                    tab.copy(url = intent.url, lastAccessedAt = currentTimeMillis())
                }
            }
            is BrowserIntent.PageLoaded -> {
                val url = intent.url
                val title = intent.title.ifBlank { url }
                val isStreamingSite = MediaDetector.isKnownStreamingSite(url)
                _state.value = _state.value.copy(
                    activePageTitle = title,
                    isAudioPlaying = if (isStreamingSite) true else _state.value.isAudioPlaying
                )
                updateCurrentTab { tab ->
                    tab.copy(url = url, title = title, lastAccessedAt = currentTimeMillis())
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
            is BrowserIntent.UpdateTabNavState -> {
                val currentState = _state.value
                val updatedTabs = currentState.tabs.map {
                    if (it.id == intent.tabId) {
                        it.copy(
                            canGoBack = intent.canGoBack,
                            canGoForward = intent.canGoForward,
                            url = if (intent.url.isNotBlank()) intent.url else it.url,
                            title = if (intent.title.isNotBlank()) intent.title else it.title
                        )
                    } else it
                }
                val activeTab = updatedTabs.find { it.id == currentState.activeTabId }
                _state.value = currentState.copy(
                    tabs = updatedTabs,
                    activePageTitle = activeTab?.title ?: currentState.activePageTitle
                )
            }
            is BrowserIntent.UpdatePageContent -> {
                _state.value = _state.value.copy(activePageContent = intent.content)
            }
            is BrowserIntent.GoHome -> {
                releaseTabWebView(_state.value.activeTabId)
                updateCurrentTab { tab ->
                    tab.copy(url = "", title = "New Tab", canGoBack = false, canGoForward = false)
                }
                _state.value = _state.value.copy(activePageTitle = "New Tab")
            }
            is BrowserIntent.ResetToCleanSession -> {
                resetToCleanSession()
            }
            is BrowserIntent.AddTab -> {
                val newId = "tab_${currentTimeMillis()}_${_state.value.tabs.size + 1}"
                val initialTitle = if (intent.url.isNotBlank()) intent.url else "New Tab"
                val newTab = Tab(
                    id = newId,
                    title = initialTitle,
                    url = intent.url,
                    initialUrl = intent.url,
                    createdAt = currentTimeMillis(),
                    lastAccessedAt = currentTimeMillis()
                )
                val updatedBackStack = _state.value.tabBackStack.filter { it != newId } + newId
                _state.value = _state.value.copy(
                    tabs = _state.value.tabs + newTab,
                    activeTabId = newTab.id,
                    tabBackStack = updatedBackStack,
                    activePageTitle = initialTitle
                )
            }
            is BrowserIntent.SwitchTab -> {
                val targetTab = _state.value.tabs.find { it.id == intent.tabId }
                if (targetTab != null) {
                    val updatedBackStack = _state.value.tabBackStack.filter { it != intent.tabId } + intent.tabId
                    val updatedTabs = _state.value.tabs.map {
                        if (it.id == intent.tabId) it.copy(lastAccessedAt = currentTimeMillis()) else it
                    }
                    _state.value = _state.value.copy(
                        tabs = updatedTabs,
                        activeTabId = intent.tabId,
                        tabBackStack = updatedBackStack,
                        activePageTitle = targetTab.title
                    )
                }
            }
            is BrowserIntent.PopTab -> {
                if (_state.value.tabBackStack.size > 1) {
                    val currentTabId = _state.value.activeTabId
                    val remainingBackStack = _state.value.tabBackStack.filter { it != currentTabId }
                    val previousTabId = remainingBackStack.last()
                    val targetTab = _state.value.tabs.find { it.id == previousTabId }
                    if (targetTab != null) {
                        _state.value = _state.value.copy(
                            activeTabId = previousTabId,
                            tabBackStack = remainingBackStack,
                            activePageTitle = targetTab.title
                        )
                    }
                }
            }
            is BrowserIntent.CloseCurrentTab -> {
                processIntent(BrowserIntent.CloseTab(_state.value.activeTabId))
            }
            is BrowserIntent.CloseTab -> {
                releaseTabWebView(intent.tabId)
                val remainingTabs = _state.value.tabs.filter { it.id != intent.tabId }
                val remainingBackStack = _state.value.tabBackStack.filter { it != intent.tabId }

                if (remainingTabs.isNotEmpty()) {
                    val nextActiveId = if (_state.value.activeTabId == intent.tabId) {
                        remainingBackStack.lastOrNull() ?: remainingTabs.last().id
                    } else {
                        _state.value.activeTabId
                    }
                    val finalBackStack = if (remainingBackStack.isNotEmpty()) remainingBackStack else listOf(nextActiveId)
                    val nextActiveTab = remainingTabs.find { it.id == nextActiveId }
                    _state.value = _state.value.copy(
                        tabs = remainingTabs,
                        activeTabId = nextActiveId,
                        tabBackStack = finalBackStack,
                        activePageTitle = nextActiveTab?.title ?: "New Tab"
                    )
                } else {
                    val defaultTab = Tab(
                        id = "tab_${currentTimeMillis()}_1",
                        title = "New Tab",
                        url = "",
                        initialUrl = "",
                        createdAt = currentTimeMillis(),
                        lastAccessedAt = currentTimeMillis()
                    )
                    _state.value = _state.value.copy(
                        tabs = listOf(defaultTab),
                        activeTabId = defaultTab.id,
                        tabBackStack = listOf(defaultTab.id),
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
                clearAllTabWebViews()
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
            is BrowserIntent.SetAudioPlaying -> {
                _state.value = _state.value.copy(isAudioPlaying = intent.isPlaying)
            }
            is BrowserIntent.StartSleepTimer -> {
                startSleepTimer(intent.durationMinutes, intent.action)
            }
            is BrowserIntent.CancelSleepTimer -> {
                sleepTimerJob?.cancel()
                sleepTimerJob = null
                _state.value = _state.value.copy(sleepTimer = SleepTimerState())
            }
            is BrowserIntent.ExtendSleepTimer -> {
                val current = _state.value.sleepTimer
                if (current.isActive) {
                    val addSec = intent.additionalMinutes * 60
                    val newTarget = current.targetEndTimeMillis + (addSec * 1000L)
                    val newTotal = current.totalSeconds + addSec
                    startSleepTimerInternal(newTarget, newTotal, current.action)
                }
            }
            is BrowserIntent.UpdateSleepTimerRemaining -> {
                _state.value = _state.value.copy(
                    sleepTimer = _state.value.sleepTimer.copy(remainingSeconds = intent.remainingSeconds)
                )
            }
            is BrowserIntent.AddDownload -> {
                val existing = _state.value.downloads
                if (existing.none { it.id == intent.item.id }) {
                    _state.value = _state.value.copy(downloads = listOf(intent.item) + existing)
                }
            }
            is BrowserIntent.UpdateDownloadProgress -> {
                val updated = _state.value.downloads.map {
                    if (it.id == intent.id) {
                        it.copy(
                            progress = intent.progress,
                            isCompleted = intent.isCompleted,
                            localUri = intent.localUri ?: it.localUri
                        )
                    } else it
                }
                _state.value = _state.value.copy(downloads = updated)
            }
        }
    }

    private fun startSleepTimer(durationMinutes: Int, action: SleepTimerAction) {
        val totalSeconds = durationMinutes * 60
        val targetEndTime = currentTimeMillis() + (totalSeconds * 1000L)
        startSleepTimerInternal(targetEndTime, totalSeconds, action)
    }

    private fun startSleepTimerInternal(targetEndTimeMillis: Long, totalSeconds: Int, action: SleepTimerAction) {
        sleepTimerJob?.cancel()
        val initialRemaining = ((targetEndTimeMillis - currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
        _state.value = _state.value.copy(
            sleepTimer = SleepTimerState(
                isActive = true,
                remainingSeconds = initialRemaining,
                totalSeconds = totalSeconds,
                targetEndTimeMillis = targetEndTimeMillis,
                action = action
            )
        )

        sleepTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = currentTimeMillis()
                val diffSeconds = ((targetEndTimeMillis - now) / 1000L).coerceAtLeast(0L).toInt()
                _state.value = _state.value.copy(
                    sleepTimer = _state.value.sleepTimer.copy(remainingSeconds = diffSeconds)
                )
                if (diffSeconds <= 0) {
                    triggerSleepTimerExpiration(action)
                    break
                }
            }
        }
    }

    private fun triggerSleepTimerExpiration(action: SleepTimerAction) {
        _state.value = _state.value.copy(sleepTimer = SleepTimerState())
        when (action) {
            SleepTimerAction.PAUSE_PLAYBACK -> {
                _state.value = _state.value.copy(isAudioPlaying = false)
                onPausePlaybackAction?.invoke()
                stopPlatformMediaService()
            }
            SleepTimerAction.CLOSE_TAB -> {
                processIntent(BrowserIntent.CloseCurrentTab)
            }
            SleepTimerAction.CLOSE_APP -> {
                exitBrowserApp()
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
