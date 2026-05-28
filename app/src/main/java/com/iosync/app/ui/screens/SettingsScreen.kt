package com.iosync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.network.GeocodingResult
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.ui.viewmodel.MainUiState
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
    var ioSyncHost      by remember(uiState.ioSyncHost)      { mutableStateOf(uiState.ioSyncHost) }
    var ioSyncPort      by remember(uiState.ioSyncPort)      { mutableStateOf(uiState.ioSyncPort.toString()) }
    var ioSyncUseHttps  by remember(uiState.ioSyncUseHttps)  { mutableStateOf(uiState.ioSyncUseHttps) }
    var ioSyncUsername  by remember(uiState.ioSyncUsername)   { mutableStateOf(uiState.ioSyncUsername) }
    var ioSyncPassword  by remember(uiState.ioSyncPassword)  { mutableStateOf(uiState.ioSyncPassword) }

    // Watchface-Konfiguration
    var wfTimeColor        by remember(uiState.wfTimeColor)        { mutableStateOf(uiState.wfTimeColor) }
    var wfDateColor        by remember(uiState.wfDateColor)        { mutableStateOf(uiState.wfDateColor) }
    var wfShowSeconds      by remember(uiState.wfShowSeconds)      { mutableStateOf(uiState.wfShowSeconds) }
    var wfShowTicks        by remember(uiState.wfShowTicks)        { mutableStateOf(uiState.wfShowTicks) }
    var wfShowWeekday      by remember(uiState.wfShowWeekday)      { mutableStateOf(uiState.wfShowWeekday) }
    var wfShowPhoneBattery by remember(uiState.wfShowPhoneBattery) { mutableStateOf(uiState.wfShowPhoneBattery) }
    var wfShowIoBrokerData by remember(uiState.wfShowIoBrokerData) { mutableStateOf(uiState.wfShowIoBrokerData) }
    var wfShowSecondsRing  by remember(uiState.wfShowSecondsRing)  { mutableStateOf(uiState.wfShowSecondsRing) }
    var wfSecondsRingColor   by remember(uiState.wfSecondsRingColor)   { mutableStateOf(uiState.wfSecondsRingColor) }
    var wfSecondsRingWidth   by remember(uiState.wfSecondsRingWidth)   { mutableStateOf(uiState.wfSecondsRingWidth.toFloat()) }
    var wfSecondsGlowWidth   by remember(uiState.wfSecondsGlowWidth)   { mutableStateOf(uiState.wfSecondsGlowWidth.toFloat()) }
    var wfSecondsNumberColor by remember(uiState.wfSecondsNumberColor) { mutableStateOf(uiState.wfSecondsNumberColor) }

    // Wetter & Gesundheitsdaten
    var wfShowWeather   by remember(uiState.wfShowWeather)   { mutableStateOf(uiState.wfShowWeather) }
    var wfShowHeartRate by remember(uiState.wfShowHeartRate) { mutableStateOf(uiState.wfShowHeartRate) }
    var wfShowOxygen    by remember(uiState.wfShowOxygen)    { mutableStateOf(uiState.wfShowOxygen) }
    var wfShowCalories  by remember(uiState.wfShowCalories)  { mutableStateOf(uiState.wfShowCalories) }

    // Custom ioBroker-Slots
    var showCustomSlots by remember(uiState.showCustomSlots) { mutableStateOf(uiState.showCustomSlots) }
    var customSlot1Id    by remember(uiState.customSlot1Id)    { mutableStateOf(uiState.customSlot1Id) }
    var customSlot1Label by remember(uiState.customSlot1Label) { mutableStateOf(uiState.customSlot1Label) }
    var customSlot2Id    by remember(uiState.customSlot2Id)    { mutableStateOf(uiState.customSlot2Id) }
    var customSlot2Label by remember(uiState.customSlot2Label) { mutableStateOf(uiState.customSlot2Label) }
    var customSlot3Id    by remember(uiState.customSlot3Id)    { mutableStateOf(uiState.customSlot3Id) }
    var customSlot3Label by remember(uiState.customSlot3Label) { mutableStateOf(uiState.customSlot3Label) }
    var customSlot4Id    by remember(uiState.customSlot4Id)    { mutableStateOf(uiState.customSlot4Id) }
    var customSlot4Label by remember(uiState.customSlot4Label) { mutableStateOf(uiState.customSlot4Label) }
    var customSlot4BarColor      by remember(uiState.customSlot4BarColor)      { mutableStateOf(uiState.customSlot4BarColor) }
    var customSlot4BarMin        by remember(uiState.customSlot4BarMin)        { mutableStateOf(uiState.customSlot4BarMin.toString()) }
    var customSlot4BarMax        by remember(uiState.customSlot4BarMax)        { mutableStateOf(uiState.customSlot4BarMax.toString()) }
    var customSlot4BarShowLabel  by remember(uiState.customSlot4BarShowLabel)  { mutableStateOf(uiState.customSlot4BarShowLabel) }

    // Individuelle Schriftgrößen je Wert
    var wfHrTextScale    by remember(uiState.wfHrTextScale)    { mutableStateOf(uiState.wfHrTextScale) }
    var wfKcalTextScale  by remember(uiState.wfKcalTextScale)  { mutableStateOf(uiState.wfKcalTextScale) }
    var wfSlot1TextScale by remember(uiState.wfSlot1TextScale) { mutableStateOf(uiState.wfSlot1TextScale) }
    var wfSlot2TextScale by remember(uiState.wfSlot2TextScale) { mutableStateOf(uiState.wfSlot2TextScale) }
    var wfSlot3TextScale by remember(uiState.wfSlot3TextScale) { mutableStateOf(uiState.wfSlot3TextScale) }
    var wfSlot4TextScale by remember(uiState.wfSlot4TextScale) { mutableStateOf(uiState.wfSlot4TextScale) }

    // Aktions-Pille
    var pillEnabled    by remember(uiState.actionPillEnabled)    { mutableStateOf(uiState.actionPillEnabled) }
    var pillColorTrue  by remember(uiState.actionPillColorTrue)  { mutableStateOf(uiState.actionPillColorTrue) }
    var pillColorFalse by remember(uiState.actionPillColorFalse) { mutableStateOf(uiState.actionPillColorFalse) }
    var pillIoBrokerId by remember(uiState.actionPillIoBrokerId) { mutableStateOf(uiState.actionPillIoBrokerId) }
    var pillValueMode  by remember(uiState.actionPillValueMode)  { mutableStateOf(uiState.actionPillValueMode) }
    var pillFixedValue by remember(uiState.actionPillFixedValue) { mutableStateOf(uiState.actionPillFixedValue) }

    // ── Auto-Transfer bei Watchface-Einstellungsänderung ────────────────────
    var wfSettingsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(
        wfTimeColor, wfDateColor, wfShowSeconds, wfShowTicks, wfShowWeekday,
        wfShowPhoneBattery, wfShowIoBrokerData, wfShowSecondsRing, wfSecondsRingColor,
        wfSecondsRingWidth, wfSecondsGlowWidth, wfSecondsNumberColor,
        wfShowWeather, wfShowHeartRate, wfShowOxygen, wfShowCalories,
        wfHrTextScale, wfKcalTextScale
    ) {
        if (!wfSettingsInitialized) { wfSettingsInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.updateWatchFaceConfig(
            timeColor          = wfTimeColor,
            dateColor          = wfDateColor,
            showSeconds        = wfShowSeconds,
            showTicks          = wfShowTicks,
            showWeekday        = wfShowWeekday,
            showPhoneBattery   = wfShowPhoneBattery,
            showIoBrokerData   = wfShowIoBrokerData,
            showSecondsRing    = wfShowSecondsRing,
            secondsRingColor   = wfSecondsRingColor,
            secondsRingWidth   = wfSecondsRingWidth.toInt(),
            secondsGlowWidth   = wfSecondsGlowWidth.toInt(),
            secondsNumberColor = wfSecondsNumberColor,
            showWeather        = wfShowWeather,
            showHeartRate      = wfShowHeartRate,
            showOxygen         = wfShowOxygen,
            showCalories       = wfShowCalories,
            showCustomSlots    = showCustomSlots,
            customSlot1Label   = customSlot1Label.trim(),
            customSlot2Label   = customSlot2Label.trim(),
            hrTextScale        = wfHrTextScale,
            kcalTextScale      = wfKcalTextScale
        )
    }

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

            // ── Datenquelle umschalten ─────────────────────────────────────────
            Text(
                text = "Datenquelle",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.useIoSyncAdapter) "IoSync Adapter" else "Simple-API",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (uiState.useIoSyncAdapter) "Nutzt den IoSync ioBroker-Adapter"
                               else "Nutzt die ioBroker Simple-API direkt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.useIoSyncAdapter,
                    onCheckedChange = { viewModel.updateDataSourceToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1A1A00),
                        checkedTrackColor = NeonYellow
                    )
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── IoSync Adapter Verbindung ─────────────────────────────────────
            Text(
                text = "IoSync Adapter",
                style = MaterialTheme.typography.titleSmall,
                color = if (uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "Verbindung zum IoSync ioBroker-Adapter. Lädt Datenpunkte für App und Watchface.",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                ProtocolSelector(
                    useHttps = ioSyncUseHttps,
                    onSelect = { ioSyncUseHttps = it },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ioSyncPort,
                    onValueChange = { ioSyncPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("345") },
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
                        port     = ioSyncPort.toIntOrNull() ?: 345,
                        useHttps = ioSyncUseHttps,
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
                Text("Speichern & Verbinden", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // ── ioBroker Simple-API ────────────────────────────────────────────
            Text(
                text = "ioBroker Simple-API",
                style = MaterialTheme.typography.titleSmall,
                color = if (!uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "Nutzt die ioBroker Simple-API direkt für Datenpunkte.",
                style = MaterialTheme.typography.bodySmall,
                color = if (!uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!uiState.useIoSyncAdapter) NeonYellow else Color(0xFF333333),
                    contentColor = if (!uiState.useIoSyncAdapter) Color(0xFF1A1A00) else Color(0xFFAAAAAA)
                )
            ) {
                Text("Simple-API speichern", style = MaterialTheme.typography.labelLarge)
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
                WatchFaceColorChip(
                    color = Color(0xFFF44336), label = "Rot",
                    selected = wfTimeColor == "red", onClick = { wfTimeColor = "red" }
                )
                WatchFaceColorChip(
                    color = Color(0xFFFF9800), label = "Orange",
                    selected = wfTimeColor == "orange", onClick = { wfTimeColor = "orange" }
                )
                WatchFaceColorChip(
                    color = Color(0xFF9C27B0), label = "Lila",
                    selected = wfTimeColor == "purple", onClick = { wfTimeColor = "purple" }
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
                WatchFaceColorChip(
                    color = Color(0xFFF44336), label = "Rot",
                    selected = wfDateColor == "red", onClick = { wfDateColor = "red" }
                )
                WatchFaceColorChip(
                    color = Color(0xFFFF9800), label = "Orange",
                    selected = wfDateColor == "orange", onClick = { wfDateColor = "orange" }
                )
                WatchFaceColorChip(
                    color = Color(0xFF9C27B0), label = "Lila",
                    selected = wfDateColor == "purple", onClick = { wfDateColor = "purple" }
                )
            }

            if (wfShowSeconds) {
                Text(
                    text = "Sekundenzahl-Farbe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WatchFaceColorChip(
                        color = Color(0xFFAAAAAA), label = "Gedimmt",
                        selected = wfSecondsNumberColor == "dim_time",
                        onClick = { wfSecondsNumberColor = "dim_time" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFE8E8E8), label = "Hellgrau",
                        selected = wfSecondsNumberColor == "light_gray",
                        onClick = { wfSecondsNumberColor = "light_gray" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFEAFF00), label = "Neon Gelb",
                        selected = wfSecondsNumberColor == "neon_yellow",
                        onClick = { wfSecondsNumberColor = "neon_yellow" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFF00BCD4), label = "Cyan",
                        selected = wfSecondsNumberColor == "cyan",
                        onClick = { wfSecondsNumberColor = "cyan" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFF44336), label = "Rot",
                        selected = wfSecondsNumberColor == "red",
                        onClick = { wfSecondsNumberColor = "red" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFFF9800), label = "Orange",
                        selected = wfSecondsNumberColor == "orange",
                        onClick = { wfSecondsNumberColor = "orange" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFF9C27B0), label = "Lila",
                        selected = wfSecondsNumberColor == "purple",
                        onClick = { wfSecondsNumberColor = "purple" }
                    )
                }
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

            // ── Wetter & Gesundheitsdaten ─────────────────────────────────────
            Text(
                text = "Wetter & Gesundheitsdaten",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WatchFaceToggleRow(
                text = "Wetter anzeigen",
                subText = "Kreis oben links mit Wettersymbol und Temperatur",
                checked = wfShowWeather,
                onCheckedChange = { wfShowWeather = it }
            )

            if (wfShowWeather) {
                WeatherLocationSection(viewModel = viewModel, uiState = uiState)
            }

            WatchFaceToggleRow(
                text = "Puls anzeigen",
                subText = "Herzfrequenz vom Sensor der Uhr",
                checked = wfShowHeartRate,
                onCheckedChange = { wfShowHeartRate = it }
            )
            WatchFaceToggleRow(
                text = "Oxygen (SpO2) anzeigen",
                subText = "Sauerstoffsättigung (falls Sensor vorhanden)",
                checked = wfShowOxygen,
                onCheckedChange = { wfShowOxygen = it }
            )
            WatchFaceToggleRow(
                text = "Kalorien anzeigen",
                subText = "Geschätzt aus Schrittzähler der Uhr",
                checked = wfShowCalories,
                onCheckedChange = { wfShowCalories = it }
            )

            Spacer(Modifier.height(4.dp))

            // ── Custom ioBroker-Slots ────────────────────────────────────────
            Text(
                text = "ioBroker-Werte auf Watchface",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WatchFaceToggleRow(
                text = "ioBroker-Slots anzeigen",
                subText = "Zwei Datenpunkte unter der Uhrzeit (Label + Wert)",
                checked = showCustomSlots,
                onCheckedChange = { showCustomSlots = it }
            )

            if (showCustomSlots) {
                val availableStates = uiState.states

                // Slot 1
                Text("Slot 1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customSlot1Label,
                        onValueChange = { if (it.length <= 3) customSlot1Label = it },
                        label = { Text("Name") },
                        placeholder = { Text("TMP") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DatapointDropdown(
                        selectedId = customSlot1Id,
                        availableStates = availableStates,
                        onSelect = { customSlot1Id = it },
                        modifier = Modifier.weight(3f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSlot1TextScale, onSelect = { wfSlot1TextScale = it })
                }

                // Slot 2
                Text("Slot 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customSlot2Label,
                        onValueChange = { if (it.length <= 3) customSlot2Label = it },
                        label = { Text("Name") },
                        placeholder = { Text("HUM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DatapointDropdown(
                        selectedId = customSlot2Id,
                        availableStates = availableStates,
                        onSelect = { customSlot2Id = it },
                        modifier = Modifier.weight(3f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSlot2TextScale, onSelect = { wfSlot2TextScale = it })
                }

                // Slot 3 (rechts neben Slot 2 auf der Uhr)
                Text("Slot 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customSlot3Label,
                        onValueChange = { if (it.length <= 3) customSlot3Label = it },
                        label = { Text("Name") },
                        placeholder = { Text("CO2") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DatapointDropdown(
                        selectedId = customSlot3Id,
                        availableStates = availableStates,
                        onSelect = { customSlot3Id = it },
                        modifier = Modifier.weight(3f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSlot3TextScale, onSelect = { wfSlot3TextScale = it })
                }

                // Slot 4: Balken-Graph (direkt unter der Uhrzeit)
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text(
                    text = "Slot 4 – Balken-Graph (unter Uhrzeit)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Füllt sich mit der eingestellten Farbe wenn der Wert dem Maximum nähert",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customSlot4Label,
                        onValueChange = { if (it.length <= 3) customSlot4Label = it },
                        label = { Text("Name") },
                        placeholder = { Text("PWR") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DatapointDropdown(
                        selectedId = customSlot4Id,
                        availableStates = availableStates,
                        onSelect = { customSlot4Id = it },
                        modifier = Modifier.weight(3f)
                    )
                }

                Text("Balken-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = customSlot4BarColor == "neon_yellow", onClick = { customSlot4BarColor = "neon_yellow" })
                    PillColorChip(color = Color(0xFF00BCD4), label = "Cyan",   selected = customSlot4BarColor == "cyan",        onClick = { customSlot4BarColor = "cyan" })
                    PillColorChip(color = Color(0xFF4CAF50), label = "Grün",   selected = customSlot4BarColor == "green",       onClick = { customSlot4BarColor = "green" })
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = customSlot4BarColor == "red",         onClick = { customSlot4BarColor = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = customSlot4BarColor == "orange",      onClick = { customSlot4BarColor = "orange" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = customSlot4BarColor == "purple",      onClick = { customSlot4BarColor = "purple" })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customSlot4BarMin,
                        onValueChange = { customSlot4BarMin = it },
                        label = { Text("Min-Wert") },
                        placeholder = { Text("0") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = customSlot4BarMax,
                        onValueChange = { customSlot4BarMax = it },
                        label = { Text("Max-Wert") },
                        placeholder = { Text("100") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                WatchFaceToggleRow(
                    text = "Beschriftung anzeigen",
                    subText = "Label und Wert über dem Balken einblenden",
                    checked = customSlot4BarShowLabel,
                    onCheckedChange = { customSlot4BarShowLabel = it }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSlot4TextScale, onSelect = { wfSlot4TextScale = it })
                }

                Button(
                    onClick = {
                        viewModel.updateCustomSlotsConfig(
                            enabled    = showCustomSlots,
                            slot1Id    = customSlot1Id.trim(),
                            slot1Label = customSlot1Label.trim(),
                            slot2Id    = customSlot2Id.trim(),
                            slot2Label = customSlot2Label.trim(),
                            slot3Id    = customSlot3Id.trim(),
                            slot3Label = customSlot3Label.trim(),
                            slot4Id    = customSlot4Id.trim(),
                            slot4Label = customSlot4Label.trim(),
                            slot4BarColor     = customSlot4BarColor,
                            slot4BarMin       = customSlot4BarMin.toFloatOrNull() ?: 0f,
                            slot4BarMax       = customSlot4BarMax.toFloatOrNull() ?: 100f,
                            slot4BarShowLabel = customSlot4BarShowLabel,
                            slot1TextScale    = wfSlot1TextScale,
                            slot2TextScale    = wfSlot2TextScale,
                            slot3TextScale    = wfSlot3TextScale,
                            slot4TextScale    = wfSlot4TextScale
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonYellow,
                        contentColor = Color(0xFF1A1A00)
                    )
                ) {
                    Text("Slots speichern & übertragen", style = MaterialTheme.typography.labelLarge)
                }
            }

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

            // ── Schriftgröße Gesundheitsdaten ─────────────────────────────
            HorizontalDivider(color = Color(0xFF2A2A2A))
            Text(
                text = "Schriftgröße – Puls & Kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Puls", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfHrTextScale, onSelect = { wfHrTextScale = it })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfKcalTextScale, onSelect = { wfKcalTextScale = it })
                }
            }

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
                    WatchFaceColorChip(
                        color = Color(0xFFF44336), label = "Rot",
                        selected = wfSecondsRingColor == "red",
                        onClick = { wfSecondsRingColor = "red" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFFFF9800), label = "Orange",
                        selected = wfSecondsRingColor == "orange",
                        onClick = { wfSecondsRingColor = "orange" }
                    )
                    WatchFaceColorChip(
                        color = Color(0xFF9C27B0), label = "Lila",
                        selected = wfSecondsRingColor == "purple",
                        onClick = { wfSecondsRingColor = "purple" }
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

                Text(
                    text = "Schein-Breite: ${wfSecondsGlowWidth.toInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = wfSecondsGlowWidth,
                    onValueChange = { wfSecondsGlowWidth = it },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    viewModel.updateWatchFaceConfig(
                        timeColor          = wfTimeColor,
                        dateColor          = wfDateColor,
                        showSeconds        = wfShowSeconds,
                        showTicks          = wfShowTicks,
                        showWeekday        = wfShowWeekday,
                        showPhoneBattery   = wfShowPhoneBattery,
                        showIoBrokerData   = wfShowIoBrokerData,
                        showSecondsRing    = wfShowSecondsRing,
                        secondsRingColor   = wfSecondsRingColor,
                        secondsRingWidth   = wfSecondsRingWidth.toInt(),
                        secondsGlowWidth   = wfSecondsGlowWidth.toInt(),
                        secondsNumberColor = wfSecondsNumberColor,
                        showWeather        = wfShowWeather,
                        showHeartRate      = wfShowHeartRate,
                        showOxygen         = wfShowOxygen,
                        showCalories       = wfShowCalories,
                        showCustomSlots    = showCustomSlots,
                        customSlot1Label   = customSlot1Label.trim(),
                        customSlot2Label   = customSlot2Label.trim(),
                        hrTextScale        = wfHrTextScale,
                        kcalTextScale      = wfKcalTextScale,
                        slot1TextScale     = wfSlot1TextScale,
                        slot2TextScale     = wfSlot2TextScale,
                        slot3TextScale     = wfSlot3TextScale,
                        slot4TextScale     = wfSlot4TextScale
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

            // Sync-Status-Konsole
            if (uiState.wearSyncLog.isNotBlank()) {
                Text(
                    text = uiState.wearSyncLog,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.wearSyncLog.startsWith("Fehler"))
                        Color(0xFFF44336) else Color(0xFF4CAF50),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
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
                val availableStates = uiState.states
                DatapointDropdown(
                    selectedId = pillIoBrokerId,
                    availableStates = availableStates,
                    onSelect = { pillIoBrokerId = it },
                    modifier = Modifier.fillMaxWidth()
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
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",
                        selected = pillColorTrue == "red", onClick = { pillColorTrue = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange",
                        selected = pillColorTrue == "orange", onClick = { pillColorTrue = "orange" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",
                        selected = pillColorTrue == "purple", onClick = { pillColorTrue = "purple" })
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

            // Sync-Status-Konsole (Pille)
            if (uiState.wearSyncLog.isNotBlank()) {
                Text(
                    text = uiState.wearSyncLog,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.wearSyncLog.startsWith("Fehler"))
                        Color(0xFFF44336) else Color(0xFF4CAF50),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun WeatherLocationSection(
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationPermissionGranted) {
            viewModel.useRealtimeWeatherLocation()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (uiState.weatherUseFixedLocation && uiState.weatherFixedCity.isNotBlank())
                "Standort: ${uiState.weatherFixedCity}" else "Standort: GPS (Echtzeit)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Suchleiste für festen Standort
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                viewModel.searchWeatherLocations(query)
            },
            label = { Text("Ort suchen") },
            placeholder = { Text("z.B. Berlin, München ...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (uiState.weatherSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NeonYellow
                    )
                }
            }
        )

        // Suchergebnisse
        if (uiState.weatherSearchResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                uiState.weatherSearchResults.forEachIndexed { index, result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setFixedWeatherLocation(result.lat, result.lon, result.displayName)
                                searchQuery = result.name
                                viewModel.clearWeatherSearchResults()
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = result.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (index < uiState.weatherSearchResults.lastIndex) {
                        HorizontalDivider(color = Color(0xFF333333))
                    }
                }
            }
        } else if (!uiState.weatherSearching && searchQuery.length >= 2) {
            Text(
                text = if (uiState.weatherSearchError != null) "Fehler: ${uiState.weatherSearchError}" else "Keine Orte gefunden",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.weatherSearchError != null) Color(0xFFF44336) else Color(0xFF888888),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Button: Echtzeit-Standort verwenden
        Button(
            onClick = {
                if (locationPermissionGranted) {
                    viewModel.useRealtimeWeatherLocation()
                } else {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!uiState.weatherUseFixedLocation) NeonYellow else Color(0xFF2A2A2A),
                contentColor = if (!uiState.weatherUseFixedLocation) Color(0xFF1A1A00) else Color(0xFFAAAAAA)
            )
        ) {
            Text(
                text = if (locationPermissionGranted) "Echtzeit-Standort verwenden"
                       else "Standort-Berechtigung erteilen",
                style = MaterialTheme.typography.labelLarge
            )
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
private fun ProtocolSelector(
    useHttps: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Button(
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!useHttps) NeonYellow else Color(0xFF2A2A2A),
                contentColor   = if (!useHttps) Color(0xFF1A1A00) else Color(0xFFAAAAAA)
            )
        ) {
            Text("HTTP", style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (useHttps) NeonYellow else Color(0xFF2A2A2A),
                contentColor   = if (useHttps) Color(0xFF1A1A00) else Color(0xFFAAAAAA)
            )
        ) {
            Text("HTTPS", style = MaterialTheme.typography.labelMedium)
        }
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

/**
 * Dropdown-Auswahl für ioBroker-Datenpunkte.
 * Zeigt die auf der Startseite geladenen Datenpunkte als Auswahlmenü.
 */
@Composable
private fun DatapointDropdown(
    selectedId: String,
    availableStates: List<SmartHomeState>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (selectedId.isBlank()) {
        ""
    } else {
        availableStates.firstOrNull { it.id == selectedId }?.name
            ?: selectedId.substringAfterLast(".")
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Datenpunkt") },
            placeholder = { Text("Tippen zum Auswählen") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            singleLine = true,
            enabled = false,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        // Unsichtbarer Klick-Overlay über dem disabled TextField
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .background(Color(0xFF2A2A2A))
        ) {
            if (availableStates.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Keine Datenpunkte geladen",
                            color = Color(0xFF888888),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = { expanded = false }
                )
            } else {
                availableStates.forEach { state ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = state.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = state.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF888888),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        onClick = {
                            onSelect(state.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ── Schriftgröße-Dropdown ──────────────────────────────────────────────────────

private val FONT_SIZE_OPTIONS = listOf(70, 80, 90, 100, 110, 120, 140, 160)

@Composable
fun FontSizeDropdown(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text("$selected %", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            FONT_SIZE_OPTIONS.forEach { size ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$size %",
                            color = if (size == selected) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(size)
                        expanded = false
                    }
                )
            }
        }
    }
}
