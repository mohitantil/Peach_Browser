package com.example.peachbrowser.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.peachbrowser.presentation.mvi.SleepTimerAction
import com.example.peachbrowser.presentation.mvi.SleepTimerState

@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStartTimer: (minutes: Int, action: SleepTimerAction) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMinutes by remember { mutableIntStateOf(30) }
    var selectedAction by remember { mutableStateOf(SleepTimerAction.PAUSE_PLAYBACK) }
    var customInputText by remember { mutableStateOf("") }
    var isCustomActive by remember { mutableStateOf(false) }

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
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFECE5),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🌙", fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Audio Sleep Timer",
                            color = Color(0xFF1E1E2E),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isActive) "Clock-synchronized countdown" else "Stop media when you drift off to sleep",
                            color = Color(0xFF757575),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE2D6F5),
                    modifier = Modifier.size(32.dp).clickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✕", fontSize = 13.sp, color = Color(0xFF555555))
                    }
                }
            }

            if (state.isActive) {
                // Active Countdown View
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFFFCCBC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TIME REMAINING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7043),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.formattedRemaining,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            color = Color(0xFF1E1E2E)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val actionDesc = when (state.action) {
                            SleepTimerAction.PAUSE_PLAYBACK -> "Will pause media playback"
                            SleepTimerAction.CLOSE_TAB -> "Will close the streaming tab"
                            SleepTimerAction.CLOSE_APP -> "Will close the browser app"
                        }
                        Text(
                            text = "⚡ $actionDesc",
                            fontSize = 13.sp,
                            color = Color(0xFF666666),
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick extend presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 15, 30).forEach { addMins ->
                                OutlinedButton(
                                    onClick = {
                                        val remainingMins = (state.remainingSeconds / 60).toInt()
                                        onStartTimer(remainingMins + addMins, state.action)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF7E57C2)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFD1C4E9))
                                ) {
                                    Text("+$addMins m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Cancel button
                        Button(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFEBEE),
                                contentColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Text(
                                "Stop Sleep Timer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                // Setup Timer View
                Text(
                    text = "Select Duration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E2E),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Presets Grid
                val presets = listOf(15, 30, 45, 60)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { mins ->
                        val isSelected = !isCustomActive && selectedMinutes == mins
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable {
                                    isCustomActive = false
                                    selectedMinutes = mins
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF7E57C2) else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF7E57C2) else Color(0xFFE0E0E0)),
                            shadowElevation = if (isSelected) 3.dp else 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$mins m",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF333333)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom time input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(3)
                            customInputText = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                isCustomActive = true
                                selectedMinutes = parsed
                            }
                        },
                        placeholder = { Text("Custom (mins)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7E57C2),
                            unfocusedBorderColor = if (isCustomActive) Color(0xFF7E57C2) else Color(0xFFE0E0E0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    if (isCustomActive && customInputText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEDE7F6),
                            modifier = Modifier.height(50.dp).padding(horizontal = 4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text("✓ Custom $selectedMinutes m", fontSize = 12.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action on Expiration
                Text(
                    text = "When Timer Ends:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E2E),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color(0xFFEFEFEF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ActionChoiceRow(
                            icon = "⏸️",
                            title = "Pause Playback",
                            description = "Pause audio or video, keep tab open",
                            selected = selectedAction == SleepTimerAction.PAUSE_PLAYBACK,
                            onSelect = { selectedAction = SleepTimerAction.PAUSE_PLAYBACK }
                        )
                        HorizontalDivider(color = Color(0xFFF5F5F5))
                        ActionChoiceRow(
                            icon = "✕",
                            title = "Close Active Tab",
                            description = "Close the streaming site tab",
                            selected = selectedAction == SleepTimerAction.CLOSE_TAB,
                            onSelect = { selectedAction = SleepTimerAction.CLOSE_TAB }
                        )
                        HorizontalDivider(color = Color(0xFFF5F5F5))
                        ActionChoiceRow(
                            icon = "🚪",
                            title = "Close Browser App",
                            description = "Fully exit and shutdown browser",
                            selected = selectedAction == SleepTimerAction.CLOSE_APP,
                            onSelect = { selectedAction = SleepTimerAction.CLOSE_APP }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Start Button
                Button(
                    onClick = {
                        onStartTimer(selectedMinutes, selectedAction)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7E57C2),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = "Start Sleep Timer ($selectedMinutes mins)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ActionChoiceRow(
    icon: String,
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E1E2E)
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
        }
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF7E57C2),
                unselectedColor = Color(0xFFBDBDBD)
            )
        )
    }
}
