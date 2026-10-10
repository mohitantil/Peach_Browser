package com.example.pikachubrowser.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pikachubrowser.domain.model.Tab
import com.example.pikachubrowser.presentation.mvi.BrowserIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListScreen(
    tabs: List<Tab>,
    activeTabId: String,
    onSelectTab: (tabId: String, url: String) -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (tabId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tabs (${tabs.size})",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E2E)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", fontSize = 22.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Button(
                        onClick = onNewTab,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("+ New Tab", color = Color.White, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF6F6F9),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = if (isActive) BorderStroke(2.dp, Color(0xFF7E57C2)) else BorderStroke(1.dp, Color(0xFFE2E2EA)),
                        shadowElevation = if (isActive) 6.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { onSelectTab(tab.id, tab.url) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Tab Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) Color(0xFFF2EDFA) else Color(0xFFFAFAFC))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (tab.url.isBlank()) "🏠" else "🌐",
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.title.ifBlank { if (tab.url.isBlank()) "New Tab" else tab.url },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E1E2E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onCloseTab(tab.id) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("✖", fontSize = 11.sp, color = Color(0xFF757575))
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

                            // Tab Body Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFFFCFCFE))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (tab.url.isBlank()) "Pikachu Start Page" else tab.url.removePrefix("https://").removePrefix("http://"),
                                        fontSize = 11.sp,
                                        color = Color(0xFF888888),
                                        fontFamily = FontFamily.SansSerif,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF7E57C2).copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color(0xFF7E57C2),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.SansSerif,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
