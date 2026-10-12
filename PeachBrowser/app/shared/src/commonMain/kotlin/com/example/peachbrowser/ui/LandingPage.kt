package com.example.peachbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.peachbrowser.presentation.mvi.BrowserIntent
import com.example.peachbrowser.presentation.mvi.QuickLink
import org.jetbrains.compose.resources.painterResource
import peachbrowser.app.shared.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPage(
    quickLinks: List<QuickLink> = listOf(
        QuickLink("DuckDuckGo", "duckduckgo.com", "🔍"),
        QuickLink("YouTube", "youtube.com", "▶️"),
        QuickLink("Wikipedia", "wikipedia.org", "🌐"),
        QuickLink("Reddit", "reddit.com", "💬")
    ),
    onNavigate: (BrowserIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddSiteDialog by remember { mutableStateOf(false) }
    var newSiteName by remember { mutableStateOf("") }
    var newSiteUrl by remember { mutableStateOf("") }

    val pastelColors = remember {
        listOf(
            Color(0xFFFFF0E5),
            Color(0xFFFFE5E5),
            Color(0xFFE8F5E9),
            Color(0xFFEDE7F6),
            Color(0xFFE0F7FA),
            Color(0xFFFFF8E1)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Logo / Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 36.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.peach_hero),
                    contentDescription = "Peach Mascot",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Peach Browser",
                        color = Color(0xFF1E1E2E),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "AI Agentic Web Engine",
                        color = Color(0xFF7E57C2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            // Center Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                placeholder = {
                    Text("Search the web or type a URL", color = Color(0xFF757575), fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
                },
                leadingIcon = {
                    Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7E57C2),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF1E1E2E),
                    unfocusedTextColor = Color(0xFF1E1E2E)
                ),
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        val input = searchQuery.trim()
                        if (input.isNotEmpty()) {
                            if (input.contains(".") && !input.contains(" ")) {
                                onNavigate(BrowserIntent.LoadUrl(input))
                            } else {
                                onNavigate(BrowserIntent.SearchQuery(input))
                            }
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Quick Links Grid Section
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Access",
                    color = Color(0xFF1E1E2E),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "+ Add Site",
                    color = Color(0xFF7E57C2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.clickable {
                        newSiteName = ""
                        newSiteUrl = ""
                        showAddSiteDialog = true
                    }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickLinks) { link ->
                    val colorIndex = (link.title.hashCode() and 0x7FFFFFFF) % pastelColors.size
                    QuickLinkTile(
                        title = link.title,
                        emoji = link.iconEmoji,
                        bgColor = pastelColors[colorIndex],
                        onClick = { onNavigate(BrowserIntent.LoadUrl(link.url)) }
                    )
                }

                item {
                    QuickLinkTile(
                        title = "Add Site",
                        emoji = "➕",
                        bgColor = Color(0xFFF2EDFA),
                        onClick = {
                            newSiteName = ""
                            newSiteUrl = ""
                            showAddSiteDialog = true
                        }
                    )
                }
            }
        }

        if (showAddSiteDialog) {
            AlertDialog(
                onDismissRequest = { showAddSiteDialog = false },
                title = {
                    Text("Add Quick Link", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newSiteName,
                            onValueChange = { newSiteName = it },
                            label = { Text("Site Name") },
                            placeholder = { Text("e.g. GitHub") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newSiteUrl,
                            onValueChange = { newSiteUrl = it },
                            label = { Text("Site URL") },
                            placeholder = { Text("e.g. github.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newSiteUrl.isNotBlank()) {
                                onNavigate(
                                    BrowserIntent.AddQuickLink(
                                        title = newSiteName.ifBlank { newSiteUrl },
                                        url = newSiteUrl
                                    )
                                )
                            }
                            showAddSiteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
                    ) {
                        Text("Add", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSiteDialog = false }) {
                        Text("Cancel", color = Color(0xFF757575))
                    }
                }
            )
        }
    }
}

@Composable
fun QuickLinkTile(
    title: String,
    emoji: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 26.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = Color(0xFF1E1E2E), // High contrast dark charcoal
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
