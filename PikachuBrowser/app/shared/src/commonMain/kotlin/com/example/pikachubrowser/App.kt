package com.example.pikachubrowser

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pikachubrowser.presentation.mvi.BrowserIntent
import com.example.pikachubrowser.presentation.viewmodel.BrowserViewModel
import com.example.pikachubrowser.presentation.viewmodel.ChatViewModel
import com.example.pikachubrowser.ui.ChatDrawer
import com.example.pikachubrowser.ui.CommonBackHandler
import com.example.pikachubrowser.ui.LandingPage
import com.example.pikachubrowser.ui.MenuOverlay
import com.example.pikachubrowser.ui.screens.ai.AiSettingsScreen
import com.example.pikachubrowser.ui.screens.bookmarks.BookmarksScreen
import com.example.pikachubrowser.ui.screens.downloads.DownloadsScreen
import com.example.pikachubrowser.ui.screens.history.HistoryScreen
import com.example.pikachubrowser.ui.screens.settings.SettingsScreen
import com.example.pikachubrowser.ui.screens.tabs.TabsListScreen
import com.example.pikachubrowser.util.SearchEngine
import com.example.pikachubrowser.util.rememberShareLauncher
import com.example.pikachubrowser.webview.PikachuWebView
import com.example.pikachubrowser.webview.rememberPikachuWebViewController
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

        val webViewController = rememberPikachuWebViewController()
        val shareLauncher = rememberShareLauncher()

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
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showChatSheet by remember { mutableStateOf(false) }
        var showMenuSheet by remember { mutableStateOf(false) }

        // Sync address bar text with active page
        LaunchedEffect(browserState.activeTabId, browserState.currentUrl, webViewController.currentUrl) {
            val url = if (webViewController.currentUrl.isNotBlank()) {
                webViewController.currentUrl
            } else {
                browserState.currentUrl
            }
            addressText = url
        }

        // Brave-Grade Multiplatform Back Navigation & Backward Swipe Hierarchy
        val isBackHandled = isSubScreenOpen || showChatSheet || showMenuSheet || isCurrentTabOnWeb || hasTabBackHistory

        CommonBackHandler(enabled = isBackHandled) {
            when {
                // Priority 1: Dismiss active modal bottom sheets
                showChatSheet -> showChatSheet = false
                showMenuSheet -> showMenuSheet = false

                // Priority 2: Pop sub-screens (Settings, TabsList, Bookmarks, History, Downloads, AiSettings)
                isSubScreenOpen -> navController.popBackStack()

                // Priority 3: Navigate backward through WebView page history within active tab
                isCurrentTabOnWeb && canGoBackInWebView -> {
                    webViewController.goBack()
                }

                // Priority 4: If active tab is at its first web page and was opened from Landing, return to Landing page
                isCurrentTabOnWeb && activeTab.initialUrl.isBlank() -> {
                    browserViewModel.processIntent(BrowserIntent.GoHome)
                }

                // Priority 5: If active tab can't go back further, switch to previous tab in MRU tabBackStack
                hasTabBackHistory -> {
                    browserViewModel.processIntent(BrowserIntent.PopTab)
                }

                // Priority 6: If on a web page with no tab history, transition back to Landing page
                isCurrentTabOnWeb -> {
                    browserViewModel.processIntent(BrowserIntent.GoHome)
                }

                // Otherwise, system back minimizes/exits app cleanly without crashing
            }
        }

        // Nested Scroll Connection for Auto-Hiding Toolbars
        val density = LocalDensity.current
        val topBarHeight = 56.dp
        val bottomBarHeight = 54.dp
        val topBarHeightPx = with(density) { topBarHeight.roundToPx().toFloat() }
        val bottomBarHeightPx = with(density) { bottomBarHeight.roundToPx().toFloat() }

        var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }
        var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

        val nestedScrollConnection = remember(isLandingPage) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (isLandingPage) return Offset.Zero
                    val delta = available.y
                    val newTopOffset = topBarOffsetHeightPx + delta
                    topBarOffsetHeightPx = newTopOffset.coerceIn(-topBarHeightPx, 0f)

                    val newBottomOffset = bottomBarOffsetHeightPx - delta
                    bottomBarOffsetHeightPx = newBottomOffset.coerceIn(0f, bottomBarHeightPx)

                    return Offset.Zero
                }
            }
        }

        LaunchedEffect(isLandingPage) {
            if (isLandingPage) {
                topBarOffsetHeightPx = 0f
                bottomBarOffsetHeightPx = 0f
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = if (isLandingPage) 0.dp else 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = topBarOffsetHeightPx.roundToInt()) }
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
                            modifier = Modifier.weight(1f).height(46.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, color = Color(0xFF1E1E2E)),
                            placeholder = { Text("Search or type URL", fontFamily = FontFamily.SansSerif, fontSize = 13.sp) },
                            leadingIcon = {
                                Text(
                                    text = if (browserState.isPrivateMode) "🔒" else "🛡️",
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
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
                                }
                            )
                        )
                    }
                }
            },
            bottomBar = {
                // Sleek, modern bottom navbar with horizontal drag gesture to switch tabs
                var bottomBarDrag by remember { mutableFloatStateOf(0f) }

                Surface(
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = bottomBarOffsetHeightPx.roundToInt()) }
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
                                            browserViewModel.processIntent(BrowserIntent.SwitchTab(browserState.tabs[curIdx - 1].id))
                                        }
                                    } else if (bottomBarDrag < -50f) {
                                        // Swipe left: next tab
                                        val curIdx = browserState.tabs.indexOfFirst { it.id == browserState.activeTabId }
                                        if (curIdx != -1 && curIdx < browserState.tabs.size - 1) {
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
                            color = if (isLandingPage) Color(0xFFE2D6F5) else Color.Transparent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    browserViewModel.processIntent(BrowserIntent.GoHome)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⌂", fontSize = 24.sp, color = if (isLandingPage) Color(0xFF7E57C2) else Color(0xFF555555))
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
                                .clickable { navController.navigate(TabsListRoute) }
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
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(paddingValues)
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
                                PikachuWebView(
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
                                browserViewModel.processIntent(BrowserIntent.SwitchTab(tabId))
                                navController.popBackStack()
                            },
                            onNewTab = {
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
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<AiSettingsRoute> {
                        AiSettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
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
                    onShareCurrentPage = {
                        val shareUrl = webViewController.currentUrl.ifBlank { browserState.currentUrl }
                        val shareTitle = webViewController.currentTitle.ifBlank { browserState.activePageTitle }
                        shareLauncher(shareUrl, shareTitle)
                    }
                )
            }
        }
    }
}