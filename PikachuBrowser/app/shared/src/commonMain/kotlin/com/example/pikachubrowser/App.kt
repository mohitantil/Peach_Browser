package com.example.pikachubrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.navigation.toRoute
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
import com.example.pikachubrowser.util.rememberShareLauncher
import com.example.pikachubrowser.webview.PikachuWebView
import com.example.pikachubrowser.webview.rememberPikachuWebViewController
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Serializable
object LandingRoute

@Serializable
data class BrowserRoute(val url: String)

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
        val isLandingPage = currentDestination?.route?.contains("LandingRoute") ?: true
        val isBrowserPage = currentDestination?.route?.contains("BrowserRoute") ?: false

        var addressText by remember { mutableStateOf("") }
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showChatSheet by remember { mutableStateOf(false) }
        var showMenuSheet by remember { mutableStateOf(false) }

        // Sync address bar text with active page
        LaunchedEffect(navBackStackEntry, webViewController.currentUrl) {
            val route = currentDestination?.route
            if (route != null && route.contains("BrowserRoute")) {
                if (webViewController.currentUrl.isNotBlank()) {
                    addressText = webViewController.currentUrl
                } else {
                    val urlIndex = route.indexOf("url=")
                    if (urlIndex != -1) {
                        val urlEnd = route.indexOf("}", urlIndex)
                        if (urlEnd != -1) {
                            addressText = route.substring(urlIndex + 4, urlEnd)
                        }
                    }
                }
            } else {
                addressText = ""
            }
        }

        // Hardware Back Button Handling with clean tab backstack tracking
        CommonBackHandler(enabled = true) {
            if (isBrowserPage) {
                if (webViewController.canGoBack) {
                    webViewController.goBack()
                } else {
                    // Navigate to Home Landing page for this tab
                    browserViewModel.processIntent(BrowserIntent.GoHome)
                    navController.navigate(LandingRoute) {
                        popUpTo(LandingRoute) { inclusive = true }
                    }
                }
            } else if (!isLandingPage) {
                navController.popBackStack()
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
                                            navController.navigate(BrowserRoute(url))
                                        } else {
                                            val searchUrl = "https://html.duckduckgo.com/html/?q=$input"
                                            browserViewModel.processIntent(BrowserIntent.SearchQuery(input))
                                            navController.navigate(BrowserRoute(searchUrl))
                                        }
                                    }
                                }
                            )
                        )
                    }
                }
            },
            bottomBar = {
                // Sleek, modern bottom navbar without useless arrow buttons
                Surface(
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = bottomBarOffsetHeightPx.roundToInt()) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Home Button
                        Surface(
                            shape = CircleShape,
                            color = if (isLandingPage) Color(0xFFE2D6F5) else Color.Transparent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    browserViewModel.processIntent(BrowserIntent.GoHome)
                                    navController.navigate(LandingRoute) {
                                        popUpTo(LandingRoute) { inclusive = true }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⌂", fontSize = 24.sp, color = if (isLandingPage) Color(0xFF7E57C2) else Color(0xFF555555))
                            }
                        }

                        // 2. Center Sleek '✨ Pikachu AI' Action Button
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
                    startDestination = LandingRoute
                ) {
                    composable<LandingRoute> {
                        LandingPage(
                            quickLinks = browserState.quickLinks,
                            onNavigate = { intent ->
                                browserViewModel.processIntent(intent)
                                if (intent is BrowserIntent.LoadUrl) {
                                    var u = intent.url
                                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
                                    navController.navigate(BrowserRoute(u))
                                } else if (intent is BrowserIntent.SearchQuery) {
                                    val searchUrl = "https://html.duckduckgo.com/html/?q=${intent.query}"
                                    navController.navigate(BrowserRoute(searchUrl))
                                }
                            }
                        )
                    }
                    composable<BrowserRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<BrowserRoute>()
                        PikachuWebView(
                            url = route.url,
                            controller = webViewController,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable<TabsListRoute> {
                        TabsListScreen(
                            tabs = browserState.tabs,
                            activeTabId = browserState.activeTabId,
                            onSelectTab = { tabId, url ->
                                browserViewModel.processIntent(BrowserIntent.SwitchTab(tabId))
                                if (url.isNotBlank()) {
                                    navController.navigate(BrowserRoute(url)) {
                                        popUpTo(LandingRoute) { inclusive = false }
                                    }
                                } else {
                                    navController.navigate(LandingRoute) {
                                        popUpTo(LandingRoute) { inclusive = true }
                                    }
                                }
                            },
                            onNewTab = {
                                browserViewModel.processIntent(BrowserIntent.AddTab(""))
                                navController.navigate(LandingRoute) {
                                    popUpTo(LandingRoute) { inclusive = true }
                                }
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
                                navController.navigate(BrowserRoute(url))
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
                                navController.navigate(BrowserRoute(url))
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