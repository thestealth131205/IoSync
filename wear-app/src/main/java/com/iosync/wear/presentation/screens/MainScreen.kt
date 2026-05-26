package com.iosync.wear.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.iosync.wear.data.model.SmartHomeState
import com.iosync.wear.data.model.StateType
import com.iosync.wear.presentation.theme.NeonYellow
import com.iosync.wear.presentation.theme.StatusOffline
import com.iosync.wear.presentation.theme.StatusOnline
import com.iosync.wear.presentation.theme.WearBackground
import com.iosync.wear.presentation.theme.WearSurfaceVariant
import com.iosync.wear.presentation.viewmodel.WearViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: WearViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.states.isEmpty()) {
            EmptyStateContent(isPhoneConnected = uiState.isPhoneConnected)
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 8.dp,
                    vertical = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "IoSync",
                            style = MaterialTheme.typography.title1.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonYellow
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        ConnectionIndicator(isConnected = uiState.isPhoneConnected)
                        if (uiState.lastSync > 0) {
                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.GERMAN)
                            Text(
                                text = sdf.format(Date(uiState.lastSync)),
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }
                    }
                }

                // Room filter chips
                if (uiState.availableRooms.isNotEmpty()) {
                    item {
                        RoomFilterRow(
                            rooms = uiState.availableRooms,
                            selectedRoom = uiState.selectedRoom,
                            onRoomSelected = viewModel::selectRoom
                        )
                    }
                }

                // State count
                item {
                    Text(
                        text = "${uiState.filteredStates.size} Datenpunkte",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // State items
                items(
                    items = uiState.filteredStates,
                    key = { it.id }
                ) { state ->
                    WearStateChip(state = state)
                }
            }
        }
    }
}

@Composable
fun WearStateChip(state: SmartHomeState) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = {},
        label = {
            Text(
                text = state.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium)
            )
        },
        secondaryLabel = {
            Text(
                text = state.id,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = when (state.type) {
                            StateType.BOOLEAN -> Color(0xFF29B6F6)
                            StateType.NUMBER -> Color(0xFFAB47BC)
                            StateType.STRING -> Color(0xFF26C6DA)
                            StateType.MIXED -> NeonYellow
                        },
                        shape = CircleShape
                    )
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = WearSurfaceVariant
        )
    )
}

@Composable
fun ConnectionIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (isConnected) StatusOnline else StatusOffline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isConnected) "Verbunden" else "Keine Verbindung",
            style = MaterialTheme.typography.caption1,
            color = if (isConnected) StatusOnline else StatusOffline
        )
    }
}

@Composable
fun RoomFilterRow(
    rooms: List<String>,
    selectedRoom: String?,
    onRoomSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        // "All" chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selectedRoom == null) NeonYellow else WearSurfaceVariant
                )
                .clickable { onRoomSelected(null) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Alle",
                style = MaterialTheme.typography.caption1,
                color = if (selectedRoom == null) Color(0xFF1A1A00) else MaterialTheme.colors.onSurface
            )
        }
        Spacer(Modifier.width(4.dp))
        rooms.take(2).forEach { room ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selectedRoom == room) NeonYellow else WearSurfaceVariant
                    )
                    .clickable { onRoomSelected(room) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = room,
                    style = MaterialTheme.typography.caption1,
                    color = if (selectedRoom == room) Color(0xFF1A1A00) else MaterialTheme.colors.onSurface,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
fun EmptyStateContent(isPhoneConnected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "IoSync",
            style = MaterialTheme.typography.title2.copy(
                fontWeight = FontWeight.Bold,
                color = NeonYellow
            )
        )
        Spacer(Modifier.height(8.dp))
        if (!isPhoneConnected) {
            Text(
                text = "Keine Verbindung\nzum Smartphone",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                indicatorColor = NeonYellow,
                trackColor = WearSurfaceVariant,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Warte auf Daten…",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
