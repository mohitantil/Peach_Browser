package com.example.peachbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuOverlay(
    onDismiss: () -> Unit,
    isPrivacyShieldEnabled: Boolean = true,
    onTogglePrivacyShield: (Boolean) -> Unit = {},
    isPrivateBrowsingEnabled: Boolean = false,
    onTogglePrivateBrowsing: (Boolean) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
    onShareCurrentPage: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFFF2EDFA),
        tonalElevation = 16.dp,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peach Browser Menu",
                    color = Color(0xFF1E1E2E),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE2D6F5),
                    modifier = Modifier.size(36.dp).clickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("✖", fontSize = 14.sp) }
                }
            }

            // Card 1: Privacy & Shields
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuOption(
                        icon = "🛡️",
                        title = "Privacy Shield",
                        hasToggle = true,
                        toggleState = isPrivacyShieldEnabled,
                        onToggle = onTogglePrivacyShield
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption(
                        icon = "🔒",
                        title = "Private Browsing",
                        hasToggle = true,
                        toggleState = isPrivateBrowsingEnabled,
                        onToggle = onTogglePrivateBrowsing
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 2: History, Downloads, Bookmarks, Sleep Timer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuOption("🕒", "History", onClick = {
                        onDismiss()
                        onNavigateToHistory()
                    })
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption("⭐", "Bookmarks & Saves", onClick = {
                        onDismiss()
                        onNavigateToBookmarks()
                    })
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption("📥", "Downloads", onClick = {
                        onDismiss()
                        onNavigateToDownloads()
                    })
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption("🌙", "Audio Sleep Timer", onClick = {
                        onDismiss()
                        onOpenSleepTimer()
                    })
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 3: Share, AI & Settings
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuOption("📤", "Share Page", onClick = {
                        onDismiss()
                        onShareCurrentPage()
                    })
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption("✨", "Peach AI Settings", onClick = {
                        onDismiss()
                        onNavigateToAiSettings()
                    })
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    MenuOption("⚙️", "Settings", onClick = {
                        onDismiss()
                        onNavigateToSettings()
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MenuOption(
    icon: String,
    title: String,
    hasToggle: Boolean = false,
    toggleState: Boolean = false,
    onToggle: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 16.dp))
        Text(
            text = title,
            color = Color(0xFF1E1E2E),
            fontSize = 15.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (hasToggle) {
            Switch(
                checked = toggleState,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFF2EDFA),
                    checkedTrackColor = Color(0xFF7E57C2)
                )
            )
        }
    }
}
