package com.example.pikachubrowser.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pikachubrowser.presentation.mvi.BrowserIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isBackgroundPlayEnabled: Boolean,
    isAdBlockEnabled: Boolean = true,
    isPrivateMode: Boolean = false,
    isClearSessionOnExitEnabled: Boolean = true,
    searchEngine: String = "DuckDuckGo",
    onIntent: (BrowserIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDataClearedSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = Color(0xFF1E1E2E)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", fontSize = 22.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFAFAFA),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Privacy & Ephemeral Browsing
            Text(
                text = "Privacy & Data Protection",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E57C2),
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsToggleRow(
                        title = "Clear Session on Exit",
                        subtitle = "Wipes cookies, storage, and cache so sites (like YouTube) always start fresh without pre-built tracking feeds.",
                        checked = isClearSessionOnExitEnabled,
                        onCheckedChange = { onIntent(BrowserIntent.ToggleClearSessionOnExit(it)) }
                    )
                    HorizontalDivider(color = Color(0xFFF2F2F2))
                    SettingsToggleRow(
                        title = "Privacy Shield",
                        subtitle = "Blocks intrusive ads, analytics, and third-party trackers.",
                        checked = isAdBlockEnabled,
                        onCheckedChange = { onIntent(BrowserIntent.ToggleAdBlock(it)) }
                    )
                    HorizontalDivider(color = Color(0xFFF2F2F2))
                    SettingsToggleRow(
                        title = "Permanent Incognito Mode",
                        subtitle = "Disables history recording and local disk storage completely.",
                        checked = isPrivateMode,
                        onCheckedChange = { onIntent(BrowserIntent.TogglePrivateMode(it)) }
                    )
                    HorizontalDivider(color = Color(0xFFF2F2F2))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onIntent(BrowserIntent.ClearAllBrowsingData)
                                showDataClearedSnackbar = true
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Browsing Data Now", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD32F2F), fontFamily = FontFamily.SansSerif)
                            Text("Immediately wipes all active cookies, cache, and history.", fontSize = 12.sp, color = Color(0xFF757575), fontFamily = FontFamily.SansSerif)
                        }
                        Text("🗑️", fontSize = 18.sp)
                    }
                }
            }

            if (showDataClearedSnackbar) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ All cookies, session data, and cache cleared!",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Playback
            Text(
                text = "Media Playback",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E57C2),
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsToggleRow(
                    title = "Background Video Playback",
                    subtitle = "Keep playing audio and video from YouTube and media sites when switching apps or locking screen.",
                    checked = isBackgroundPlayEnabled,
                    onCheckedChange = { onIntent(BrowserIntent.ToggleBackgroundPlay(it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Search Engine
            Text(
                text = "Default Search Engine",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E57C2),
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val engines = listOf("DuckDuckGo", "Brave", "Google")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        engines.forEach { engine ->
                            val isSelected = searchEngine.equals(engine, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onIntent(BrowserIntent.SetSearchEngine(engine)) },
                                label = { Text(engine, fontFamily = FontFamily.SansSerif, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE2D6F5),
                                    selectedLabelColor = Color(0xFF7E57C2)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E1E2E), fontFamily = FontFamily.SansSerif)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF757575), fontFamily = FontFamily.SansSerif)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFF2EDFA),
                checkedTrackColor = Color(0xFF7E57C2)
            )
        )
    }
}
