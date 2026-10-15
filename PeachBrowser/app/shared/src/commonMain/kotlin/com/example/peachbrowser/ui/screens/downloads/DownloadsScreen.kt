package com.example.peachbrowser.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.peachbrowser.currentTimeMillis
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.DownloadItem
import com.example.peachbrowser.presentation.mvi.THIRTY_DAYS_MILLIS
import com.example.peachbrowser.util.openDownloadedFile
import com.example.peachbrowser.util.openDownloadsFolder
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    sessionDownloads: List<DownloadItem>,
    allTimeDownloads: List<DownloadItem>,
    onIntent: (BrowserIntent) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Split Overview (Both), 1 = Current Session, 2 = Past 30 Days
    var nowMillis by remember { mutableLongStateOf(currentTimeMillis()) }

    // Trigger immediate & periodic real-time storage sync while DownloadsScreen is open
    LaunchedEffect(Unit) {
        onIntent(BrowserIntent.SyncDownloadsWithStorage)
        while (true) {
            delay(1000L)
            nowMillis = currentTimeMillis()
            onIntent(BrowserIntent.SyncDownloadsWithStorage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Downloads",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFF1E1E2E)
                        )
                        Text(
                            "Real-time storage sync • 30-day history",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = Color(0xFF7E57C2)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", fontSize = 22.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF2EDFA),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { openDownloadsFolder() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📂", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Files",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF7E57C2)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter Pills: All Split View | Current Session | Past 30 Days
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "Split View" to 0,
                    "Current Session (${sessionDownloads.size})" to 1,
                    "Past 30 Days (${allTimeDownloads.size})" to 2
                )
                tabs.forEach { (label, idx) ->
                    val isSelected = selectedTab == idx
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF7E57C2) else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF7E57C2) else Color(0xFFE0E0E0)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedTab = idx }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else Color(0xFF555555),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // SECTION 1: CURRENT SESSION DOWNLOADS
                if (selectedTab == 0 || selectedTab == 1) {
                    item(key = "header_session") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CURRENT SESSION DOWNLOADS (${sessionDownloads.size})",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF7E57C2)
                            )
                            if (sessionDownloads.isNotEmpty()) {
                                Text(
                                    text = "Clear Session List",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF5E35B1),
                                    modifier = Modifier.clickable {
                                        onIntent(BrowserIntent.ClearSessionDownloads)
                                    }
                                )
                            }
                        }
                    }

                    if (sessionDownloads.isEmpty()) {
                        item(key = "empty_session") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "No downloads in this active session yet.",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        color = Color(0xFF757575)
                                    )
                                }
                            }
                        }
                    } else {
                        items(sessionDownloads, key = { "session_${it.id}_${it.timestamp}" }) { item ->
                            DownloadCardRow(
                                item = item,
                                nowMillis = nowMillis,
                                onOpen = { openDownloadedFile(item) },
                                onDeleteFromPhone = {
                                    onIntent(BrowserIntent.RemoveDownloadItem(item.id, deleteFromStorage = true))
                                }
                            )
                        }
                    }
                }

                // SECTION 2: ALL-TIME DOWNLOADS (PAST 30 DAYS)
                if (selectedTab == 0 || selectedTab == 2) {
                    item(key = "header_all_time") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ALL DOWNLOADS • PAST 30 DAYS (${allTimeDownloads.size})",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF7E57C2)
                            )
                            Text(
                                text = "Synced with phone storage",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (allTimeDownloads.isEmpty()) {
                        item(key = "empty_all_time") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("📥", fontSize = 36.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No downloads on phone storage from the past 30 days",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        color = Color(0xFF757575)
                                    )
                                }
                            }
                        }
                    } else {
                        items(allTimeDownloads, key = { "all_${it.id}_${it.timestamp}" }) { item ->
                            DownloadCardRow(
                                item = item,
                                nowMillis = nowMillis,
                                onOpen = { openDownloadedFile(item) },
                                onDeleteFromPhone = {
                                    onIntent(BrowserIntent.RemoveDownloadItem(item.id, deleteFromStorage = true))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCardRow(
    item: DownloadItem,
    nowMillis: Long,
    onOpen: () -> Unit,
    onDeleteFromPhone: () -> Unit
) {
    val isImage = remember(item.fileName, item.mimeType) {
        val lower = item.fileName.lowercase()
        item.mimeType.startsWith("image/") ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
    }

    val badgeEmoji = when {
        item.isFailed -> "⚠️"
        !item.isCompleted -> "⏬"
        isImage -> "🖼️"
        item.fileName.lowercase().endsWith(".pdf") -> "📄"
        item.fileName.lowercase().let { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".m4a") } -> "🎵"
        item.fileName.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm") } -> "🎬"
        else -> "📦"
    }

    val ageAndExpiryText = remember(item.timestamp, nowMillis) {
        formatDownloadClockAge(item.timestamp, nowMillis)
    }

    val sizeText = remember(item.bytesDownloaded, item.totalBytes, item.isCompleted) {
        when {
            item.isCompleted && item.totalBytes > 0L -> formatFileSize(item.totalBytes)
            item.isCompleted && item.bytesDownloaded > 0L -> formatFileSize(item.bytesDownloaded)
            item.totalBytes > 0L -> "${formatFileSize(item.bytesDownloaded)} / ${formatFileSize(item.totalBytes)}"
            item.bytesDownloaded > 0L -> formatFileSize(item.bytesDownloaded)
            else -> ""
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (item.isCompleted) Color(0xFFE8F5E9) else Color(0xFFF2EDFA),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(badgeEmoji, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E1E2E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                val statusLine = when {
                    item.isFailed -> "Download failed"
                    item.isCompleted -> buildString {
                        append("Completed (100%)")
                        if (sizeText.isNotBlank()) append(" • $sizeText")
                    }
                    else -> buildString {
                        append("Downloading ${item.progress}%")
                        if (sizeText.isNotBlank()) append(" • $sizeText")
                    }
                }

                Text(
                    text = statusLine,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = when {
                        item.isFailed -> Color(0xFFD32F2F)
                        item.isCompleted -> Color(0xFF2E7D32)
                        else -> Color(0xFF7E57C2)
                    }
                )

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = ageAndExpiryText,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = Color(0xFF757575)
                )

                if (!item.isCompleted && !item.isFailed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF7E57C2),
                        trackColor = Color(0xFFEDE7F6)
                    )
                }
            }

            IconButton(onClick = onDeleteFromPhone) {
                Text("🗑️", fontSize = 16.sp)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.toInt()} KB"
    val mb = kb / 1024.0
    val rounded = (mb * 10.0).toInt() / 10.0
    return "$rounded MB"
}

private fun formatDownloadClockAge(timestamp: Long, nowMillis: Long): String {
    if (timestamp <= 0L) return "Kept for 30 days"
    val elapsedMs = (nowMillis - timestamp).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMs / (60L * 1000L)
    val elapsedHours = elapsedMinutes / 60L
    val elapsedDays = elapsedHours / 24L
    val remainingDays = ((THIRTY_DAYS_MILLIS - elapsedMs) / (24L * 60L * 60L * 1000L)).coerceIn(0L, 30L)

    val agePart = when {
        elapsedMinutes < 1L -> "Just now"
        elapsedMinutes < 60L -> "${elapsedMinutes}m ago"
        elapsedHours < 24L -> "${elapsedHours}h ago"
        elapsedDays == 1L -> "Yesterday"
        else -> "${elapsedDays}d ago"
    }
    return "$agePart • Auto-clears in ${remainingDays}d"
}
