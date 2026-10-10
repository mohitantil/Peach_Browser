package com.example.pikachubrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pikachubrowser.domain.model.LlmMessage
import com.example.pikachubrowser.presentation.mvi.ChatIntent
import com.example.pikachubrowser.presentation.mvi.ChatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDrawer(
    state: ChatState,
    activeUrl: String = "",
    activeTitle: String = "",
    activeContent: String = "",
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    fun send() {
        val trimmed = inputText.trim()
        if (trimmed.isNotBlank()) {
            onIntent(
                ChatIntent.SendMessage(
                    message = trimmed,
                    pageUrl = activeUrl,
                    pageTitle = activeTitle,
                    pageContent = activeContent
                )
            )
            inputText = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFF1E1E2E),
        tonalElevation = 16.dp,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            // Drawer Drag Handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF45475A))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✨ Pikachu AI",
                        color = Color(0xFFCBA6F7),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Clear",
                        color = Color(0xFFA6ADC8),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onIntent(ChatIntent.ClearChat) }
                    )
                }
            }

            // Web Context Pill (if active website content is loaded)
            if (activeUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF313244),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌐", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Analyzing: ${activeTitle.ifBlank { activeUrl }}",
                            color = Color(0xFFBAC2DE),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeContent.isNotBlank()) {
                            Text(
                                text = "• Ready",
                                color = Color(0xFFA6E3A1),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Messages List
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                if (state.isTyping) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFFCBA6F7),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pikachu is thinking...",
                                color = Color(0xFFA6ADC8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }

                if (state.error != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF38BA8).copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = state.error,
                                color = Color(0xFFF38BA8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                items(state.messages.reversed()) { msg ->
                    val isUser = msg.role == LlmMessage.Role.USER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            color = if (isUser) Color(0xFF89B4FA) else Color(0xFF313244)
                        ) {
                            Text(
                                text = msg.content,
                                modifier = Modifier.padding(12.dp),
                                color = if (isUser) Color(0xFF11111B) else Color(0xFFCDD6F4),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (state.messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚡", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (activeUrl.isNotBlank())
                                        "Ask me to summarize this page or find facts!"
                                    else
                                        "How can Pikachu assist your web journey today?",
                                    color = Color(0xFFA6ADC8),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row with Enter KeyboardAction and Sleek Send Icon Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (activeUrl.isNotBlank()) "Ask about this page..." else "Ask Pikachu...",
                            color = Color(0xFF6C7086),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFCBA6F7),
                        unfocusedBorderColor = Color(0xFF45475A),
                        focusedContainerColor = Color(0xFF181825),
                        unfocusedContainerColor = Color(0xFF181825),
                        focusedTextColor = Color(0xFFCDD6F4),
                        unfocusedTextColor = Color(0xFFCDD6F4)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { send() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (inputText.isNotBlank()) Color(0xFFCBA6F7) else Color(0xFF45475A),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "➔",
                                color = if (inputText.isNotBlank()) Color(0xFF1E1E2E) else Color(0xFF6C7086),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
