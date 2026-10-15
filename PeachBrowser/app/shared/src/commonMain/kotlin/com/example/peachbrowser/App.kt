package com.example.peachbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.viewmodel.BrowserViewModel
import com.example.peachbrowser.presentation.viewmodel.ChatViewModel
import com.example.peachbrowser.ui.ChatDrawer
import com.example.peachbrowser.ui.CommonBackHandler
import com.example.peachbrowser.ui.LandingPage
import com.example.peachbrowser.ui.MenuOverlay
import com.example.peachbrowser.ui.ShieldSheet
import com.example.peachbrowser.ui.SleepTimerSheet
import com.example.peachbrowser.ui.screens.ai.AiSettingsScreen
import com.example.peachbrowser.ui.screens.bookmarks.BookmarksScreen
import com.example.peachbrowser.ui.screens.downloads.DownloadsScreen
import com.example.peachbrowser.ui.screens.history.HistoryScreen
import com.example.peachbrowser.ui.screens.settings.SettingsScreen
import com.example.peachbrowser.ui.screens.tabs.TabsListScreen
import com.example.peachbrowser.util.SearchEngine
import com.example.peachbrowser.util.rememberShareLauncher
import com.example.peachbrowser.webview.PeachWebView
import com.example.peachbrowser.webview.rememberPeachWebViewController
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object BrowserHomeRoute

@Serializable
object TabsListRoute

@Serializable
object SettingsRoute

@Serializable
object HistoryRoute

@Serializable
object BookmarksRoute

@Serializable
object DownloadsRoute

@Serializable
object AiSettingsRoute

private val CustomTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme(typography = CustomTypography) {
        val chatViewModel = koinInject<ChatViewModel>()
        val browserViewModel = koinInject<BrowserViewModel>()
        val chatState by chatViewModel.state.collectAsState()
        val browserState by browserViewModel.state.collectAsState()

        val webViewController = rememberPeachWebViewController()
        val shareLauncher = rememberShareLauncher()
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isSubScreenOpen = currentDestination?.route != null && !currentDestination.route!!.contains("BrowserHomeRoute")

        val activeTab = browserState.currentTab
        val isLandingPage = (activeTab == null || activeTab.url.isBlank()) && !isSubScreenOpen
        val isCurrentTabOnWeb = activeTab != null && activeTab.url.isNotBlank() && !isSubScreenOpen
        val canGoBackInWebView = webViewController.canGoBack
        val hasTabBackHistory = browserState.tabBackStack.size > 1

        var addressText by remember { mutableStateOf("") }
        var isAddressBarFocused by remember { mutableStateOf(false) }
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sleepTimerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val shieldSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showChatSheet by remember { mutableStateOf(false) }
        var showMenuSheet by remember { mutableStateOf(false) }
        var showSleepTimerSheet by remember { mutableStateOf(false) }
        var showShieldSheet by remember { mutableStateOf(false) }

        val currentDisplayUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl }
        val activeHost = remember(currentDisplayUrl) {
            try {
                val clean = currentDisplayUrl.trim().lowercase().removePrefix("https://").removePrefix("http://")
                clean.substringBefore("/").substringBefore("?").substringBefore(":").removePrefix("www.")
            } catch (e: Exception) { "" }
        }
        val isCurrentHostException = remember(browserState.siteExceptions, activeHost) {
            activeHost.isNotBlank() && browserState.siteExceptions.any { activeHost == it || activeHost.endsWith(".$it") }
        }

        // Wire media pause action from browser controller
        LaunchedEffect(webViewController) {
            browserViewModel.onPausePlaybackAction = {
                webViewController.pauseMedia()
            }
        }

        // Sync address bar text with active page without clobbering active user typing
        LaunchedEffect(browserState.activeTabId) {
            addressText = if (isLandingPage) "" else (webViewController.currentUrl.ifBlank { browserState.currentUrl })
        }
        LaunchedEffect(browserState.currentUrl, webViewController.currentUrl, isLandingPage) {
            if (!isAddressBarFocused) {
                addressText = if (isLandingPage) {
                    ""
                } else if (webViewController.currentUrl.isNotBlank()) {
                    webViewController.currentUrl
                } else {
                    browserState.currentUrl
                }
            }
        }

        // Multiplatform Back Navigation & Backward Swipe Hierarchy
        val isBackHandled = webViewController.isCustomViewShowing || isSubScreenOpen || showChatSheet || showMenuSheet || showSleepTimerSheet || showShieldSheet || isCurrentTabOnWeb

        CommonBackHandler(enabled = isBackHandled) {
            when {
                // Priority 0: Exit fullscreen custom video player if active
                webViewController.isCustomViewShowing -> {
                    webViewController.hideCustomView()
                }

                // Priority 1: Dismiss active modal bottom sheets
                showChatSheet -> showChatSheet = false
                showMenuSheet -> showMenuSheet = false
                showSleepTimerSheet -> showSleepTimerSheet = false
                showShieldSheet -> showShieldSheet = false

                // Priority 2: Pop sub-screens (Settings, TabsList, Bookmarks, History, Downloads, AiSettings)
                isSubScreenOpen -> navController.popBackStack()

                // Priority 3: Navigate backward through WebView page history within active tab
                isCurrentTabOnWeb && canGoBackInWebView -> {
                    webViewController.goBack()
                }

                // Priority 4: If active tab is at its root web page, return cleanly to Landing page in one gesture
                isCurrentTabOnWeb -> {
                    webViewController.reset()
                    browserViewModel.processIntent(BrowserIntent.GoHome)
                }

                // Otherwise, system back cleanly minimizes/exits app on Landing page
            }
        }

        Scaffold(
            topBar = {
                if (!isSubScreenOpen && !webViewController.isCustomViewShowing) {
                    Surface(
                        color = Color.White,
                        shadowElevation = if (isLandingPage) 0.dp else 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .windowInsetsPadding(WindowInsets.statusBars),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .onFocusChanged { isAddressBarFocused = it.isFocused },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, color = Color(0xFF1E1E2E)),
                            placeholder = { Text("Search or type URL", fontFamily = FontFamily.SansSerif, fontSize = 13.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            showShieldSheet = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isCurrentHostException) "⚡" else if (browserState.isPrivateMode) "🔒" else "🛡️",
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                                    if (webViewController.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF7E57C2)
                                        )
                                    } else {
                                        Text(
                                            text = "🔄",
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable {
                                                webViewController.reload()
                                            }
                                        )
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7E57C2),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFF9F9F9),
                                focusedTextColor = Color(0xFF1E1E2E),
                                unfocusedTextColor = Color(0xFF1E1E2E)
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    val input = addressText.trim()
                                    if (input.isNotBlank()) {
                                        if (input.contains(".") && !input.contains(" ")) {
                                            val url = if (!input.startsWith("http://") && !input.startsWith("https://")) "https://$input" else input
                                            browserViewModel.processIntent(BrowserIntent.LoadUrl(url))
                                            webViewController.loadUrl(url)
                                        } else {
                                            val engine = SearchEngine.fromName(browserState.searchEngine)
                                            val searchUrl = engine.buildSearchUrl(input)
                                            browserViewModel.processIntent(BrowserIntent.SearchQuery(input))
                                            webViewController.loadUrl(searchUrl)
                                        }
                                    }
                                    focusManager.clearFocus()
                                }
                            )
                        )
                    }
                }
            }
            },
            bottomBar = {
                if (!webViewController.isCustomViewShowing) {
                    // Sleek, modern bottom navbar with horizontal drag gesture to switch tabs
                    var bottomBarDrag by remember { mutableFloatStateOf(0f) }

                    Surface(
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(browserState.activeTabId, browserState.tabs) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, delta ->
                                    bottomBarDrag += delta
                                },
                                onDragEnd = {
                                    if (bottomBarDrag > 50f) {
                                        // Swipe right: previous tab
                                        val curIdx = browserState.tabs.indexOfFirst { it.id == browserState.activeTabId }
                                        if (curIdx > 0) {
                                            webViewController.reset()
                                            browserViewModel.processIntent(BrowserIntent.SwitchTab(browserState.tabs[curIdx - 1].id))
                                        }
                                    } else if (bottomBarDrag < -50f) {
                                        // Swipe left: next tab
                                        val curIdx = browserState.tabs.indexOfFirst { it.id == browserState.activeTabId }
                                        if (curIdx != -1 && curIdx < browserState.tabs.size - 1) {
                                            webViewController.reset()
                                            browserViewModel.processIntent(BrowserIntent.SwitchTab(browserState.tabs[curIdx + 1].id))
                                        }
                                    }
                                    bottomBarDrag = 0f
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Home Button: cleanly navigates active tab to Landing page
                        Surface(
                            shape = CircleShape,
                            color = if (isLandingPage && !isSubScreenOpen) Color(0xFFE2D6F5) else Color.Transparent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    webViewController.reset()
                                    browserViewModel.processIntent(BrowserIntent.GoHome)
                                    if (isSubScreenOpen) {
                                        navController.navigate(BrowserHomeRoute) {
                                            popUpTo(BrowserHomeRoute) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⌂", fontSize = 24.sp, color = if (isLandingPage && !isSubScreenOpen) Color(0xFF7E57C2) else Color(0xFF555555))
                            }
                        }

                        // 2. Center Sleek '✨ Ask AI' Action Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF2EDFA),
                            modifier = Modifier
                                .clickable { showChatSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("✨", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ask AI",
                                    color = Color(0xFF7E57C2),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // 3. Tabs Switcher Badge Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7E57C2)),
                            color = Color(0xFFF2EDFA),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable {
                                    if (currentDestination?.route?.contains("TabsListRoute") != true) {
                                        navController.navigate(TabsListRoute)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${browserState.tabs.size}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7E57C2),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }

                        // 4. Three-Dots Menu Button
                        Text(
                            text = "•••",
                            fontSize = 22.sp,
                            color = Color(0xFF7E57C2),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { showMenuSheet = true }
                        )
                    }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(
                        top = if (isSubScreenOpen || webViewController.isCustomViewShowing) 0.dp else paddingValues.calculateTopPadding(),
                        bottom = if (webViewController.isCustomViewShowing) 0.dp else paddingValues.calculateBottomPadding()
                    )
            ) {
                NavHost(
                    navController = navController,
                    startDestination = BrowserHomeRoute
                ) {
                    composable<BrowserHomeRoute> {
                        val currentTab = browserState.currentTab
                        val tabUrl = currentTab?.url ?: ""
                        if (tabUrl.isBlank()) {
                            LandingPage(
                                quickLinks = browserState.quickLinks,
                                onNavigate = { intent ->
                                    browserViewModel.processIntent(intent)
                                }
                            )
                        } else {
                            key(currentTab?.id ?: "tab_1") {
                                PeachWebView(
                                    tabId = currentTab?.id ?: "tab_1",
                                    url = tabUrl,
                                    controller = webViewController,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    composable<TabsListRoute> {
                        TabsListScreen(
                            tabs = browserState.tabs,
                            activeTabId = browserState.activeTabId,
                            onSelectTab = { tabId, _ ->
                                webViewController.reset()
                                browserViewModel.processIntent(BrowserIntent.SwitchTab(tabId))
                                navController.popBackStack()
                            },
                            onNewTab = {
                                webViewController.reset()
                                browserViewModel.processIntent(BrowserIntent.AddTab(""))
                                navController.popBackStack()
                            },
                            onCloseTab = { tabId ->
                                browserViewModel.processIntent(BrowserIntent.CloseTab(tabId))
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            isBackgroundPlayEnabled = browserState.isBackgroundPlayEnabled,
                            isAdBlockEnabled = browserState.isAdBlockEnabled,
                            isPrivateMode = browserState.isPrivateMode,
                            isClearSessionOnExitEnabled = browserState.isClearSessionOnExitEnabled,
                            isTrackingProtectionEnabled = browserState.isTrackingProtectionEnabled,
                            isFarblingEnabled = browserState.isFarblingEnabled,
                            isDeAmpEnabled = browserState.isDeAmpEnabled,
                            searchEngine = browserState.searchEngine,
                            siteExceptions = browserState.siteExceptions,
                            onIntent = { browserViewModel.processIntent(it) },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<HistoryRoute> {
                        HistoryScreen(
                            onOpenUrl = { url ->
                                navController.popBackStack()
                                browserViewModel.processIntent(BrowserIntent.LoadUrl(url))
                                webViewController.loadUrl(url)
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<BookmarksRoute> {
                        BookmarksScreen(
                            currentUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl },
                            currentTitle = webViewController.currentTitle.ifBlank { browserState.activePageTitle },
                            onOpenUrl = { url ->
                                navController.popBackStack()
                                browserViewModel.processIntent(BrowserIntent.LoadUrl(url))
                                webViewController.loadUrl(url)
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<DownloadsRoute> {
                        DownloadsScreen(
                            sessionDownloads = browserState.sessionDownloads,
                            allTimeDownloads = browserState.downloads,
                            onIntent = { browserViewModel.processIntent(it) },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<AiSettingsRoute> {
                        AiSettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                // Sleek Floating Sleep Timer Pill (visible when streaming audio or timer running)
                if (!isSubScreenOpen && !webViewController.isCustomViewShowing && (browserState.isAudioPlaying || browserState.sleepTimer.isActive)) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (browserState.sleepTimer.isActive) Color(0xFFFFECE5) else Color(0xFFEDE7F6),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (browserState.sleepTimer.isActive) Color(0xFFFF7043) else Color(0xFF7E57C2)
                        ),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .clickable { showSleepTimerSheet = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (browserState.sleepTimer.isActive) "🌙" else "🎵",
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (browserState.sleepTimer.isActive) browserState.sleepTimer.formattedRemaining else "Sleep Timer",
                                color = if (browserState.sleepTimer.isActive) Color(0xFFD84315) else Color(0xFF5E35B1),
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (showChatSheet) {
            ModalBottomSheet(
                onDismissRequest = { showChatSheet = false },
                sheetState = bottomSheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                ChatDrawer(
                    state = chatState,
                    activeUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl },
                    activeTitle = webViewController.currentTitle.ifBlank { browserState.activePageTitle },
                    activeContent = webViewController.pageContent.ifBlank { browserState.activePageContent },
                    onIntent = { chatViewModel.processIntent(it) },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
                )
            }
        }

        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                sheetState = menuSheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                MenuOverlay(
                    onDismiss = { showMenuSheet = false },
                    isPrivacyShieldEnabled = browserState.isAdBlockEnabled,
                    onTogglePrivacyShield = { browserViewModel.processIntent(BrowserIntent.ToggleAdBlock(it)) },
                    isPrivateBrowsingEnabled = browserState.isPrivateMode,
                    onTogglePrivateBrowsing = { browserViewModel.processIntent(BrowserIntent.TogglePrivateMode(it)) },
                    onNavigateToHistory = { navController.navigate(HistoryRoute) },
                    onNavigateToBookmarks = { navController.navigate(BookmarksRoute) },
                    onNavigateToDownloads = { navController.navigate(DownloadsRoute) },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) },
                    onNavigateToAiSettings = { navController.navigate(AiSettingsRoute) },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onShareCurrentPage = {
                        val shareUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl }
                        val shareTitle = webViewController.currentTitle.ifBlank { browserState.activePageTitle }
                        shareLauncher(shareUrl, shareTitle)
                    }
                )
            }
        }

        if (showSleepTimerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerSheet = false },
                sheetState = sleepTimerSheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                SleepTimerSheet(
                    state = browserState.sleepTimer,
                    onStartTimer = { minutes, action ->
                        browserViewModel.processIntent(BrowserIntent.StartSleepTimer(minutes, action))
                    },
                    onCancelTimer = {
                        browserViewModel.processIntent(BrowserIntent.CancelSleepTimer)
                    },
                    onDismiss = { showSleepTimerSheet = false }
                )
            }
        }

        if (showShieldSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShieldSheet = false },
                sheetState = shieldSheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                ShieldSheet(
                    browserState = browserState,
                    activeUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl },
                    onDismiss = { showShieldSheet = false },
                    onToggleShields = { host, config ->
                        browserViewModel.processIntent(BrowserIntent.ToggleSiteShield(host, config))
                    },
                    onToggleSiteException = { host, addException ->
                        if (addException) {
                            browserViewModel.processIntent(BrowserIntent.AddSiteException(host))
                        } else {
                            browserViewModel.processIntent(BrowserIntent.RemoveSiteException(host))
                        }
                    },
                    onShredSiteData = { host ->
                        browserViewModel.processIntent(BrowserIntent.ShredSiteData(host))
                    },
                    onOpenSettings = {
                        showShieldSheet = false
                        navController.navigate(SettingsRoute)
                    }
                )
            }
        }
    }
}