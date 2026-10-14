package com.example.peachbrowser.ui.screens.bookmarks

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
import com.example.peachbrowser.db.BookmarkEntity
import com.example.peachbrowser.db.PeachDatabase
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    currentUrl: String = "",
    currentTitle: String = "",
    onOpenUrl: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val database = koinInject<PeachDatabase>()
    var bookmarks by remember { mutableStateOf<List<BookmarkEntity>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(currentTitle) }
    var newUrl by remember { mutableStateOf(currentUrl) }

    fun refresh() {
        bookmarks = try {
            database.bookmarksQueries.getAllBookmarks().executeAsList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bookmarks & Saves",
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
                    IconButton(onClick = {
                        newTitle = currentTitle
                        newUrl = currentUrl
                        showAddDialog = true
                    }) {
                        Text("➕", fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No bookmarks saved yet",
                            color = Color(0xFF757575),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                ) {
                    items(bookmarks, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenUrl(item.url) },
                            color = Color.White,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFFF3D6),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⭐", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title.ifBlank { item.url },
                                        color = Color(0xFF1E1E2E),
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.url,
                                        color = Color(0xFF757575),
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        database.bookmarksQueries.deleteBookmark(item.id)
                                        refresh()
                                    }
                                ) {
                                    Text("✖", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = {
                        Text("Add Bookmark", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newUrl,
                                onValueChange = { newUrl = it },
                                label = { Text("URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newUrl.isNotBlank()) {
                                    database.bookmarksQueries.insertBookmark(
                                        url = newUrl,
                                        title = newTitle.ifBlank { newUrl },
                                        createdAt = currentTimeMillis()
                                    )
                                    refresh()
                                }
                                showAddDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
                        ) {
                            Text("Save", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = Color(0xFF757575))
                        }
                    }
                )
            }
        }
    }
}
