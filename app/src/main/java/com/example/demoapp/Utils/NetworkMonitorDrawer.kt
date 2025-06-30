package com.example.demoapp.UI

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.network.ui.MainViewModel
import com.example.network.util.loadFriendlyName
import kotlinx.coroutines.launch

@Composable
fun NetworkMonitorDrawer(
    viewModel: MainViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val ipAddress = viewModel.getLocalIpAddress()

    val deviceLogs by viewModel.deviceLogFeed.collectAsState()
    val workLogs by viewModel.workLogFeed.collectAsState()
    val debugLogs = viewModel.debugLogs

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Worker Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    "Worker Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(deviceLogs.entries.toList()) { entry ->
                        WorkerStatusItem(entry.key, entry.value)
                    }
                }
            }
        }

        // Work Progress Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    "Computation Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(workLogs.entries.toList()) { entry ->
                        WorkProgressItem(entry.key, entry.value)
                    }
                }
            }
        }

        // Debug Logs Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    "Debug Logs",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                
                val listState = rememberLazyListState()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                ) {
                    items(debugLogs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .fillMaxWidth()
                        )
                    }
                }
                
                // Auto-scroll to bottom when new logs are added
                LaunchedEffect(debugLogs.size) {
                    if (debugLogs.isNotEmpty()) {
                        scope.launch {
                            listState.animateScrollToItem(debugLogs.size - 1)
                        }
                    }
                }
            }
        }

        // Device Info Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Device: ${context.loadFriendlyName()} | IP: $ipAddress",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WorkerStatusItem(friendlyName: String, status: String) {
    val backgroundColor = when {
        status.contains("Online") -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        status.contains("Offline") -> Color(0xFFF44336).copy(alpha = 0.1f)
        else -> Color(0xFFFFA000).copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, MaterialTheme.shapes.small)
            .border(1.dp, backgroundColor.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            friendlyName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
fun WorkProgressItem(friendlyName: String, status: String) {
    val backgroundColor = when {
        status.contains("✔️") -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        status.contains("⚠️") -> Color(0xFFF44336).copy(alpha = 0.1f)
        status.contains("➡️") -> Color(0xFF2196F3).copy(alpha = 0.1f)
        else -> Color(0xFF2D2D2D)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(backgroundColor, MaterialTheme.shapes.small)
            .border(1.dp, backgroundColor.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            friendlyName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
} 