package com.example.pikachubrowser.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pikachubrowser.domain.model.Tab
import com.example.pikachubrowser.presentation.mvi.BrowserIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListScreen(
    tabs: List<Tab>,
    activeTabId: String,
    onIntent: (BrowserIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Tabs (${tabs.size})", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Text("◀", fontSize = 18.sp, color = Color(0xFF757575), modifier = Modifier
                        .padding(16.dp)
                        .clickable { onNavigateBack() })
                },
                actions = {
                    Text("➕ New", fontSize = 16.sp, color = Color(0xFF7E57C2), modifier = Modifier
                        .padding(16.dp)
                        .clickable {
                            onIntent(BrowserIntent.AddTab(""))
                            onNavigateBack()
                        })
                }
            )
        },
        modifier = modifier.fillMaxSize().background(Color(0xFFF9F9F9))
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tabs) { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Color(0xFFE2D6F5) else Color.White,
                    shadowElevation = if (isActive) 8.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onIntent(BrowserIntent.SwitchTab(tab.id))
                        onNavigateBack()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (tab.url.isEmpty()) "New Tab" else tab.url.take(30) + "...",
                                fontSize = 16.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF1E1E2E)
                            )
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0xFFFFE5E5),
                            modifier = Modifier.size(32.dp).clickable { onIntent(BrowserIntent.CloseTab(tab.id)) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✖", fontSize = 12.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
