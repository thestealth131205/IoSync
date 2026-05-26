package com.iosync.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.model.StateType
import com.iosync.app.data.network.WebSocketStatus
import com.iosync.app.ui.theme.ColorBoolean
import com.iosync.app.ui.theme.ColorNumber
import com.iosync.app.ui.theme.ColorString
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.ui.theme.StatusError
import com.iosync.app.ui.theme.StatusOffline
import com.iosync.app.ui.theme.StatusOnline
import com.iosync.app.ui.theme.SurfaceElevated
import com.iosync.app.ui.theme.SurfaceMid
import com.iosync.app.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onStateClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    onToggleConnection: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var searchActive by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "IoSync",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonYellow
                        )
                    )
                },
                actions = {
                    ConnectionStatusButton(
                        status = uiState.connectionStatus,
                        onClick = onToggleConnection
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Aktualisieren",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Einstellungen",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.searchQuery,
                        onQueryChange = {},
                        onSearch = {},
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        placeholder = { Text("Datenpunkt suchen…") }
                    )
                },
                expanded = searchActive,
                onExpandedChange = { searchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {}

            // Room filter chips
            if (uiState.availableRooms.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedRoom == null,
                            onClick = {},
                            label = { Text("Alle") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonYellow,
                                selectedLabelColor = Color(0xFF1A1A00)
                            )
                        )
                    }
                    items(uiState.availableRooms) { room ->
                        FilterChip(
                            selected = uiState.selectedRoom == room,
                            onClick = {},
                            label = { Text(room) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonYellow,
                                selectedLabelColor = Color(0xFF1A1A00)
                            )
                        )
                    }
                }
            }

            // State summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.filteredStates.size} Datenpunkte",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${uiState.host}:${uiState.port}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Loading indicator
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = NeonYellow,
                        strokeWidth = 2.dp
                    )
                }
            }

            // State list
            if (!uiState.isLoading) {
                if (uiState.filteredStates.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Keine Datenpunkte gefunden",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Verbindung prüfen oder Suche anpassen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.filteredStates,
                            key = { it.id }
                        ) { state ->
                            SmartHomeStateCard(
                                state = state,
                                onClick = { onStateClick(state.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartHomeStateCard(
    state: SmartHomeState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceMid)
            .border(
                width = 1.dp,
                color = if (state.isOnline) SurfaceElevated else StatusError.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Type indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = when (state.type) {
                            StateType.BOOLEAN -> ColorBoolean
                            StateType.NUMBER -> ColorNumber
                            StateType.STRING -> ColorString
                            StateType.MIXED -> NeonYellow
                        },
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = state.displayValue,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = NeonYellow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.room != null) {
                Text(
                    text = state.room,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusButton(
    status: WebSocketStatus,
    onClick: () -> Unit
) {
    val (icon, tint) = when (status) {
        WebSocketStatus.CONNECTED -> Icons.Default.Wifi to StatusOnline
        WebSocketStatus.CONNECTING,
        WebSocketStatus.RECONNECTING -> Icons.Default.Wifi to NeonYellow
        WebSocketStatus.DISCONNECTED,
        WebSocketStatus.FAILED -> Icons.Default.SignalWifiOff to StatusOffline
    }
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = "Verbindungsstatus", tint = tint)
    }
}
