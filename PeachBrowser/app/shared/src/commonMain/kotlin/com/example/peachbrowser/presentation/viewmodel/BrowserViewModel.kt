package com.example.peachbrowser.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.peachbrowser.currentTimeMillis
import com.example.peachbrowser.db.PeachDatabase
import com.example.peachbrowser.domain.model.Tab
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.BrowserState
import com.example.peachbrowser.presentation.mvi.DEFAULT_SITE_EXCEPTIONS
import com.example.peachbrowser.presentation.mvi.DownloadItem
import com.example.peachbrowser.presentation.mvi.QuickLink
import com.example.peachbrowser.presentation.mvi.SiteShieldConfig
import com.example.peachbrowser.presentation.mvi.SleepTimerAction
import com.example.peachbrowser.presentation.mvi.SleepTimerState
import com.example.peachbrowser.presentation.mvi.THIRTY_DAYS_MILLIS
import com.example.peachbrowser.util.ExternalLinkRouter
import com.example.peachbrowser.util.SearchEngine
import com.example.peachbrowser.util.UrlSanitizer
import com.example.peachbrowser.util.clearBrowserSession
import com.example.peachbrowser.util.deleteDownloadedFileFromStorage
import com.example.peachbrowser.util.exitBrowserApp
import com.example.peachbrowser.util.shredSiteData
import com.example.peachbrowser.util.stopPlatformMediaService
import com.example.peachbrowser.util.syncDownloadsWithStorage
import com.example.peachbrowser.webview.clearAllTabWebViews
import com.example.peachbrowser.webview.releaseTabWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BrowserViewModel(
    private val database: PeachDatabase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var downloadsSyncJob: Job? = null
    var onPausePlaybackAction: (() -> Unit)? = null

    init {
        ExternalLinkRouter.activeViewModel = this
        loadPersistedSettings()
        startRealTimeDownloadsSync()
    }

    private fun normalizeDomain(raw: String): String {
        return raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore(":")
            .removePrefix("www.")
    }

    private fun pruneOlderThan30Days(items: List<DownloadItem>, nowMillis: Long = currentTimeMillis()): List<DownloadItem> {
        val cutoff = nowMillis - THIRTY_DAYS_MILLIS
        return items.filter { it.timestamp >= cutoff }
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
            var siteExceptions = _state.value.siteExceptions
            var siteShieldOverrides = _state.value.siteShieldOverrides
            var allTimeDownloads = _state.value.downloads

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
                    "site_exceptions" -> {
                        siteExceptions = setting.setting_value
                            .split(",")
                            .map { normalizeDomain(it) }
                            .filter { it.isNotBlank() }
                            .toSet()
                    }
                    "site_shield_overrides" -> {
                        try {
                            siteShieldOverrides = Json.decodeFromString(setting.setting_value)
                        } catch (_: Exception) {}
                    }
                    "all_time_downloads" -> {
                        try {
                            val decoded = Json.decodeFromString<List<DownloadItem>>(setting.setting_value)
                            allTimeDownloads = pruneOlderThan30Days(decoded)
                        } catch (_: Exception) {}
                    }
                }
            }

            val syncedAllTime = syncDownloadsWithStorage(allTimeDownloads)

            _state.value = _state.value.copy(
                isBackgroundPlayEnabled = bgPlay,
                isAdBlockEnabled = adBlock,
                isPrivateMode = privateMode,
                isClearSessionOnExitEnabled = clearSession,
                isTrackingProtectionEnabled = trackingProtection,
                isFarblingEnabled = farbling,
                isDeAmpEnabled = deAmp,
                searchEngine = searchEngine,
                siteExceptions = siteExceptions,
                siteShieldOverrides = siteShieldOverrides,
                downloads = syncedAllTime
            )

            if (syncedAllTime != allTimeDownloads) {
                persistAllTimeDownloads(syncedAllTime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRealTimeDownloadsSync() {
        downloadsSyncJob?.cancel()
        downloadsSyncJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                performDownloadsStorageSync()
            }
        }
    }

    private suspend fun performDownloadsStorageSync() {
        val currentSession = _state.value.sessionDownloads
        val currentAllTime = _state.value.downloads
        if (currentSession.isEmpty() && currentAllTime.isEmpty()) return

        val now = currentTimeMillis()
        val (syncedSession, syncedAllTime) = withContext(Dispatchers.Default) {
            val prunedSession = pruneOlderThan30Days(currentSession, now)
            val prunedAll = pruneOlderThan30Days(currentAllTime, now)
            Pair(
                syncDownloadsWithStorage(prunedSession),
                syncDownloadsWithStorage(prunedAll)
            )
        }

        if (syncedSession != currentSession || syncedAllTime != currentAllTime) {
            _state.value = _state.value.copy(
                sessionDownloads = syncedSession,
                downloads = syncedAllTime
            )
            if (syncedAllTime != currentAllTime) {
                persistAllTimeDownloads(syncedAllTime)
            }
        }
    }

    private fun persistAllTimeDownloads(list: List<DownloadItem>) {
        try {
            persistSetting("all_time_downloads", Json.encodeToString(list))
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
        // Clearing session resets Current Session downloads while keeping 30-day All-Time downloads synced with phone storage
        _state.value = _state.value.copy(
            tabs = listOf(defaultTab),
            activeTabId = defaultTab.id,
            tabBackStack = listOf(defaultTab.id),
            activePageTitle = "New Tab",
            activePageContent = "",
            isAudioPlaying = false,
            sessionDownloads = emptyList()
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
                    val isHttp = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
                    val isLocalOrScheme = url.startsWith("file://", ignoreCase = true) ||
                            url.startsWith("content://", ignoreCase = true) ||
                            url.startsWith("about:", ignoreCase = true)

                    if (!isHttp && !isLocalOrScheme) {
                        url = "https://$url"
                    }
                    if (isHttp || (!isLocalOrScheme && !url.startsWith("about:"))) {
                        val host = normalizeDomain(url)
                        val isException = _state.value.siteExceptions.any { host == it || host.endsWith(".$it") }
                        if (_state.value.isDeAmpEnabled && !isException) {
                            url = UrlSanitizer.deAmp(url)
                        }
                        if (_state.value.isTrackingProtectionEnabled && !isException) {
                            url = UrlSanitizer.stripTrackingParams(url)
                        }
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
                    searchUrl = UrlSanitizer.stripTrackingParams(searchUrl)
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
                _state.value = _state.value.copy(
                    activePageTitle = title
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
                if (_state.value.isBackgroundPlayEnabled != intent.enabled) {
                    _state.value = _state.value.copy(
                        isBackgroundPlayEnabled = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("background_play_enabled", intent.enabled.toString())
                    if (!intent.enabled) {
                        stopPlatformMediaService()
                    }
                }
            }
            is BrowserIntent.ToggleAdBlock -> {
                if (_state.value.isAdBlockEnabled != intent.enabled) {
                    _state.value = _state.value.copy(
                        isAdBlockEnabled = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("ad_block_enabled", intent.enabled.toString())
                }
            }
            is BrowserIntent.TogglePrivateMode -> {
                if (_state.value.isPrivateMode != intent.enabled) {
                    _state.value = _state.value.copy(
                        isPrivateMode = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("private_mode", intent.enabled.toString())
                }
            }
            is BrowserIntent.ToggleClearSessionOnExit -> {
                if (_state.value.isClearSessionOnExitEnabled != intent.enabled) {
                    _state.value = _state.value.copy(isClearSessionOnExitEnabled = intent.enabled)
                    persistSetting("clear_session_on_exit", intent.enabled.toString())
                }
            }
            is BrowserIntent.ToggleTrackingProtection -> {
                if (_state.value.isTrackingProtectionEnabled != intent.enabled) {
                    val currentUrl = _state.value.currentUrl
                    val sanitizedUrl = if (intent.enabled && currentUrl.isNotBlank()) {
                        UrlSanitizer.stripTrackingParams(currentUrl)
                    } else {
                        currentUrl
                    }
                    updateCurrentTab { tab ->
                        if (sanitizedUrl.isNotBlank()) tab.copy(url = sanitizedUrl) else tab
                    }
                    _state.value = _state.value.copy(
                        isTrackingProtectionEnabled = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("tracking_protection_enabled", intent.enabled.toString())
                }
            }
            is BrowserIntent.ToggleFarbling -> {
                if (_state.value.isFarblingEnabled != intent.enabled) {
                    _state.value = _state.value.copy(
                        isFarblingEnabled = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("farbling_enabled", intent.enabled.toString())
                }
            }
            is BrowserIntent.ToggleDeAmp -> {
                if (_state.value.isDeAmpEnabled != intent.enabled) {
                    val currentUrl = _state.value.currentUrl
                    val deAmpedUrl = if (intent.enabled && currentUrl.isNotBlank()) {
                        UrlSanitizer.deAmp(currentUrl)
                    } else {
                        currentUrl
                    }
                    updateCurrentTab { tab ->
                        if (deAmpedUrl.isNotBlank()) tab.copy(url = deAmpedUrl) else tab
                    }
                    _state.value = _state.value.copy(
                        isDeAmpEnabled = intent.enabled,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("deamp_enabled", intent.enabled.toString())
                }
            }
            is BrowserIntent.ReloadActiveTab -> {
                _state.value = _state.value.copy(
                    shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                )
            }
            is BrowserIntent.ToggleSiteShield -> {
                val cleanHost = normalizeDomain(intent.host)
                if (cleanHost.isNotBlank()) {
                    val existing = _state.value.siteShieldOverrides[cleanHost]
                    if (existing != intent.config) {
                        val updatedOverrides = _state.value.siteShieldOverrides.toMutableMap()
                        updatedOverrides[cleanHost] = intent.config
                        _state.value = _state.value.copy(
                            siteShieldOverrides = updatedOverrides,
                            shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                        )
                        try {
                            persistSetting("site_shield_overrides", Json.encodeToString(updatedOverrides))
                        } catch (_: Exception) {}
                    }
                }
            }
            is BrowserIntent.AddSiteException -> {
                val cleanHost = normalizeDomain(intent.host)
                if (cleanHost.isNotBlank() && !_state.value.siteExceptions.contains(cleanHost)) {
                    val updated = _state.value.siteExceptions + cleanHost
                    _state.value = _state.value.copy(
                        siteExceptions = updated,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("site_exceptions", updated.joinToString(","))
                }
            }
            is BrowserIntent.RemoveSiteException -> {
                val cleanHost = normalizeDomain(intent.host)
                if (_state.value.siteExceptions.contains(cleanHost)) {
                    val updated = _state.value.siteExceptions - cleanHost
                    _state.value = _state.value.copy(
                        siteExceptions = updated,
                        shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                    )
                    persistSetting("site_exceptions", updated.joinToString(","))
                }
            }
            is BrowserIntent.ResetDefaultSiteExceptions -> {
                _state.value = _state.value.copy(
                    siteExceptions = DEFAULT_SITE_EXCEPTIONS,
                    shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                )
                persistSetting("site_exceptions", DEFAULT_SITE_EXCEPTIONS.joinToString(","))
            }
            is BrowserIntent.ShredSiteData -> {
                shredSiteData(intent.host)
                _state.value = _state.value.copy(
                    shouldReloadTrigger = _state.value.shouldReloadTrigger + 1
                )
            }
            is BrowserIntent.SetSearchEngine -> {
                _state.value = _state.value.copy(searchEngine = intent.engine)
                persistSetting("search_engine", intent.engine)
            }
            is BrowserIntent.ClearAllBrowsingData -> {
                clearBrowserSession()
                clearAllTabWebViews()
                _state.value = _state.value.copy(sessionDownloads = emptyList())
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
                if (_state.value.isAudioPlaying != intent.isPlaying) {
                    _state.value = _state.value.copy(isAudioPlaying = intent.isPlaying)
                }
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
                val now = currentTimeMillis()
                val stampedItem = if (intent.item.timestamp > 0L) intent.item else intent.item.copy(timestamp = now)
                val updatedSession = listOf(stampedItem) + _state.value.sessionDownloads.filter { it.id != stampedItem.id }
                val updatedAllTime = pruneOlderThan30Days(
                    listOf(stampedItem) + _state.value.downloads.filter { it.id != stampedItem.id },
                    now
                )
                _state.value = _state.value.copy(
                    sessionDownloads = updatedSession,
                    downloads = updatedAllTime
                )
                persistAllTimeDownloads(updatedAllTime)
            }
            is BrowserIntent.UpdateDownloadProgress -> {
                val updateFn: (DownloadItem) -> DownloadItem = {
                    if (it.id == intent.id) {
                        it.copy(
                            progress = intent.progress,
                            isCompleted = intent.isCompleted,
                            localUri = intent.localUri ?: it.localUri
                        )
                    } else it
                }
                val updatedSession = _state.value.sessionDownloads.map(updateFn)
                val updatedAllTime = _state.value.downloads.map(updateFn)
                _state.value = _state.value.copy(
                    sessionDownloads = updatedSession,
                    downloads = updatedAllTime
                )
                persistAllTimeDownloads(updatedAllTime)
            }
            is BrowserIntent.RemoveDownloadItem -> {
                val target = _state.value.downloads.find { it.id == intent.id }
                    ?: _state.value.sessionDownloads.find { it.id == intent.id }
                if (intent.deleteFromStorage && target != null) {
                    deleteDownloadedFileFromStorage(target)
                }
                val updatedSession = _state.value.sessionDownloads.filter { it.id != intent.id }
                val updatedAllTime = _state.value.downloads.filter { it.id != intent.id }
                _state.value = _state.value.copy(
                    sessionDownloads = updatedSession,
                    downloads = updatedAllTime
                )
                persistAllTimeDownloads(updatedAllTime)
            }
            is BrowserIntent.ClearSessionDownloads -> {
                _state.value = _state.value.copy(sessionDownloads = emptyList())
            }
            is BrowserIntent.SyncDownloadsWithStorage -> {
                viewModelScope.launch {
                    performDownloadsStorageSync()
                }
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
