package com.iosync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Simple-API Verbindung
    var host by remember(uiState.host) { mutableStateOf(uiState.host) }
    var port by remember(uiState.port) { mutableStateOf(uiState.port.toString()) }

    // IoSync Adapter Verbindung
    var ioSyncHost     by remember(uiState.ioSyncHost)     { mutableStateOf(uiState.ioSyncHost) }
    var ioSyncPort     by remember(uiState.ioSyncPort)     { mutableStateOf(uiState.ioSyncPort.toString()) }
    var ioSyncUsername by remember(uiState.ioSyncUsername) { mutableStateOf(uiState.ioSyncUsername) }
    var ioSyncPassword by remember(uiState.ioSyncPassword) { mutableStateOf(uiState.ioSyncPassword) }

    // Watchface-Konfiguration
    var wfTimeColor        by remember(uiState.wfTimeColor)        { mutableStateOf(uiState.wfTimeColor) }
    var wfDateColor        by remember(uiState.wfDateColor)        { mutableStateOf(uiState.wfDateColor) }
    var wfShowSeconds      by remember(uiState.wfShowSeconds)      { mutableStateOf(uiState.wfShowSeconds) }
    var wfShowTicks        by remember(uiState.wfShowTicks)        { mutableStateOf(uiState.wfShowTicks) }
    var wfShowWeekday      by remember(uiState.wfShowWeekday)      { mutableStateOf(uiState.wfShowWeekday) }
    var wfShowPhoneBattery by remember(uiState.wfShowPhoneBattery) { mutableStateOf(uiState.wfShowPhoneBattery) }
    var wfShowIoBrokerData by remember(uiState.wfShowIoBrokerData) { mutableStateOf(uiState.wfShowIoBrokerData) }
    var wfShowSecondsRing  by remember(uiState.wfShowSecondsRing)  { mutableStateOf(uiState.wfShowSecondsRing) }
    var wfSecondsRingColor by remember(uiState.wfSecondsRingColor) { mutableStateOf(uiState.wfSecondsRingColor) }
    var wfSecondsRingWidth by remember(uiState.wfSecondsRingWidth) { mutableStateOf(uiState.wfSecondsRingWidth.toFloat()) }

    // Aktions-Pille
    var pillEnabled    by remember(uiState.actionPillEnabled)    { mutableStateOf(uiState.actionPillEnabled) }
    var pillColorTrue  by remember(uiState.actionPillColorTrue)  { mutableStateOf(uiState.actionPillColorTrue) }
    var pillColorFalse by remember(uiState.actionPillColorFalse) { mutableStateOf(uiState.actionPillColorFalse) }
    var pillIoBrokerId by remember(uiState.actionPillIoBrokerId) { mutableStateOf(uiState.actionPillIoBrokerId) }
    var pillValueMode  by remember(uiState.actionPillValueMode)  { mutableStateOf(uiState.actionPillValueMode) }
    var pillFixedValue by remember(uiState.actionPillFixedValue) { mutableStateOf(uiState.actionPillFixedValue) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── ioBroker Simple-API ──────────────────────────────────────────
            Text(
                text = "ioBroker / Home Assistant Verbindung",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP-Adresse") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port (Simple-API)") },
                placeholder = { Text("8082") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = {
                    viewModel.updateConnectionSettings(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 8082
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = Color(0xFF1A1A00)
                )
            ) {
                Text("Speichern & Verbinden", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // ── IoSync Adapter ───────────────────────────────────────────────
            Text(
                text = "IoSync Adapter (HTTPS API)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Verbindung zum IoSync ioBroker-Adapter für Watchface-Datenpunkte und Schreibzugriff.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = ioSyncHost,
                onValueChange = { ioSyncHost = it },
                label = { Text("Adapter-Host / IP") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ioSyncPort,
                    onValueChange = { ioSyncPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("7443") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = ioSyncUsername,
                    onValueChange = { ioSyncUsername = it },
                    label = { Text("Benutzername") },
                    modifier = Modifier.weight(2f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = ioSyncPassword,
                onValueChange = { ioSyncPassword = it },
                label = { Text("Passwort") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Button(
                onClick = {
                    viewModel.updateIoSyncSettings(
                        host     = ioSyncHost.trim(),
                        port     = ioSyncPort.toIntOrNull() ?: 7443,
                        username = ioSyncUsername.trim(),
                        password = ioSyncPassword
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = Color(0xFF1A1A00)
                )
            ) {
                Text("Adapter-Verbindung speichern", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // ── Watchface Konfiguration ──────────────────────────────────────
            Text(
                text = "Watchface Konfiguration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Uhrzeitfarbe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(
                    color = Color(0xFFE8E8E8), label = "Hellgrau",
                    selected = wfTimeColor == "light_gray", onClick = { wfTimeColor = "light_gray" }
                )
                WatchFaceColorChip(
                    color = Color.White, label = "Weiß",
                    selected = wfTimeColor == "white", onClick = { wfTimeColor = "white" }
                )
                WatchFaceColorChip(
                    color = Color(0xFFEAFF00), label = "Neon Gelb",
                    selected = wfTimeColor == "neon_yellow", onClick = { wfTimeColor = "neon_yellow" }
                )
                WatchFaceColorChip(
                    color = Color(0xFF00BCD4), label = "Cyan",
                    selected = wfTimeColor == "cyan", onClick = { wfTimeColor = "cyan" }
                )
            }

            Text(
                text = "Datum-/Wochentagfarbe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(
                    color = Color(0xFFE8E8E8), label = "Hellgrau",
                    selected = wfDateColor == "light_gray", onClick = { wfDateColor = "light_gray" }
                )
                WatchFaceColorChip(
                    color = Color.White, label = "Weiß",
                    selected = wfDateColor == "white", onClick = { wfDateColor = "white" }
                )
                WatchFaceColorChip(
                    color = Color(0xFFEAFF00), label = "Neon Gelb",
                    selected = wfDateColor == "neon_yellow", onClick = { wfDateColor = "neon_yellow" }
                )
                WatchFaceColorChip(
                    color = Color(0xFF00BCD4), label = "Cyan",
                    selected = wfDateColor == "cyan", onClick = { wfDateColor = "cyan" }
                )
            }

            WatchFaceToggleRow(
                text = "Sekunden anzeigen",
                checked = wfShowSeconds,
                onCheckedChange = { wfShowSeconds = it }
            )
            WatchFaceToggleRow(
                text = "Zifferblatt-Striche",
                checked = wfShowTicks,
                onCheckedChange = { wfShowTicks = it }
            )
            WatchFaceToggleRow(
                text = "Wochentag anzeigen",
                checked = wfShowWeekday,
                onCheckedChange = { wfShowWeekday = it }
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Watchface-Daten (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WatchFaceToggleRow(
                text = "ioBroker-Datenpunkte anzeigen",
                subText = if (uiState.ioSyncHost.isBlank()) "IoSync Adapter muss konfiguriert sein"
                          else "${uiState.ioSyncStates.size} Datenpunkte verfügbar",
                checked = wfShowIoBrokerData,
                onCheckedChange = { wfShowIoBrokerData = it }
            )
            WatchFaceToggleRow(
                text = "Handy-Akkustand anzeigen",
                subText = if (uiState.phoneBatteryLevel >= 0) "Aktuell: ${uiState.phoneBatteryLevel}%"
                          else "Wird beim ersten Sync gelesen",
                checked = wfShowPhoneBattery,
                onCheckedChange = { wfShowPhoneBattery = it }
            )

            Spacer(Modifier.height(4.dp))

            // ── Sekundenring ─────────────────────────────────────────────────
            Text(
                text = "Sekundenring (äußerer Rand)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WatchFaceToggleRow(
                text = "Sekundenring anzeigen",
                subText = "Füllt sich sekündlich um den Rand (0–59 s = 0–360°)",
                checked = wfShowSecondsRing,
                onCheckedChange = { wfShowSecondsRing = it }
            )

            if (wfShowSecondsRing) {
                Text(
                    text = "Ringfarbe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WatchFaceColorChip(
                        color = Color(0xFFE8E8E8), label = "Hellgrau",
                        selected = wfSecondsRingColor == "light_gray",
                        onClick = { wfSecondsRingColor = "light_gray" }
                    )
                    WatchFaceColorChip(
                        color = Color.White, label = "Weiß",
                        selected = wfSecondsRingColor == "white",
                        onClick = { wfSecondsRingColor = "white" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFEAFF00), label = "Neon Gelb",
                        selected = wfSecondsRingColor == "neon_yellow",
                        onClick = { wfSecondsRingColor = "neon_yellow" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFF00BCD4), label = "Cyan",
                        selected = wfSecondsRingColor == "cyan",
                        onClick = { wfSecondsRingColor = "cyan" }
                    )
                }

                Text(
                    text = "Ringbreite: ${wfSecondsRingWidth.toInt()} dp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = wfSecondsRingWidth,
                    onValueChange = { wfSecondsRingWidth = it },
                    valueRange = 2f..10f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    viewModel.updateWatchFaceConfig(
                        timeColor        = wfTimeColor,
                        dateColor        = wfDateColor,
                        showSeconds      = wfShowSeconds,
                        showTicks        = wfShowTicks,
                        showWeekday      = wfShowWeekday,
                        showPhoneBattery = wfShowPhoneBattery,
                        showIoBrokerData = wfShowIoBrokerData,
                        showSecondsRing  = wfShowSecondsRing,
                        secondsRingColor = wfSecondsRingColor,
                        secondsRingWidth = wfSecondsRingWidth.toInt()
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = Color(0xFF1A1A00)
                )
            ) {
                Text("Auf Uhr übertragen", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // ── Aktions-Pille (6-Uhr-Button) ─────────────────────────────────
            Text(
                text = "Aktions-Pille (6-Uhr-Button)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Pill-Button bei 6 Uhr am Watchface. Doppelklick sendet einen ioBroker-Befehl.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WatchFaceToggleRow(
                text = "Aktions-Pille aktivieren",
                checked = pillEnabled,
                onCheckedChange = { pillEnabled = it }
            )

            if (pillEnabled) {
                OutlinedTextField(
                    value = pillIoBrokerId,
                    onValueChange = { pillIoBrokerId = it },
                    label = { Text("ioBroker Datenpunkt-ID") },
                    placeholder = { Text("hm-rpc.0.ABC123.1.STATE") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Aktion beim Doppelklick",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PillValueModeSelector(
                    selected = pillValueMode,
                    onSelect = { pillValueMode = it }
                )

                if (pillValueMode == "fixed") {
                    OutlinedTextField(
                        value = pillFixedValue,
                        onValueChange = { pillFixedValue = it },
                        label = { Text("Fester Wert") },
                        placeholder = { Text("z.B. 50 oder Hallo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Text(
                    text = "Farbe: Aktiv (true)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PillColorChip(color = Color(0xFF00BCD4), label = "Cyan",
                        selected = pillColorTrue == "cyan", onClick = { pillColorTrue = "cyan" })
                    PillColorChip(color = Color(0xFF4CAF50), label = "Grün",
                        selected = pillColorTrue == "green", onClick = { pillColorTrue = "green" })
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",
                        selected = pillColorTrue == "neon_yellow", onClick = { pillColorTrue = "neon_yellow" })
                    PillColorChip(color = Color.White, label = "Weiß",
                        selected = pillColorTrue == "white", onClick = { pillColorTrue = "white" })
                }

                Text(
                    text = "Farbe: Inaktiv (false)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",
                        selected = pillColorFalse == "red", onClick = { pillColorFalse = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange",
                        selected = pillColorFalse == "orange", onClick = { pillColorFalse = "orange" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",
                        selected = pillColorFalse == "purple", onClick = { pillColorFalse = "purple" })
                    PillColorChip(color = Color(0xFF888888), label = "Grau",
                        selected = pillColorFalse == "light_gray", onClick = { pillColorFalse = "light_gray" })
                }

                Button(
                    onClick = {
                        viewModel.updateActionPillConfig(
                            enabled    = pillEnabled,
                            colorTrue  = pillColorTrue,
                            colorFalse = pillColorFalse,
                            ioBrokerId = pillIoBrokerId.trim(),
                            valueMode  = pillValueMode,
                            fixedValue = pillFixedValue.trim()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonYellow,
                        contentColor = Color(0xFF1A1A00)
                    )
                ) {
                    Text("Pille auf Uhr übertragen", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Über IoSync ──────────────────────────────────────────────────
            Text(
                text = "Über IoSync",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DetailCard(label = "Version") {
                Text("1.0.0", style = MaterialTheme.typography.bodyMedium)
            }

            DetailCard(label = "Beschreibung") {
                Text(
                    text = "IoSync verbindet dein Android-Gerät und deine Wear OS Smartwatch mit ioBroker " +
                           "und Home Assistant. Datenpunkte werden in Echtzeit synchronisiert. Der IoSync-Adapter " +
                           "stellt ausgewählte ioBroker-Werte für das Watchface bereit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WatchFaceColorChip(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, CircleShape)
                .then(
                    if (selected) Modifier.border(2.dp, NeonYellow, CircleShape)
                    else Modifier.border(1.dp, Color(0xFF444444), CircleShape)
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) NeonYellow else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WatchFaceToggleRow(
    text: String,
    subText: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1A00),
                checkedTrackColor = NeonYellow
            )
        )
    }
}

@Composable
private fun PillColorChip(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color, CircleShape)
                .then(
                    if (selected) Modifier.border(2.dp, NeonYellow, CircleShape)
                    else Modifier.border(1.dp, Color(0xFF444444), CircleShape)
                )
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) NeonYellow else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PillValueModeSelector(selected: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        "toggle" to "Toggle",
        "true"   to "Boolean TRUE",
        "false"  to "Boolean FALSE",
        "fixed"  to "Fester Wert"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        modes.forEach { (id, label) ->
            val isSelected = selected == id
            Button(
                onClick = { onSelect(id) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) NeonYellow else Color(0xFF2A2A2A),
                    contentColor   = if (isSelected) Color(0xFF1A1A00) else Color(0xFFAAAAAA)
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
