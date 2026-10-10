package com.example.pikachubrowser.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onIntent: (BrowserIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Text("◀", fontSize = 18.sp, color = Color(0xFF757575), modifier = Modifier
                        .padding(16.dp)
                        .clickable { onNavigateBack() })
                }
            )
        },
        modifier = modifier.fillMaxSize().background(Color(0xFFF9F9F9))
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Playback",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E57C2),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Video Playback", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E1E2E))
                        Text("Keep playing video/audio from sites like YouTube in the background when the app is minimized.", fontSize = 12.sp, color = Color(0xFF757575))
                    }
                    Switch(
                        checked = isBackgroundPlayEnabled,
                        onCheckedChange = { onIntent(BrowserIntent.ToggleBackgroundPlay(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF2EDFA),
                            checkedTrackColor = Color(0xFF7E57C2)
                        )
                    )
                }
            }
        }
    }
}
