package com.example.peachbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.peachbrowser.presentation.mvi.BrowserState
import com.example.peachbrowser.presentation.mvi.SiteShieldConfig

@Composable
fun ShieldSheet(
    browserState: BrowserState,
    activeUrl: String,
    onDismiss: () -> Unit,
    onToggleShields: (host: String, config: SiteShieldConfig) -> Unit,
    onToggleSiteException: (host: String, addException: Boolean) -> Unit = { _, _ -> },
    onShredSiteData: (host: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val host = remember(activeUrl) {
        extractHostFromUrl(activeUrl)
    }

    val isSiteException = remember(browserState.siteExceptions, host) {
        host.isNotBlank() && browserState.siteExceptions.any { host == it || host.endsWith(".$it") }
    }

    val currentConfig = remember(browserState.siteShieldOverrides, host, isSiteException) {
        browserState.siteShieldOverrides[host] ?: SiteShieldConfig(
            shieldsUp = !isSiteException && (browserState.isAdBlockEnabled || browserState.isTrackingProtectionEnabled),
            blockTrackersAndAds = !isSiteException && browserState.isAdBlockEnabled,
            blockScripts = false,
            blockFingerprinting = !isSiteException && browserState.isFarblingEnabled,
            blockCookies = false,
            blockedCount = if (!isSiteException && browserState.isAdBlockEnabled) 4 else 0
        )
    }

    var shieldsUp by remember(currentConfig) { mutableStateOf(currentConfig.shieldsUp) }
    var blockTrackers by remember(currentConfig) { mutableStateOf(currentConfig.blockTrackersAndAds) }
    var blockScripts by remember(currentConfig) { mutableStateOf(currentConfig.blockScripts) }
    var blockFingerprinting by remember(currentConfig) { mutableStateOf(currentConfig.blockFingerprinting) }
    var showAdvanced by remember { mutableStateOf(false) }

    fun emitUpdate(
        newShieldsUp: Boolean = shieldsUp,
        newBlockTrackers: Boolean = blockTrackers,
        newBlockScripts: Boolean = blockScripts,
        newBlockFingerprinting: Boolean = blockFingerprinting
    ) {
        val updated = currentConfig.copy(
            shieldsUp = newShieldsUp,
            blockTrackersAndAds = newBlockTrackers,
            blockScripts = newBlockScripts,
            blockFingerprinting = newBlockFingerprinting
        )
        onToggleShields(host, updated)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFFFAF7FD),
        tonalElevation = 16.dp,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD1C4E9))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (shieldsUp) Color(0xFFFFECE5) else Color(0xFFE8EAF6),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isSiteException) "⚡" else if (shieldsUp) "🛡️" else "⚪",
                                fontSize = 20.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (host.isNotBlank()) host else "Active Tab",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E2E)
                        )
                        Text(
                            text = when {
                                isSiteException -> "Compatibility Mode (Site Exception)"
                                shieldsUp -> "Shields up for this site"
                                else -> "Shields down for this site"
                            },
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            color = if (shieldsUp && !isSiteException) Color(0xFFE65100) else Color(0xFF5E35B1)
                        )
                    }
                }

                Switch(
                    checked = shieldsUp && !isSiteException,
                    onCheckedChange = { checked ->
                        if (isSiteException && checked) {
                            onToggleSiteException(host, false)
                        }
                        shieldsUp = checked
                        emitUpdate(newShieldsUp = checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF6F00),
                        uncheckedThumbColor = Color(0xFF9E9E9E),
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                )
            }

            // Quick Site Exception Toggle Button
            if (host.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onToggleSiteException(host, !isSiteException)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSiteException) Color(0xFFEDE7F6) else Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD1C4E9))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSiteException) "In Site Exceptions (Compatibility Mode)" else "Add to Site Exceptions",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5E35B1)
                            )
                            Text(
                                text = if (isSiteException)
                                    "Tap to remove exception and restore full shields"
                                else
                                    "Recommended if login, Turnstile, or chat input fails",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                color = Color(0xFF757575)
                            )
                        }
                        Text(
                            text = if (isSiteException) "REMOVE" else "ADD",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7E57C2)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (shieldsUp && !isSiteException) Color(0xFFFFF3E0) else Color(0xFFF5F5F5),
                border = BorderStroke(1.dp, if (shieldsUp && !isSiteException) Color(0xFFFFCC80) else Color(0xFFE0E0E0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (shieldsUp && !isSiteException) "Trackers, ads & farbling active" else "Compatibility Mode active",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (shieldsUp && !isSiteException) Color(0xFFE65100) else Color(0xFF616161)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (shieldsUp && !isSiteException)
                                "Neutralizing cross-site scripts, trackers, and fingerprinting"
                            else
                                "DOM storage & challenges enabled; session wiped on exit",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = Color(0xFF757575)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (shieldsUp && !isSiteException) Color(0xFFFF6F00) else Color(0xFF7E57C2),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = if (shieldsUp && !isSiteException) "ACTIVE" else "COMPAT",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced Controls",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5E35B1)
                )
                Text(
                    text = if (showAdvanced) "▲" else "▼",
                    fontSize = 12.sp,
                    color = Color(0xFF5E35B1)
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    ShieldRowItem(
                        icon = "🚫",
                        title = "Block trackers & ads",
                        subtitle = "Filters ads and analytics beacons",
                        checked = blockTrackers && shieldsUp && !isSiteException,
                        enabled = shieldsUp && !isSiteException,
                        onCheckedChange = { checked ->
                            blockTrackers = checked
                            emitUpdate(newBlockTrackers = checked)
                        }
                    )

                    ShieldRowItem(
                        icon = "🎭",
                        title = "Block fingerprinting (Farbling)",
                        subtitle = "Adds micro-noise to Canvas, Audio & WebGL",
                        checked = blockFingerprinting && shieldsUp && !isSiteException,
                        enabled = shieldsUp && !isSiteException,
                        onCheckedChange = { checked ->
                            blockFingerprinting = checked
                            emitUpdate(newBlockFingerprinting = checked)
                        }
                    )

                    ShieldRowItem(
                        icon = "⚡",
                        title = "Block scripts (JavaScript)",
                        subtitle = "Disables script execution on this origin",
                        checked = blockScripts && shieldsUp && !isSiteException,
                        enabled = shieldsUp && !isSiteException,
                        onCheckedChange = { checked ->
                            blockScripts = checked
                            emitUpdate(newBlockScripts = checked)
                        }
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔒", fontSize = 16.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Connections upgraded to HTTPS",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E1E2E)
                                    )
                                    Text(
                                        "Enforces secure transport encryption",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        color = Color(0xFF757575)
                                    )
                                }
                            }
                            Text(
                                "Always",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onShredSiteData(host)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFFFCDD2))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("🗑️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Shred site's cookies and local data",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "If this site appears broken, add it to Site Exceptions for Compatibility Mode. All session cookies are still wiped when you exit Peach Browser.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E),
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenSettings
                ) {
                    Text(
                        "🛡️ Global settings",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = Color(0xFF5E35B1)
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Done",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ShieldRowItem(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(icon, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) Color(0xFF1E1E2E) else Color(0xFF9E9E9E)
                    )
                    Text(
                        text = subtitle,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7E57C2),
                    uncheckedThumbColor = Color(0xFF9E9E9E),
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

private fun extractHostFromUrl(url: String): String {
    if (url.isBlank()) return ""
    return try {
        val clean = url.trim().lowercase().removePrefix("https://").removePrefix("http://")
        clean.substringBefore("/").substringBefore("?").substringBefore(":").removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}
