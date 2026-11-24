package com.iosync.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.iosync.app.data.sleep.HealthySleep
import com.iosync.app.data.sleep.HealthySleepHaWorker
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.ui.theme.StatusError
import com.iosync.app.ui.theme.StatusOnline
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HealthySleepUiState(
    /** null = noch keine Meldung von der Uhr. */
    val sleeping: Boolean? = null,
    val changedAt: Long? = null,
    val sleepStart: Long? = null,
    val sleepEnd: Long? = null,
    val haEnabled: Boolean = true,
    val haError: String? = null,
    val haSyncedAt: Long? = null,
    val haVia: String? = null
)

/**
 * Eigenes ViewModel statt Felder im MainUiState — der ist schon an der Grenze
 * (VerifyError durch zu großen MainUiState, v5.1.x).
 */
@HiltViewModel
class HealthySleepViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val state: StateFlow<HealthySleepUiState> = dataStore.data.map { prefs ->
        HealthySleepUiState(
            sleeping   = prefs[HealthySleep.KEY_SLEEPING]?.takeIf { prefs[HealthySleep.KEY_CHANGED_AT] != null },
            changedAt  = prefs[HealthySleep.KEY_CHANGED_AT],
            sleepStart = prefs[HealthySleep.KEY_SLEEP_START],
            sleepEnd   = prefs[HealthySleep.KEY_SLEEP_END],
            haEnabled  = prefs[HealthySleep.KEY_HA_ENABLED] ?: true,
            haError    = prefs[HealthySleep.KEY_HA_STATUS],
            haSyncedAt = prefs[HealthySleep.KEY_HA_SYNCED_AT],
            haVia      = prefs[HealthySleep.KEY_HA_VIA]
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthySleepUiState())

    fun setHaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[HealthySleep.KEY_HA_ENABLED] = enabled }
            if (enabled) HealthySleepHaWorker.enqueue(context)
        }
    }

    fun syncNow() = HealthySleepHaWorker.enqueue(context)
}

/** Inhalt der Sektion "Schlafstatus von Healthy" in den Einstellungen. */
@Composable
fun HealthySleepSection(viewModel: HealthySleepViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        "Die Healthy-App auf der Uhr meldet Einschlafen und Aufwachen sofort an das IoSync-Watchface; " +
            "das Handy trägt den Status als Gerät „Healthy“ in Home Assistant ein (Langzeit-Token von oben) – " +
            "über die IoSync-Integration aus HACS, ohne sie über die Mobile-App-Integration. " +
            "So lässt sich z.B. die Heizung steuern, solange du schläfst.",
        style = MaterialTheme.typography.bodySmall, color = muted
    )

    val sleeping = state.sleeping
    Text(
        when (sleeping) {
            null  -> "Noch keine Meldung von Healthy"
            true  -> "Schläft seit ${time(state.sleepStart ?: state.changedAt)}"
            false -> "Wach seit ${time(state.sleepEnd ?: state.changedAt)}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (sleeping == null) muted else NeonYellow
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("An Home Assistant senden", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${HealthySleep.ENTITY_SLEEPING} (an = schläft), ${HealthySleep.ENTITY_SLEEP_START}, ${HealthySleep.ENTITY_SLEEP_END}",
                style = MaterialTheme.typography.labelSmall, color = muted
            )
        }
        Switch(
            checked = state.haEnabled, onCheckedChange = viewModel::setHaEnabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1A1A00), checkedTrackColor = NeonYellow)
        )
    }

    if (state.haEnabled) {
        val error = state.haError
        val syncedAt = state.haSyncedAt
        when {
            error != null -> Text(error, style = MaterialTheme.typography.labelSmall, color = StatusError)
            syncedAt != null -> Text(
                "Übertragen ${time(syncedAt)}" + when (state.haVia) {
                    HealthySleep.VIA_INTEGRATION -> " über IoSync-Integration"
                    HealthySleep.VIA_MOBILE_APP  -> " über Mobile-App-Integration"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall, color = StatusOnline
            )
        }
        if (sleeping != null) {
            OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) {
                Text("Jetzt an Home Assistant senden")
            }
        }
    }
}

private fun time(millis: Long?): String =
    millis?.let { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it)) } ?: "--"
