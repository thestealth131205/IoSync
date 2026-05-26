package com.iosync.wear.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iosync.wear.data.model.SmartHomeState
import com.iosync.wear.data.repository.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class WearUiState(
    val states: List<SmartHomeState> = emptyList(),
    val filteredStates: List<SmartHomeState> = emptyList(),
    val selectedRoom: String? = null,
    val availableRooms: List<String> = emptyList(),
    val isPhoneConnected: Boolean = false,
    val lastSync: Long = 0L
)

@HiltViewModel
class WearViewModel @Inject constructor(
    private val wearRepository: WearRepository
) : ViewModel() {

    private val _selectedRoom = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WearUiState> = combine(
        wearRepository.states,
        wearRepository.isPhoneConnected,
        wearRepository.lastSync,
        _selectedRoom
    ) { states, connected, lastSync, room ->
        val rooms = states.mapNotNull { it.room }.distinct().sorted()
        val filtered = if (room != null) states.filter { it.room == room } else states
        WearUiState(
            states = states,
            filteredStates = filtered,
            selectedRoom = room,
            availableRooms = rooms,
            isPhoneConnected = connected,
            lastSync = lastSync
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WearUiState()
    )

    fun selectRoom(room: String?) {
        _selectedRoom.value = room
    }

    fun getStateById(id: String): SmartHomeState? =
        wearRepository.getStateById(id)
}
