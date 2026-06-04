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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.RadioButton
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
import com.iosync.app.data.crash.CrashLogManager
import com.iosync.app.data.model.SmartHomeState
import com.iosync.app.data.network.GeocodingResult
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.data.health.HealthConnectStatus
import com.iosync.app.data.health.HealthDataTypeInfo
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
    var wfShowSteps     by remember(uiState.wfShowSteps)     { mutableStateOf(uiState.wfShowSteps) }
    var wfHealthDataSource by remember(uiState.wfHealthDataSource) { mutableStateOf(uiState.wfHealthDataSource) }

    // Pro-Typ Gesundheitsdaten-Quelle
    var wfHrSource         by remember(uiState.wfHrSource)         { mutableStateOf(uiState.wfHrSource) }
    var wfKcalSource       by remember(uiState.wfKcalSource)       { mutableStateOf(uiState.wfKcalSource) }
    var wfOxygenSource     by remember(uiState.wfOxygenSource)     { mutableStateOf(uiState.wfOxygenSource) }
    // Pro-Typ gewählter Health-Connect-Datentyp (Key als String, "" = Automatisch/Standard)
    var wfHrComplication     by remember(uiState.wfHrComplication)     { mutableStateOf(uiState.wfHrComplication) }
    var wfKcalComplication   by remember(uiState.wfKcalComplication)   { mutableStateOf(uiState.wfKcalComplication) }
    var wfOxygenComplication by remember(uiState.wfOxygenComplication) { mutableStateOf(uiState.wfOxygenComplication) }
    // Pro-Slot gewählte Metrik (welcher Health-Connect-Wert angezeigt wird)
    var wfKcalMetric   by remember(uiState.wfKcalMetric)   { mutableStateOf(uiState.wfKcalMetric) }
    var wfOxygenMetric by remember(uiState.wfOxygenMetric) { mutableStateOf(uiState.wfOxygenMetric) }

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
    var customSlot4Warn1Color    by remember(uiState.customSlot4Warn1Color)    { mutableStateOf(uiState.customSlot4Warn1Color) }
    var customSlot4Warn1Value    by remember(uiState.customSlot4Warn1Value)    { mutableStateOf(if (uiState.customSlot4Warn1Value.isNaN()) "" else uiState.customSlot4Warn1Value.toString()) }
    var customSlot4Warn2Color    by remember(uiState.customSlot4Warn2Color)    { mutableStateOf(uiState.customSlot4Warn2Color) }
    var customSlot4Warn2Value    by remember(uiState.customSlot4Warn2Value)    { mutableStateOf(if (uiState.customSlot4Warn2Value.isNaN()) "" else uiState.customSlot4Warn2Value.toString()) }

    // Individuelle Schriftgrößen je Wert
    var wfHrTextScale      by remember(uiState.wfHrTextScale)      { mutableStateOf(uiState.wfHrTextScale) }
    var wfKcalTextScale    by remember(uiState.wfKcalTextScale)    { mutableStateOf(uiState.wfKcalTextScale) }
    var wfStepsTextScale   by remember(uiState.wfStepsTextScale)   { mutableStateOf(uiState.wfStepsTextScale) }
    var wfSlot1TextScale   by remember(uiState.wfSlot1TextScale)   { mutableStateOf(uiState.wfSlot1TextScale) }
    var wfSlot2TextScale   by remember(uiState.wfSlot2TextScale)   { mutableStateOf(uiState.wfSlot2TextScale) }
    var wfSlot3TextScale   by remember(uiState.wfSlot3TextScale)   { mutableStateOf(uiState.wfSlot3TextScale) }
    var wfSlot4TextScale   by remember(uiState.wfSlot4TextScale)   { mutableStateOf(uiState.wfSlot4TextScale) }
    var wfWeatherTextScale by remember(uiState.wfWeatherTextScale) { mutableStateOf(uiState.wfWeatherTextScale) }
    var wfSunriseTextScale by remember(uiState.wfSunriseTextScale) { mutableStateOf(uiState.wfSunriseTextScale) }
    var wfWatchBatteryTextScale by remember(uiState.wfWatchBatteryTextScale) { mutableStateOf(uiState.wfWatchBatteryTextScale) }

    // Akku-Ring-Farben und Ringbreite
    var wfBatteryRingColor1       by remember(uiState.wfBatteryRingColor1)       { mutableStateOf(uiState.wfBatteryRingColor1) }
    var wfBatteryRingColor2       by remember(uiState.wfBatteryRingColor2)       { mutableStateOf(uiState.wfBatteryRingColor2) }
    var wfBatteryRingStrokeScale  by remember(uiState.wfBatteryRingStrokeScale)  { mutableStateOf(uiState.wfBatteryRingStrokeScale.toFloat()) }
    var wfBatteryWarn1Color       by remember(uiState.wfBatteryWarn1Color)       { mutableStateOf(uiState.wfBatteryWarn1Color) }
    var wfBatteryWarn1Threshold   by remember(uiState.wfBatteryWarn1Threshold)   { mutableStateOf(uiState.wfBatteryWarn1Threshold.toFloat()) }
    var wfBatteryWarn2Color       by remember(uiState.wfBatteryWarn2Color)       { mutableStateOf(uiState.wfBatteryWarn2Color) }
    var wfBatteryWarn2Threshold   by remember(uiState.wfBatteryWarn2Threshold)   { mutableStateOf(uiState.wfBatteryWarn2Threshold.toFloat()) }

    // Aktualisierungsintervalle
    var batteryPollInterval by remember(uiState.batteryPollIntervalSec) { mutableStateOf(uiState.batteryPollIntervalSec) }
    var slotPollInterval   by remember(uiState.slotPollIntervalSec)    { mutableStateOf(uiState.slotPollIntervalSec) }
    var healthPollInterval by remember(uiState.healthPollIntervalSec)  { mutableStateOf(uiState.healthPollIntervalSec) }
    var page2SyncInterval  by remember(uiState.page2SyncIntervalSec)   { mutableStateOf(uiState.page2SyncIntervalSec) }

    // Hintergrundbild
    var wfShowBackground by remember(uiState.wfShowBackground) { mutableStateOf(uiState.wfShowBackground) }

    // Gesundheitsdaten-Farben
    var wfHrColor      by remember(uiState.wfHrColor)      { mutableStateOf(uiState.wfHrColor) }
    var wfKcalColor    by remember(uiState.wfKcalColor)    { mutableStateOf(uiState.wfKcalColor) }
    var wfOxygenColor  by remember(uiState.wfOxygenColor)  { mutableStateOf(uiState.wfOxygenColor) }
    var wfStepsColor   by remember(uiState.wfStepsColor)   { mutableStateOf(uiState.wfStepsColor) }
    var wfSleepColor   by remember(uiState.wfSleepColor)   { mutableStateOf(uiState.wfSleepColor) }
    var wfSunriseColor by remember(uiState.wfSunriseColor) { mutableStateOf(uiState.wfSunriseColor) }
    var wfSlotColor    by remember(uiState.wfSlotColor)    { mutableStateOf(uiState.wfSlotColor) }

    // Aktions-Pille
    var pillEnabled    by remember(uiState.actionPillEnabled)    { mutableStateOf(uiState.actionPillEnabled) }
    var pillColorTrue  by remember(uiState.actionPillColorTrue)  { mutableStateOf(uiState.actionPillColorTrue) }
    var pillColorFalse by remember(uiState.actionPillColorFalse) { mutableStateOf(uiState.actionPillColorFalse) }
    var pillIoBrokerId by remember(uiState.actionPillIoBrokerId) { mutableStateOf(uiState.actionPillIoBrokerId) }
    var pillValueMode  by remember(uiState.actionPillValueMode)  { mutableStateOf(uiState.actionPillValueMode) }
    var pillFixedValue by remember(uiState.actionPillFixedValue) { mutableStateOf(uiState.actionPillFixedValue) }

    // Seite 2 – Slots
    var p2Slot1Id    by remember(uiState.p2Slot1Id)    { mutableStateOf(uiState.p2Slot1Id) }
    var p2Slot1Label by remember(uiState.p2Slot1Label) { mutableStateOf(uiState.p2Slot1Label) }
    var p2Slot2Id    by remember(uiState.p2Slot2Id)    { mutableStateOf(uiState.p2Slot2Id) }
    var p2Slot2Label by remember(uiState.p2Slot2Label) { mutableStateOf(uiState.p2Slot2Label) }
    var p2Slot3Id    by remember(uiState.p2Slot3Id)    { mutableStateOf(uiState.p2Slot3Id) }
    var p2Slot3Label by remember(uiState.p2Slot3Label) { mutableStateOf(uiState.p2Slot3Label) }
    var p2Slot4Id    by remember(uiState.p2Slot4Id)    { mutableStateOf(uiState.p2Slot4Id) }
    var p2Slot4Label by remember(uiState.p2Slot4Label) { mutableStateOf(uiState.p2Slot4Label) }
    var p2Slot1TextScale by remember(uiState.p2Slot1TextScale) { mutableStateOf(uiState.p2Slot1TextScale) }
    var p2Slot2TextScale by remember(uiState.p2Slot2TextScale) { mutableStateOf(uiState.p2Slot2TextScale) }
    var p2Slot3TextScale by remember(uiState.p2Slot3TextScale) { mutableStateOf(uiState.p2Slot3TextScale) }
    var p2Slot4TextScale by remember(uiState.p2Slot4TextScale) { mutableStateOf(uiState.p2Slot4TextScale) }
    var wfSleepTextScale    by remember(uiState.wfSleepTextScale)    { mutableStateOf(uiState.wfSleepTextScale) }
    var wfSleepSource       by remember(uiState.wfSleepSource)       { mutableStateOf(uiState.wfSleepSource) }
    var wfSleepIoBrokerId   by remember(uiState.wfSleepIoBrokerId)   { mutableStateOf(uiState.wfSleepIoBrokerId) }
    var wfSleepComplication by remember(uiState.wfSleepComplication) { mutableStateOf(uiState.wfSleepComplication) }
    var p2ShowBackground    by remember(uiState.p2ShowBackground)    { mutableStateOf(uiState.p2ShowBackground) }
    // Seite 2 – Pille 1 (7 Uhr)
    var p2PillEnabled    by remember(uiState.p2PillEnabled)    { mutableStateOf(uiState.p2PillEnabled) }
    var p2PillColorTrue  by remember(uiState.p2PillColorTrue)  { mutableStateOf(uiState.p2PillColorTrue) }
    var p2PillColorFalse by remember(uiState.p2PillColorFalse) { mutableStateOf(uiState.p2PillColorFalse) }
    var p2PillIoBrokerId by remember(uiState.p2PillIoBrokerId) { mutableStateOf(uiState.p2PillIoBrokerId) }
    var p2PillValueMode  by remember(uiState.p2PillValueMode)  { mutableStateOf(uiState.p2PillValueMode) }
    var p2PillFixedValue by remember(uiState.p2PillFixedValue) { mutableStateOf(uiState.p2PillFixedValue) }
    // Seite 2 – Pille 2 (5 Uhr)
    var p2Pill2Enabled    by remember(uiState.p2Pill2Enabled)    { mutableStateOf(uiState.p2Pill2Enabled) }
    var p2Pill2ColorTrue  by remember(uiState.p2Pill2ColorTrue)  { mutableStateOf(uiState.p2Pill2ColorTrue) }
    var p2Pill2ColorFalse by remember(uiState.p2Pill2ColorFalse) { mutableStateOf(uiState.p2Pill2ColorFalse) }
    var p2Pill2IoBrokerId by remember(uiState.p2Pill2IoBrokerId) { mutableStateOf(uiState.p2Pill2IoBrokerId) }
    var p2Pill2ValueMode  by remember(uiState.p2Pill2ValueMode)  { mutableStateOf(uiState.p2Pill2ValueMode) }
    var p2Pill2FixedValue by remember(uiState.p2Pill2FixedValue) { mutableStateOf(uiState.p2Pill2FixedValue) }

    // Accordion-Sektionsstatus
    var sectionAdapter by remember { mutableStateOf(true) }
    var sectionPage1   by remember { mutableStateOf(false) }
    var sectionPage2   by remember { mutableStateOf(false) }
    var sectionHealth  by remember { mutableStateOf(false) }
    var sectionNtp     by remember { mutableStateOf(false) }

    // NTP-Zeitkorrektur
    var ntpEnabled by remember(uiState.wfNtpEnabled) { mutableStateOf(uiState.wfNtpEnabled) }
    var ntpServer  by remember(uiState.wfNtpServer)  { mutableStateOf(uiState.wfNtpServer) }

    // Wetter-Temperatur-Quelle
    var weatherTempSource by remember(uiState.wfWeatherTempSource) { mutableStateOf(uiState.wfWeatherTempSource) }
    var weatherIoBrokerId by remember(uiState.wfWeatherIoBrokerId) { mutableStateOf(uiState.wfWeatherIoBrokerId) }

    // Seite 2 – Vertikaler Balken
    var p2BarId         by remember(uiState.p2BarId)         { mutableStateOf(uiState.p2BarId) }
    var p2BarLabel      by remember(uiState.p2BarLabel)      { mutableStateOf(uiState.p2BarLabel) }
    var p2BarColor      by remember(uiState.p2BarColor)      { mutableStateOf(uiState.p2BarColor) }
    var p2BarMin        by remember(uiState.p2BarMin)        { mutableStateOf(uiState.p2BarMin.toString()) }
    var p2BarMax        by remember(uiState.p2BarMax)        { mutableStateOf(uiState.p2BarMax.toString()) }
    var p2BarShowLabel  by remember(uiState.p2BarShowLabel)  { mutableStateOf(uiState.p2BarShowLabel) }
    var p2BarTextScale  by remember(uiState.p2BarTextScale)  { mutableStateOf(uiState.p2BarTextScale) }
    var p2BarWarn1Color by remember(uiState.p2BarWarn1Color) { mutableStateOf(uiState.p2BarWarn1Color) }
    var p2BarWarn1Value by remember(uiState.p2BarWarn1Value) { mutableStateOf(if (uiState.p2BarWarn1Value.isNaN()) "" else uiState.p2BarWarn1Value.toString()) }
    var p2BarWarn2Color by remember(uiState.p2BarWarn2Color) { mutableStateOf(uiState.p2BarWarn2Color) }
    var p2BarWarn2Value by remember(uiState.p2BarWarn2Value) { mutableStateOf(if (uiState.p2BarWarn2Value.isNaN()) "" else uiState.p2BarWarn2Value.toString()) }

    // ── Auto-Transfer bei Watchface-Einstellungsänderung ────────────────────
    var wfSettingsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(
        wfTimeColor, wfDateColor, wfShowSeconds, wfShowTicks, wfShowWeekday,
        wfShowPhoneBattery, wfShowIoBrokerData, wfShowSecondsRing, wfSecondsRingColor,
        wfSecondsRingWidth, wfSecondsGlowWidth, wfSecondsNumberColor,
        wfShowWeather, wfShowHeartRate, wfShowOxygen, wfShowCalories, wfShowSteps,
        wfHrTextScale, wfKcalTextScale, wfStepsTextScale, wfWeatherTextScale, wfSunriseTextScale, wfWatchBatteryTextScale,
        wfBatteryRingColor1, wfBatteryRingColor2, wfBatteryRingStrokeScale,
        wfBatteryWarn1Color, wfBatteryWarn1Threshold, wfBatteryWarn2Color, wfBatteryWarn2Threshold,
        wfShowBackground,
        wfHrColor, wfKcalColor, wfOxygenColor, wfStepsColor, wfSleepColor, wfSunriseColor, wfSlotColor,
        weatherTempSource, weatherIoBrokerId
    ) {
        if (!wfSettingsInitialized) { wfSettingsInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.previewWatchFaceConfig(
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
            showSteps          = wfShowSteps,
            showCustomSlots    = showCustomSlots,
            customSlot1Label   = customSlot1Label.trim(),
            customSlot2Label   = customSlot2Label.trim(),
            hrTextScale        = wfHrTextScale,
            kcalTextScale      = wfKcalTextScale,
            stepsTextScale     = wfStepsTextScale,
            weatherTextScale   = wfWeatherTextScale,
            sunriseTextScale   = wfSunriseTextScale,
            watchBatteryTextScale = wfWatchBatteryTextScale,
            batteryRingColor1       = wfBatteryRingColor1,
            batteryRingColor2       = wfBatteryRingColor2,
            batteryRingStrokeScale  = wfBatteryRingStrokeScale.toInt(),
            batteryWarn1Color       = wfBatteryWarn1Color,
            batteryWarn1Threshold   = wfBatteryWarn1Threshold.toInt(),
            batteryWarn2Color       = wfBatteryWarn2Color,
            batteryWarn2Threshold   = wfBatteryWarn2Threshold.toInt(),
            showBackground          = wfShowBackground,
            hrColor                 = wfHrColor,
            kcalColor               = wfKcalColor,
            oxygenColor             = wfOxygenColor,
            stepsColor              = wfStepsColor,
            sleepColor              = wfSleepColor,
            sunriseColor            = wfSunriseColor,
            slotColor               = wfSlotColor,
            weatherTempSource       = weatherTempSource,
            weatherIoBrokerId       = weatherIoBrokerId
        )
    }

    // ── Intervall-Änderungen speichern ───────────────────────────────────────
    var intervalInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(batteryPollInterval, slotPollInterval, healthPollInterval, page2SyncInterval) {
        if (!intervalInitialized) { intervalInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.updatePollIntervals(batteryPollInterval, slotPollInterval, healthPollInterval, page2SyncInterval)
    }

    // ── Live-Vorschau: Custom-Slots (ohne Persistenz) ────────────────────────
    var customSlotsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(
        showCustomSlots, customSlot1Id, customSlot1Label, customSlot2Id, customSlot2Label,
        customSlot3Id, customSlot3Label, customSlot4Id, customSlot4Label,
        customSlot4BarColor, customSlot4BarMin, customSlot4BarMax, customSlot4BarShowLabel,
        wfSlot1TextScale, wfSlot2TextScale, wfSlot3TextScale, wfSlot4TextScale,
        customSlot4Warn1Color, customSlot4Warn1Value, customSlot4Warn2Color, customSlot4Warn2Value
    ) {
        if (!customSlotsInitialized) { customSlotsInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.previewCustomSlotsConfig(
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
            slot4TextScale    = wfSlot4TextScale,
            slot4Warn1Color   = customSlot4Warn1Color,
            slot4Warn1Value   = customSlot4Warn1Value.toFloatOrNull() ?: Float.NaN,
            slot4Warn2Color   = customSlot4Warn2Color,
            slot4Warn2Value   = customSlot4Warn2Value.toFloatOrNull() ?: Float.NaN
        )
    }

    // ── Live-Vorschau: Aktions-Pille (ohne Persistenz) ───────────────────────
    var pillInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(pillEnabled, pillColorTrue, pillColorFalse, pillIoBrokerId, pillValueMode, pillFixedValue) {
        if (!pillInitialized) { pillInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.previewActionPillConfig(
            enabled    = pillEnabled,
            colorTrue  = pillColorTrue,
            colorFalse = pillColorFalse,
            ioBrokerId = pillIoBrokerId.trim(),
            valueMode  = pillValueMode,
            fixedValue = pillFixedValue.trim()
        )
    }

    // ── Live-Vorschau: Health-Quellen (ohne Persistenz) ──────────────────────
    var healthSourceInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(
        wfHrSource, wfKcalSource, wfOxygenSource,
        wfHrComplication, wfKcalComplication, wfOxygenComplication
    ) {
        if (!healthSourceInitialized) { healthSourceInitialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.previewHealthSourceConfig(
            hrSource = wfHrSource,
            kcalSource = wfKcalSource,
            oxygenSource = wfOxygenSource,
            hrComplication = wfHrComplication,
            kcalComplication = wfKcalComplication,
            oxygenComplication = wfOxygenComplication
        )
    }

    // ── Live-Vorschau: Seite 2 (ohne Persistenz) ─────────────────────────────
    var page2Initialized by remember { mutableStateOf(false) }
    LaunchedEffect(
        p2Slot1Id, p2Slot1Label, p2Slot2Id, p2Slot2Label,
        p2Slot3Id, p2Slot3Label, p2Slot4Id, p2Slot4Label,
        p2Slot1TextScale, p2Slot2TextScale, p2Slot3TextScale, p2Slot4TextScale, wfSleepTextScale,
        wfSleepSource, wfSleepIoBrokerId, wfSleepComplication,
        p2PillEnabled, p2PillColorTrue, p2PillColorFalse, p2PillIoBrokerId, p2PillValueMode, p2PillFixedValue,
        p2Pill2Enabled, p2Pill2ColorTrue, p2Pill2ColorFalse, p2Pill2IoBrokerId, p2Pill2ValueMode, p2Pill2FixedValue,
        p2BarId, p2BarLabel, p2BarColor, p2BarMin, p2BarMax, p2BarShowLabel, p2BarTextScale,
        p2BarWarn1Color, p2BarWarn1Value, p2BarWarn2Color, p2BarWarn2Value,
        p2ShowBackground
    ) {
        if (!page2Initialized) { page2Initialized = true; return@LaunchedEffect }
        delay(400)
        viewModel.previewPage2Config(
            slot1Id = p2Slot1Id.trim(), slot1Label = p2Slot1Label.trim(),
            slot2Id = p2Slot2Id.trim(), slot2Label = p2Slot2Label.trim(),
            slot3Id = p2Slot3Id.trim(), slot3Label = p2Slot3Label.trim(),
            slot4Id = p2Slot4Id.trim(), slot4Label = p2Slot4Label.trim(),
            slot1TextScale = p2Slot1TextScale, slot2TextScale = p2Slot2TextScale,
            slot3TextScale = p2Slot3TextScale, slot4TextScale = p2Slot4TextScale,
            sleepTextScale = wfSleepTextScale,
            sleepSource = wfSleepSource, sleepIoBrokerId = wfSleepIoBrokerId.trim(),
            sleepComplication = wfSleepComplication,
            p2PillEnabled = p2PillEnabled, p2PillColorTrue = p2PillColorTrue,
            p2PillColorFalse = p2PillColorFalse, p2PillIoBrokerId = p2PillIoBrokerId.trim(),
            p2PillValueMode = p2PillValueMode, p2PillFixedValue = p2PillFixedValue.trim(),
            p2Pill2Enabled = p2Pill2Enabled, p2Pill2ColorTrue = p2Pill2ColorTrue,
            p2Pill2ColorFalse = p2Pill2ColorFalse, p2Pill2IoBrokerId = p2Pill2IoBrokerId.trim(),
            p2Pill2ValueMode = p2Pill2ValueMode, p2Pill2FixedValue = p2Pill2FixedValue.trim(),
            p2BarId = p2BarId.trim(), p2BarLabel = p2BarLabel.trim(),
            p2BarColor = p2BarColor,
            p2BarMin = p2BarMin.toFloatOrNull() ?: 0f,
            p2BarMax = p2BarMax.toFloatOrNull() ?: 100f,
            p2BarShowLabel = p2BarShowLabel,
            p2BarTextScale = p2BarTextScale,
            p2BarWarn1Color = p2BarWarn1Color,
            p2BarWarn1Value = p2BarWarn1Value.toFloatOrNull() ?: Float.NaN,
            p2BarWarn2Color = p2BarWarn2Color,
            p2BarWarn2Value = p2BarWarn2Value.toFloatOrNull() ?: Float.NaN,
            p2ShowBackground = p2ShowBackground
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

            // ── Sektion 1: ioBroker Adapter Verbindung ────────────────────────
            AccordionSection(
                title = "ioBroker Adapter Verbindung",
                expanded = sectionAdapter,
                onToggle = { sectionAdapter = !sectionAdapter }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (uiState.useIoSyncAdapter) "IoSync Adapter" else "Simple-API", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (uiState.useIoSyncAdapter) "Nutzt den IoSync ioBroker-Adapter" else "Nutzt die ioBroker Simple-API direkt",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = uiState.useIoSyncAdapter, onCheckedChange = { viewModel.updateDataSourceToggle(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1A1A00), checkedTrackColor = NeonYellow))
                }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("IoSync Adapter", style = MaterialTheme.typography.titleSmall,
                    color = if (uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Text("Verbindung zum IoSync ioBroker-Adapter. Lädt Datenpunkte für App und Watchface.", style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                OutlinedTextField(value = ioSyncHost, onValueChange = { ioSyncHost = it }, label = { Text("Adapter-Host / IP") }, placeholder = { Text("192.168.1.100") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProtocolSelector(useHttps = ioSyncUseHttps, onSelect = { ioSyncUseHttps = it }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = ioSyncPort, onValueChange = { ioSyncPort = it.filter { c -> c.isDigit() } }, label = { Text("Port") }, placeholder = { Text("345") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = ioSyncUsername, onValueChange = { ioSyncUsername = it }, label = { Text("Benutzername") }, modifier = Modifier.weight(2f), singleLine = true)
                }
                OutlinedTextField(value = ioSyncPassword, onValueChange = { ioSyncPassword = it }, label = { Text("Passwort") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(
                    onClick = { viewModel.updateIoSyncSettings(host = ioSyncHost.trim(), port = ioSyncPort.toIntOrNull() ?: 345, useHttps = ioSyncUseHttps, username = ioSyncUsername.trim(), password = ioSyncPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = Color(0xFF1A1A00))
                ) { Text("Speichern & Verbinden", style = MaterialTheme.typography.labelLarge) }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("ioBroker Simple-API", style = MaterialTheme.typography.titleSmall,
                    color = if (!uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Text("Nutzt die ioBroker Simple-API direkt für Datenpunkte.", style = MaterialTheme.typography.bodySmall,
                    color = if (!uiState.useIoSyncAdapter) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host / IP-Adresse") }, placeholder = { Text("192.168.1.100") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("Port (Simple-API)") }, placeholder = { Text("8082") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(
                    onClick = { viewModel.updateConnectionSettings(host = host.trim(), port = port.toIntOrNull() ?: 8082) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (!uiState.useIoSyncAdapter) NeonYellow else Color(0xFF333333), contentColor = if (!uiState.useIoSyncAdapter) Color(0xFF1A1A00) else Color(0xFFAAAAAA))
                ) { Text("Simple-API speichern", style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.height(4.dp))

            // ── Sektion 2: Erste Seite Watchface ──────────────────────────────
            AccordionSection(
                title = "Erste Seite Watchface",
                expanded = sectionPage1,
                onToggle = { sectionPage1 = !sectionPage1 }
            ) {

            WatchFaceToggleRow(
                text = "Hintergrundbild anzeigen",
                subText = "Skeuomorphes Metalldesign als Zifferblatt-Hintergrund",
                checked = wfShowBackground,
                onCheckedChange = { wfShowBackground = it }
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

            // ── Akku-Ring Farbverlauf ─────────────────────────────────────────
            Text(
                text = "Akku-Ring: Startfarbe (Uhrzeit 12)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(color = Color(0xFF00BCD4), label = "Cyan",       selected = wfBatteryRingColor1 == "cyan",        onClick = { wfBatteryRingColor1 = "cyan" })
                WatchFaceColorChip(color = Color(0xFFEAFF00), label = "Neon Gelb",  selected = wfBatteryRingColor1 == "neon_yellow", onClick = { wfBatteryRingColor1 = "neon_yellow" })
                WatchFaceColorChip(color = Color.White,       label = "Weiß",       selected = wfBatteryRingColor1 == "white",       onClick = { wfBatteryRingColor1 = "white" })
                WatchFaceColorChip(color = Color(0xFF4CAF50), label = "Grün",       selected = wfBatteryRingColor1 == "green",       onClick = { wfBatteryRingColor1 = "green" })
                WatchFaceColorChip(color = Color(0xFFFF9800), label = "Orange",     selected = wfBatteryRingColor1 == "orange",      onClick = { wfBatteryRingColor1 = "orange" })
                WatchFaceColorChip(color = Color(0xFF9C27B0), label = "Lila",       selected = wfBatteryRingColor1 == "purple",      onClick = { wfBatteryRingColor1 = "purple" })
            }

            Text(
                text = "Akku-Ring: Endfarbe (Füllstand-Ende)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(color = Color(0xFFEAFF00), label = "Neon Gelb",  selected = wfBatteryRingColor2 == "neon_yellow", onClick = { wfBatteryRingColor2 = "neon_yellow" })
                WatchFaceColorChip(color = Color(0xFF00BCD4), label = "Cyan",       selected = wfBatteryRingColor2 == "cyan",        onClick = { wfBatteryRingColor2 = "cyan" })
                WatchFaceColorChip(color = Color.White,       label = "Weiß",       selected = wfBatteryRingColor2 == "white",       onClick = { wfBatteryRingColor2 = "white" })
                WatchFaceColorChip(color = Color(0xFF4CAF50), label = "Grün",       selected = wfBatteryRingColor2 == "green",       onClick = { wfBatteryRingColor2 = "green" })
                WatchFaceColorChip(color = Color(0xFFFF9800), label = "Orange",     selected = wfBatteryRingColor2 == "orange",      onClick = { wfBatteryRingColor2 = "orange" })
                WatchFaceColorChip(color = Color(0xFF9C27B0), label = "Lila",       selected = wfBatteryRingColor2 == "purple",      onClick = { wfBatteryRingColor2 = "purple" })
            }

            Text(
                text = "Akku-Ring Breite: ${wfBatteryRingStrokeScale.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = wfBatteryRingStrokeScale,
                onValueChange = { wfBatteryRingStrokeScale = it },
                valueRange = 30f..200f,
                steps = 16,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // ── Akku-Ring Warnstufen ───────────────────────────────────────────
            Text(
                text = "Akku-Ring Warnstufe 1: " +
                    if (wfBatteryWarn1Threshold.toInt() == 0) "aus" else "unter ${wfBatteryWarn1Threshold.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = wfBatteryWarn1Threshold,
                onValueChange = { wfBatteryWarn1Threshold = it },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(color = Color(0xFFFF9800), label = "Orange",     selected = wfBatteryWarn1Color == "orange",      onClick = { wfBatteryWarn1Color = "orange" })
                WatchFaceColorChip(color = Color(0xFFF44336), label = "Rot",        selected = wfBatteryWarn1Color == "red",         onClick = { wfBatteryWarn1Color = "red" })
                WatchFaceColorChip(color = Color(0xFFEAFF00), label = "Neon Gelb",  selected = wfBatteryWarn1Color == "neon_yellow", onClick = { wfBatteryWarn1Color = "neon_yellow" })
                WatchFaceColorChip(color = Color(0xFF9C27B0), label = "Lila",       selected = wfBatteryWarn1Color == "purple",      onClick = { wfBatteryWarn1Color = "purple" })
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Akku-Ring Warnstufe 2: " +
                    if (wfBatteryWarn2Threshold.toInt() == 0) "aus" else "unter ${wfBatteryWarn2Threshold.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = wfBatteryWarn2Threshold,
                onValueChange = { wfBatteryWarn2Threshold = it },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchFaceColorChip(color = Color(0xFFF44336), label = "Rot",        selected = wfBatteryWarn2Color == "red",         onClick = { wfBatteryWarn2Color = "red" })
                WatchFaceColorChip(color = Color(0xFFFF9800), label = "Orange",     selected = wfBatteryWarn2Color == "orange",      onClick = { wfBatteryWarn2Color = "orange" })
                WatchFaceColorChip(color = Color(0xFFEAFF00), label = "Neon Gelb",  selected = wfBatteryWarn2Color == "neon_yellow", onClick = { wfBatteryWarn2Color = "neon_yellow" })
                WatchFaceColorChip(color = Color(0xFF9C27B0), label = "Lila",       selected = wfBatteryWarn2Color == "purple",      onClick = { wfBatteryWarn2Color = "purple" })
            }

            Spacer(Modifier.height(8.dp))

            // ── Aktualisierungsintervalle ──────────────────────────────────────
            Text(
                text = "Aktualisierungsintervalle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Akku-Sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IntervalDropdown(
                    selected = batteryPollInterval,
                    onSelect = { batteryPollInterval = it },
                    modifier = Modifier.width(120.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Seite 1 Sync-Intervall",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IntervalDropdown(
                    selected = slotPollInterval,
                    onSelect = { slotPollInterval = it },
                    modifier = Modifier.width(120.dp),
                    options = PAGE_SYNC_INTERVAL_OPTIONS_SEC
                )
            }

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
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("Temperaturquelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("openweather" to "OpenWeather API", "iobroker" to "ioBroker Datenpunkt").forEach { (src, label) ->
                        OutlinedButton(
                            onClick = { weatherTempSource = src },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (weatherTempSource == src) NeonYellow.copy(alpha = 0.15f) else Color.Transparent),
                            border = BorderStroke(1.dp, if (weatherTempSource == src) NeonYellow else Color(0xFF444444))
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                if (weatherTempSource == "iobroker") {
                    Text("ioBroker Datenpunkt für Temperatur (Standort bleibt aktiv für Wettericons)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val wtStates = uiState.ioSyncStates.ifEmpty { uiState.states }
                    DatapointDropdown(selectedId = weatherIoBrokerId, availableStates = wtStates, onSelect = { weatherIoBrokerId = it }, modifier = Modifier.fillMaxWidth())
                }
            }

            WatchFaceToggleRow(
                text = "Puls anzeigen",
                subText = "Herzfrequenz auf dem Watchface",
                checked = wfShowHeartRate,
                onCheckedChange = { wfShowHeartRate = it }
            )
            if (wfShowHeartRate) {
                HealthSourcePerTypeRow(
                    label = "Puls-Quelle",
                    source = wfHrSource,
                    onSourceChange = { wfHrSource = it },
                    defaultHcTypeKey = "heart_rate",
                    hcSourcePkg = wfHrComplication,
                    onHcSourcePkgChange = { wfHrComplication = it },
                    availableHcTypes = uiState.healthConnectStatus.dataTypes.filter { it.available }
                )
                Text("Puls-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceColorRow(selected = wfHrColor, onSelect = { wfHrColor = it })
            }

            WatchFaceToggleRow(
                text = "Kalorien anzeigen",
                subText = "Kalorien auf dem Watchface",
                checked = wfShowCalories,
                onCheckedChange = { wfShowCalories = it }
            )
            if (wfShowCalories) {
                HealthSourcePerTypeRow(
                    label = "Kcal-Quelle",
                    source = wfKcalSource,
                    onSourceChange = { wfKcalSource = it },
                    defaultHcTypeKey = "total_calories",
                    hcSourcePkg = wfKcalComplication,
                    onHcSourcePkgChange = { wfKcalComplication = it },
                    availableHcTypes = uiState.healthConnectStatus.dataTypes.filter { it.available }
                )
                if (wfKcalSource == "healthconnect") {
                    Text("Angezeigter Wert (mit Vorschau)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GenericValueDropdown(
                        options = uiState.healthConnectStatus.dataTypes
                            .filter { it.available }
                            .map { it.key to "${it.displayName}: ${it.latestValue ?: "--"}" },
                        selected = wfKcalMetric,
                        onSelect = { wfKcalMetric = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("Kcal-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceColorRow(selected = wfKcalColor, onSelect = { wfKcalColor = it })
            }

            WatchFaceToggleRow(
                text = "Oxygen (SpO2) anzeigen",
                subText = "Sauerstoffsättigung auf dem Watchface",
                checked = wfShowOxygen,
                onCheckedChange = { wfShowOxygen = it }
            )
            if (wfShowOxygen) {
                HealthSourcePerTypeRow(
                    label = "SpO2-Quelle",
                    source = wfOxygenSource,
                    onSourceChange = { wfOxygenSource = it },
                    defaultHcTypeKey = "oxygen_saturation",
                    hcSourcePkg = wfOxygenComplication,
                    onHcSourcePkgChange = { wfOxygenComplication = it },
                    availableHcTypes = uiState.healthConnectStatus.dataTypes.filter { it.available }
                )
                if (wfOxygenSource == "healthconnect") {
                    Text("Angezeigter Wert (mit Vorschau)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GenericValueDropdown(
                        options = uiState.healthConnectStatus.dataTypes
                            .filter { it.available }
                            .map { it.key to "${it.displayName}: ${it.latestValue ?: "--"}" },
                        selected = wfOxygenMetric,
                        onSelect = { wfOxygenMetric = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("SpO2-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceColorRow(selected = wfOxygenColor, onSelect = { wfOxygenColor = it })
            }

            // Abfrage-Intervall für Health-Connect-Werte (Puls/Kcal/SpO2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Health-Intervall (Puls/Kcal)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IntervalDropdown(
                    selected = healthPollInterval,
                    onSelect = { healthPollInterval = it },
                    modifier = Modifier.width(120.dp),
                    options = HEALTH_INTERVAL_OPTIONS_SEC
                )
            }

            // Speichern-Button für Health-Quellen
            Button(
                onClick = {
                    viewModel.updateHealthSourceConfig(
                        hrSource = wfHrSource,
                        kcalSource = wfKcalSource,
                        oxygenSource = wfOxygenSource,
                        hrComplication = wfHrComplication,
                        kcalComplication = wfKcalComplication,
                        oxygenComplication = wfOxygenComplication,
                        kcalMetric = wfKcalMetric,
                        oxygenMetric = wfOxygenMetric
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = Color(0xFF1A1A00)
                )
            ) {
                Text("Health-Quellen speichern & übertragen", style = MaterialTheme.typography.labelLarge)
            }

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

                Text("Slot-Wertfarbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceColorRow(selected = wfSlotColor, onSelect = { wfSlotColor = it })

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

                // ── Balken-Warnstufen ──────────────────────────────────────
                Text(
                    "Warnstufe 1 (leer = aus). Balken wird unter diesem Wert eingefärbt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSlot4Warn1Value,
                        onValueChange = { customSlot4Warn1Value = it },
                        label = { Text("Schwelle 1") },
                        placeholder = { Text("z.B. 20") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = customSlot4Warn1Color == "orange",      onClick = { customSlot4Warn1Color = "orange" })
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = customSlot4Warn1Color == "red",         onClick = { customSlot4Warn1Color = "red" })
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = customSlot4Warn1Color == "neon_yellow", onClick = { customSlot4Warn1Color = "neon_yellow" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = customSlot4Warn1Color == "purple",      onClick = { customSlot4Warn1Color = "purple" })
                }
                Text(
                    "Warnstufe 2 (leer = aus). Niedrigere Schwelle hat Vorrang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSlot4Warn2Value,
                        onValueChange = { customSlot4Warn2Value = it },
                        label = { Text("Schwelle 2") },
                        placeholder = { Text("z.B. 10") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = customSlot4Warn2Color == "red",         onClick = { customSlot4Warn2Color = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = customSlot4Warn2Color == "orange",      onClick = { customSlot4Warn2Color = "orange" })
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = customSlot4Warn2Color == "neon_yellow", onClick = { customSlot4Warn2Color = "neon_yellow" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = customSlot4Warn2Color == "purple",      onClick = { customSlot4Warn2Color = "purple" })
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
                            slot4TextScale    = wfSlot4TextScale,
                            slot4Warn1Color   = customSlot4Warn1Color,
                            slot4Warn1Value   = customSlot4Warn1Value.toFloatOrNull() ?: Float.NaN,
                            slot4Warn2Color   = customSlot4Warn2Color,
                            slot4Warn2Value   = customSlot4Warn2Value.toFloatOrNull() ?: Float.NaN
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

            HorizontalDivider(color = Color(0xFF2A2A2A))

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

            // ── Schriftgröße Gesundheitsdaten & Wetter ─────────────────────
            HorizontalDivider(color = Color(0xFF2A2A2A))
            Text(
                text = "Schriftgröße – Wetter, Sonne, Puls & Kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wetter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfWeatherTextScale, onSelect = { wfWeatherTextScale = it })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sonnenauf/-untergang", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSunriseTextScale, onSelect = { wfSunriseTextScale = it })
                }
            }
            Text("Sonnenauf/-untergang Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WatchFaceColorRow(selected = wfSunriseColor, onSelect = { wfSunriseColor = it })

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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Akku-Ringe (Uhr)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfWatchBatteryTextScale, onSelect = { wfWatchBatteryTextScale = it })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Schritte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfStepsTextScale, onSelect = { wfStepsTextScale = it })
                }
            }
            Text("Schritte-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WatchFaceColorRow(selected = wfStepsColor, onSelect = { wfStepsColor = it })

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
                        showSteps          = wfShowSteps,
                        showCustomSlots    = showCustomSlots,
                        customSlot1Label   = customSlot1Label.trim(),
                        customSlot2Label   = customSlot2Label.trim(),
                        hrTextScale        = wfHrTextScale,
                        kcalTextScale      = wfKcalTextScale,
                        stepsTextScale     = wfStepsTextScale,
                        slot1TextScale     = wfSlot1TextScale,
                        slot2TextScale     = wfSlot2TextScale,
                        slot3TextScale     = wfSlot3TextScale,
                        slot4TextScale     = wfSlot4TextScale,
                        weatherTextScale   = wfWeatherTextScale,
                        sunriseTextScale   = wfSunriseTextScale,
                        watchBatteryTextScale = wfWatchBatteryTextScale,
                        batteryRingColor1       = wfBatteryRingColor1,
                        batteryRingColor2       = wfBatteryRingColor2,
                        batteryRingStrokeScale  = wfBatteryRingStrokeScale.toInt(),
                        batteryWarn1Color       = wfBatteryWarn1Color,
                        batteryWarn1Threshold   = wfBatteryWarn1Threshold.toInt(),
                        batteryWarn2Color       = wfBatteryWarn2Color,
                        batteryWarn2Threshold   = wfBatteryWarn2Threshold.toInt(),
                        healthDataSource        = wfHealthDataSource,
                        hrColor                 = wfHrColor,
                        kcalColor               = wfKcalColor,
                        oxygenColor             = wfOxygenColor,
                        stepsColor              = wfStepsColor,
                        sleepColor              = wfSleepColor,
                        sunriseColor            = wfSunriseColor,
                        slotColor               = wfSlotColor,
                        weatherTempSource       = weatherTempSource,
                        weatherIoBrokerId       = weatherIoBrokerId
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

            } // end AccordionSection "Erste Seite Watchface"

            Spacer(Modifier.height(4.dp))

            // ── Sektion 3: Zweite Seite Watchface ─────────────────────────────
            AccordionSection(
                title = "Zweite Seite Watchface",
                expanded = sectionPage2,
                onToggle = { sectionPage2 = !sectionPage2 }
            ) {
                val p2States = uiState.ioSyncStates.ifEmpty { uiState.states }

                Text("4 Datenpunkte auf der zweiten Watchface-Seite (Doppeltipp 9 Uhr zum Öffnen)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                WatchFaceToggleRow(text = "Hintergrundbild anzeigen", subText = "Skeuomorphes Metalldesign als Hintergrund auf Seite 2", checked = p2ShowBackground, onCheckedChange = { p2ShowBackground = it })

                Text("Slot 1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2Slot1Label, onValueChange = { if (it.length <= 6) p2Slot1Label = it }, label = { Text("Name") }, placeholder = { Text("TEMP") }, modifier = Modifier.weight(1f), singleLine = true)
                    DatapointDropdown(selectedId = p2Slot1Id, availableStates = p2States, onSelect = { p2Slot1Id = it }, modifier = Modifier.weight(3f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = p2Slot1TextScale, onSelect = { p2Slot1TextScale = it })
                }

                Text("Slot 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2Slot2Label, onValueChange = { if (it.length <= 6) p2Slot2Label = it }, label = { Text("Name") }, placeholder = { Text("HUM") }, modifier = Modifier.weight(1f), singleLine = true)
                    DatapointDropdown(selectedId = p2Slot2Id, availableStates = p2States, onSelect = { p2Slot2Id = it }, modifier = Modifier.weight(3f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = p2Slot2TextScale, onSelect = { p2Slot2TextScale = it })
                }

                Text("Slot 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2Slot3Label, onValueChange = { if (it.length <= 6) p2Slot3Label = it }, label = { Text("Name") }, placeholder = { Text("CO2") }, modifier = Modifier.weight(1f), singleLine = true)
                    DatapointDropdown(selectedId = p2Slot3Id, availableStates = p2States, onSelect = { p2Slot3Id = it }, modifier = Modifier.weight(3f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = p2Slot3TextScale, onSelect = { p2Slot3TextScale = it })
                }

                Text("Slot 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2Slot4Label, onValueChange = { if (it.length <= 6) p2Slot4Label = it }, label = { Text("Name") }, placeholder = { Text("PWR") }, modifier = Modifier.weight(1f), singleLine = true)
                    DatapointDropdown(selectedId = p2Slot4Id, availableStates = p2States, onSelect = { p2Slot4Id = it }, modifier = Modifier.weight(3f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = p2Slot4TextScale, onSelect = { p2Slot4TextScale = it })
                }

                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("Schlafdauer (oben auf Seite 2)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Datenquelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("healthconnect" to "Health Connect", "iobroker" to "ioBroker (Lokal)").forEach { (src, label) ->
                        OutlinedButton(
                            onClick = { wfSleepSource = src },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (wfSleepSource == src) NeonYellow.copy(alpha = 0.15f) else Color.Transparent),
                            border = BorderStroke(1.dp, if (wfSleepSource == src) NeonYellow else Color(0xFF444444))
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                if (wfSleepSource == "iobroker") {
                    Text("ioBroker-Datenpunkt (Schlafdauer in Minuten)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DatapointDropdown(selectedId = wfSleepIoBrokerId, availableStates = p2States, onSelect = { wfSleepIoBrokerId = it }, modifier = Modifier.fillMaxWidth())
                }
                if (wfSleepSource == "healthconnect") {
                    val sleepTypeInfo = uiState.healthConnectStatus.dataTypes.find { it.key == "sleep" && it.available }
                    if (sleepTypeInfo != null && sleepTypeInfo.sourcePackages.isNotEmpty()) {
                        Text("Datenquelle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HealthConnectSourceDropdown(
                            labels = sleepTypeInfo.sources,
                            packages = sleepTypeInfo.sourcePackages,
                            selectedPkg = wfSleepComplication,
                            onSelect = { wfSleepComplication = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text("Schlafdauer-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceColorRow(selected = wfSleepColor, onSelect = { wfSleepColor = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = wfSleepTextScale, onSelect = { wfSleepTextScale = it })
                }

                HorizontalDivider(color = Color(0xFF2A2A2A))
                // ── Pille 1 (7 Uhr) ───────────────────────────────────────────
                Text("Pille 1 (7 Uhr)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceToggleRow(text = "Pille 1 aktivieren", subText = "Tipp auf Pille bei 7 Uhr sendet Aktion an ioBroker", checked = p2PillEnabled, onCheckedChange = { p2PillEnabled = it })
                if (p2PillEnabled) {
                    Text("ioBroker-Datenpunkt (Ziel der Aktion)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DatapointDropdown(selectedId = p2PillIoBrokerId, availableStates = p2States, onSelect = { p2PillIoBrokerId = it }, modifier = Modifier.fillMaxWidth())
                    Text("Aktionsmodus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("toggle" to "Umschalten", "fixed" to "Fester Wert").forEach { (mode, label) ->
                            OutlinedButton(onClick = { p2PillValueMode = mode }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (p2PillValueMode == mode) NeonYellow.copy(alpha = 0.15f) else Color.Transparent),
                                border = BorderStroke(1.dp, if (p2PillValueMode == mode) NeonYellow else Color(0xFF444444))
                            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (p2PillValueMode == "fixed") {
                        OutlinedTextField(value = p2PillFixedValue, onValueChange = { p2PillFixedValue = it }, label = { Text("Fester Wert") }, placeholder = { Text("true / 1 / ein") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Text("Farbe wenn aktiv (true)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PillColorChip(color = Color(0xFF00BCD4), label = "Cyan",   selected = p2PillColorTrue == "cyan",        onClick = { p2PillColorTrue = "cyan" })
                        PillColorChip(color = Color(0xFF4CAF50), label = "Grün",   selected = p2PillColorTrue == "green",       onClick = { p2PillColorTrue = "green" })
                        PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = p2PillColorTrue == "neon_yellow", onClick = { p2PillColorTrue = "neon_yellow" })
                        PillColorChip(color = Color.White,       label = "Weiß",   selected = p2PillColorTrue == "white",       onClick = { p2PillColorTrue = "white" })
                        PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2PillColorTrue == "red",         onClick = { p2PillColorTrue = "red" })
                        PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2PillColorTrue == "orange",      onClick = { p2PillColorTrue = "orange" })
                        PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2PillColorTrue == "purple",      onClick = { p2PillColorTrue = "purple" })
                    }
                    Text("Farbe wenn inaktiv (false)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2PillColorFalse == "red",        onClick = { p2PillColorFalse = "red" })
                        PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2PillColorFalse == "orange",     onClick = { p2PillColorFalse = "orange" })
                        PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2PillColorFalse == "purple",     onClick = { p2PillColorFalse = "purple" })
                        PillColorChip(color = Color(0xFF888888), label = "Grau",   selected = p2PillColorFalse == "light_gray", onClick = { p2PillColorFalse = "light_gray" })
                    }
                    // ── Pille 1 jetzt schalten ────────────────────────────────
                    DoubleTapPillButton(
                        label       = "Pille 1 schalten",
                        hint        = "Doppeltipp = Befehl senden",
                        pillState   = uiState.p2Pill1State,
                        colorTrue   = uiState.p2PillColorTrue,
                        colorFalse  = uiState.p2PillColorFalse,
                        onDoubleTap = { viewModel.toggleP2Pill1FromApp() }
                    )
                }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                // ── Pille 2 (5 Uhr) ───────────────────────────────────────────
                Text("Pille 2 (5 Uhr)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatchFaceToggleRow(text = "Pille 2 aktivieren", subText = "Tipp auf Pille bei 5 Uhr sendet Aktion an ioBroker", checked = p2Pill2Enabled, onCheckedChange = { p2Pill2Enabled = it })
                if (p2Pill2Enabled) {
                    Text("ioBroker-Datenpunkt (Ziel der Aktion)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DatapointDropdown(selectedId = p2Pill2IoBrokerId, availableStates = p2States, onSelect = { p2Pill2IoBrokerId = it }, modifier = Modifier.fillMaxWidth())
                    Text("Aktionsmodus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("toggle" to "Umschalten", "fixed" to "Fester Wert").forEach { (mode, label) ->
                            OutlinedButton(onClick = { p2Pill2ValueMode = mode }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (p2Pill2ValueMode == mode) NeonYellow.copy(alpha = 0.15f) else Color.Transparent),
                                border = BorderStroke(1.dp, if (p2Pill2ValueMode == mode) NeonYellow else Color(0xFF444444))
                            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (p2Pill2ValueMode == "fixed") {
                        OutlinedTextField(value = p2Pill2FixedValue, onValueChange = { p2Pill2FixedValue = it }, label = { Text("Fester Wert") }, placeholder = { Text("true / 1 / ein") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Text("Farbe wenn aktiv (true)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PillColorChip(color = Color(0xFF00BCD4), label = "Cyan",   selected = p2Pill2ColorTrue == "cyan",        onClick = { p2Pill2ColorTrue = "cyan" })
                        PillColorChip(color = Color(0xFF4CAF50), label = "Grün",   selected = p2Pill2ColorTrue == "green",       onClick = { p2Pill2ColorTrue = "green" })
                        PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = p2Pill2ColorTrue == "neon_yellow", onClick = { p2Pill2ColorTrue = "neon_yellow" })
                        PillColorChip(color = Color.White,       label = "Weiß",   selected = p2Pill2ColorTrue == "white",       onClick = { p2Pill2ColorTrue = "white" })
                        PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2Pill2ColorTrue == "red",         onClick = { p2Pill2ColorTrue = "red" })
                        PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2Pill2ColorTrue == "orange",      onClick = { p2Pill2ColorTrue = "orange" })
                        PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2Pill2ColorTrue == "purple",      onClick = { p2Pill2ColorTrue = "purple" })
                    }
                    Text("Farbe wenn inaktiv (false)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2Pill2ColorFalse == "red",        onClick = { p2Pill2ColorFalse = "red" })
                        PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2Pill2ColorFalse == "orange",     onClick = { p2Pill2ColorFalse = "orange" })
                        PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2Pill2ColorFalse == "purple",     onClick = { p2Pill2ColorFalse = "purple" })
                        PillColorChip(color = Color(0xFF888888), label = "Grau",   selected = p2Pill2ColorFalse == "light_gray", onClick = { p2Pill2ColorFalse = "light_gray" })
                    }
                    // ── Pille 2 jetzt schalten ────────────────────────────────
                    DoubleTapPillButton(
                        label       = "Pille 2 schalten",
                        hint        = "Doppeltipp = Befehl senden",
                        pillState   = uiState.p2Pill2State,
                        colorTrue   = uiState.p2Pill2ColorTrue,
                        colorFalse  = uiState.p2Pill2ColorFalse,
                        onDoubleTap = { viewModel.toggleP2Pill2FromApp() }
                    )
                }

                // Vertikaler Balken (p2Bar)
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("Vertikaler Balken-Graph (rechts, 3 Uhr)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Zeigt einen Wert als vertikalen Balken rechts auf Seite 2. Beschriftung links.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2BarLabel, onValueChange = { if (it.length <= 6) p2BarLabel = it }, label = { Text("Name") }, placeholder = { Text("TEMP") }, modifier = Modifier.weight(1f), singleLine = true)
                    DatapointDropdown(selectedId = p2BarId, availableStates = p2States, onSelect = { p2BarId = it }, modifier = Modifier.weight(3f))
                }
                Text("Balken-Farbe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = p2BarColor == "neon_yellow", onClick = { p2BarColor = "neon_yellow" })
                    PillColorChip(color = Color(0xFF00BCD4), label = "Cyan",   selected = p2BarColor == "cyan",        onClick = { p2BarColor = "cyan" })
                    PillColorChip(color = Color(0xFF4CAF50), label = "Grün",   selected = p2BarColor == "green",       onClick = { p2BarColor = "green" })
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2BarColor == "red",         onClick = { p2BarColor = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2BarColor == "orange",      onClick = { p2BarColor = "orange" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2BarColor == "purple",      onClick = { p2BarColor = "purple" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p2BarMin, onValueChange = { p2BarMin = it }, label = { Text("Min-Wert") }, placeholder = { Text("0") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = p2BarMax, onValueChange = { p2BarMax = it }, label = { Text("Max-Wert") }, placeholder = { Text("100") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                WatchFaceToggleRow(text = "Beschriftung anzeigen", subText = "Name, Min, Max und Wert links vom Balken", checked = p2BarShowLabel, onCheckedChange = { p2BarShowLabel = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schriftgröße:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FontSizeDropdown(selected = p2BarTextScale, onSelect = { p2BarTextScale = it })
                }
                Text("Warnstufe 1 (leer = aus). Balken wird unter diesem Wert eingefärbt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = p2BarWarn1Value, onValueChange = { p2BarWarn1Value = it }, label = { Text("Schwelle 1") }, placeholder = { Text("z.B. 20") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2BarWarn1Color == "orange",      onClick = { p2BarWarn1Color = "orange" })
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2BarWarn1Color == "red",         onClick = { p2BarWarn1Color = "red" })
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = p2BarWarn1Color == "neon_yellow", onClick = { p2BarWarn1Color = "neon_yellow" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2BarWarn1Color == "purple",      onClick = { p2BarWarn1Color = "purple" })
                }
                Text("Warnstufe 2 (leer = aus). Niedrigere Schwelle hat Vorrang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = p2BarWarn2Value, onValueChange = { p2BarWarn2Value = it }, label = { Text("Schwelle 2") }, placeholder = { Text("z.B. 10") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillColorChip(color = Color(0xFFF44336), label = "Rot",    selected = p2BarWarn2Color == "red",         onClick = { p2BarWarn2Color = "red" })
                    PillColorChip(color = Color(0xFFFF9800), label = "Orange", selected = p2BarWarn2Color == "orange",      onClick = { p2BarWarn2Color = "orange" })
                    PillColorChip(color = Color(0xFFEAFF00), label = "Gelb",   selected = p2BarWarn2Color == "neon_yellow", onClick = { p2BarWarn2Color = "neon_yellow" })
                    PillColorChip(color = Color(0xFF9C27B0), label = "Lila",   selected = p2BarWarn2Color == "purple",      onClick = { p2BarWarn2Color = "purple" })
                }

                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text(
                    text = "Aktualisierungsintervall",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Seite 2 Sync-Intervall",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IntervalDropdown(
                        selected = page2SyncInterval,
                        onSelect = { page2SyncInterval = it },
                        modifier = Modifier.width(120.dp),
                        options = PAGE_SYNC_INTERVAL_OPTIONS_SEC
                    )
                }

                Button(
                    onClick = {
                        viewModel.updatePage2Config(
                            slot1Id = p2Slot1Id.trim(), slot1Label = p2Slot1Label.trim(),
                            slot2Id = p2Slot2Id.trim(), slot2Label = p2Slot2Label.trim(),
                            slot3Id = p2Slot3Id.trim(), slot3Label = p2Slot3Label.trim(),
                            slot4Id = p2Slot4Id.trim(), slot4Label = p2Slot4Label.trim(),
                            slot1TextScale = p2Slot1TextScale, slot2TextScale = p2Slot2TextScale,
                            slot3TextScale = p2Slot3TextScale, slot4TextScale = p2Slot4TextScale,
                            sleepTextScale = wfSleepTextScale,
                            sleepSource = wfSleepSource, sleepIoBrokerId = wfSleepIoBrokerId.trim(),
                            sleepComplication = wfSleepComplication,
                            p2PillEnabled = p2PillEnabled, p2PillColorTrue = p2PillColorTrue,
                            p2PillColorFalse = p2PillColorFalse, p2PillIoBrokerId = p2PillIoBrokerId.trim(),
                            p2PillValueMode = p2PillValueMode, p2PillFixedValue = p2PillFixedValue.trim(),
                            p2Pill2Enabled = p2Pill2Enabled, p2Pill2ColorTrue = p2Pill2ColorTrue,
                            p2Pill2ColorFalse = p2Pill2ColorFalse, p2Pill2IoBrokerId = p2Pill2IoBrokerId.trim(),
                            p2Pill2ValueMode = p2Pill2ValueMode, p2Pill2FixedValue = p2Pill2FixedValue.trim(),
                            p2BarId = p2BarId.trim(), p2BarLabel = p2BarLabel.trim(),
                            p2BarColor = p2BarColor,
                            p2BarMin = p2BarMin.toFloatOrNull() ?: 0f,
                            p2BarMax = p2BarMax.toFloatOrNull() ?: 100f,
                            p2BarShowLabel = p2BarShowLabel,
                            p2BarTextScale = p2BarTextScale,
                            p2BarWarn1Color = p2BarWarn1Color,
                            p2BarWarn1Value = p2BarWarn1Value.toFloatOrNull() ?: Float.NaN,
                            p2BarWarn2Color = p2BarWarn2Color,
                            p2BarWarn2Value = p2BarWarn2Value.toFloatOrNull() ?: Float.NaN,
                            p2ShowBackground = p2ShowBackground
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonYellow, contentColor = Color(0xFF1A1A00))
                ) { Text("Seite 2 speichern & übertragen", style = MaterialTheme.typography.labelLarge) }
                if (uiState.wearSyncLog.isNotBlank()) {
                    Text(text = uiState.wearSyncLog, style = MaterialTheme.typography.labelSmall, color = if (uiState.wearSyncLog.startsWith("Fehler")) Color(0xFFF44336) else Color(0xFF4CAF50), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                }
            } // end AccordionSection "Zweite Seite Watchface"

            Spacer(Modifier.height(4.dp))

            // ── Sektion 4: Health Connect Verbindung ──────────────────────────
            AccordionSection(
                title = "Health Connect Verbindung",
                expanded = sectionHealth,
                onToggle = { sectionHealth = !sectionHealth }
            ) {
                HealthConnectSection(viewModel = viewModel, uiState = uiState)
            }

            Spacer(Modifier.height(4.dp))

            // ── Sektion 5: NTP-Zeitkorrektur ──────────────────────────────────
            AccordionSection(
                title = "Zeitkorrektur (NTP)",
                expanded = sectionNtp,
                onToggle = { sectionNtp = !sectionNtp }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exakte Uhrzeit (NTP)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Das Watchface ermittelt per NTP einen Offset zur Systemzeit und zeigt damit die exakte Zeit. Der Offset wird alle 30 Minuten aktualisiert.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ntpEnabled,
                        onCheckedChange = {
                            ntpEnabled = it
                            viewModel.setNtpCorrection(it, ntpServer)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1A1A00), checkedTrackColor = NeonYellow)
                    )
                }
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Text("NTP-Server", style = MaterialTheme.typography.titleSmall,
                    color = if (ntpEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = ntpServer == "de.pool.ntp.org",
                        enabled = ntpEnabled,
                        onClick = { ntpServer = "de.pool.ntp.org"; viewModel.setNtpCorrection(ntpEnabled, "de.pool.ntp.org") }
                    )
                    Text("Deutsche Zeit (de.pool.ntp.org)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = ntpServer == "pool.ntp.org",
                        enabled = ntpEnabled,
                        onClick = { ntpServer = "pool.ntp.org"; viewModel.setNtpCorrection(ntpEnabled, "pool.ntp.org") }
                    )
                    Text("Automatisch (pool.ntp.org)", style = MaterialTheme.typography.bodyMedium)
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
                Text(com.iosync.app.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
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

            // ── Crash-Log ─────────────────────────────────────────────────────
            val ctx = LocalContext.current
            var crashLogs by remember { mutableStateOf(CrashLogManager.readAllCrashLogs(ctx)) }
            if (crashLogs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Crash-Logs (${crashLogs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFF44336)
                )
                crashLogs.forEach { (name, content) ->
                    var expanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .background(Color(0xFF1A0000), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Bold
                        )
                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = content,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 80
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Button(
                    onClick = {
                        CrashLogManager.clearCrashLogs(ctx)
                        crashLogs = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crash-Logs löschen")
                }
            }
        }
    }
}

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = NeonYellow, fontWeight = FontWeight.Bold)
            Text(if (expanded) "▲" else "▼", color = NeonYellow, style = MaterialTheme.typography.bodyMedium)
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
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

/** Kompakte Farb-Chip-Zeile für konfigurierbare Watchface-Farben. */
@Composable
private fun WatchFaceColorRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        WatchFaceColorChip(Color(0xFFEAFF00), "Neon-Gelb", selected == "neon_yellow") { onSelect("neon_yellow") }
        WatchFaceColorChip(Color(0xFF00BCD4), "Cyan",      selected == "cyan")        { onSelect("cyan") }
        WatchFaceColorChip(Color(0xFFF44336), "Rot",       selected == "red")         { onSelect("red") }
        WatchFaceColorChip(Color(0xFFFF9800), "Orange",    selected == "orange")      { onSelect("orange") }
        WatchFaceColorChip(Color(0xFF9C27B0), "Lila",      selected == "purple")      { onSelect("purple") }
        WatchFaceColorChip(Color(0xFF4CAF50), "Grün",      selected == "green")       { onSelect("green") }
        WatchFaceColorChip(Color.White,       "Weiß",      selected == "white")       { onSelect("white") }
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

// Intervall-Optionen in Sekunden (allgemein)
private val INTERVAL_OPTIONS_SEC = listOf(30, 60, 180, 300, 600, 900, 1800, 3600)
// Intervall-Optionen für Watchface-Seiten (Seite 1 & 2)
private val PAGE_SYNC_INTERVAL_OPTIONS_SEC = listOf(30, 60, 120, 240, 360, 600)
// Intervall-Optionen für Health-Connect-Werte (Puls/Kcal/SpO2)
private val HEALTH_INTERVAL_OPTIONS_SEC = listOf(15, 30, 120, 240, 600, 900, 1800)

private fun formatInterval(sec: Int): String = when {
    sec < 60 -> "$sec s"
    else -> "${sec / 60} min"
}

@Composable
fun IntervalDropdown(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = INTERVAL_OPTIONS_SEC
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
            Text(formatInterval(selected), style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            options.forEach { sec ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatInterval(sec),
                            color = if (sec == selected) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(sec)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Health Connect Bereich ──────────────────────────────────────────────────

@Composable
private fun HealthConnectSection(
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current
    val status = uiState.healthConnectStatus
    val loading = uiState.healthConnectLoading

    // Status wird bereits beim App-Start (ViewModel-Init) geladen und im
    // ViewModel gehalten. Hier nur nachladen, falls noch nichts vorhanden ist
    // (z. B. erster Start) – so blinkt der Status beim Auf-/Zuklappen der
    // Sektion nicht jedes Mal erneut auf "Wird geladen...".
    LaunchedEffect(Unit) {
        if (status.dataTypes.isEmpty() && !loading) {
            viewModel.refreshHealthConnectStatus()
        }
    }

    // Health-Connect-Berechtigungen anfragen (spezieller Contract)
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) {
        // Nach Berechtigungsanfrage Status neu laden
        viewModel.refreshHealthConnectStatus()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Gesundheitsdaten (Health Connect)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Health Connect verbindet Google Fit, Samsung Health und andere Gesundheits-Apps.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // Status-Anzeige
        DetailCard(label = "Status") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (loading) Color(0xFFFF9800) else if (status.sdkAvailable) Color(0xFF4CAF50) else Color(0xFFF44336),
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (loading) "Wird geladen..." else if (status.sdkAvailable) "Verfügbar" else viewModel.healthConnectManager.getSdkStatusText(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!status.sdkAvailable && !loading) {
            Text(
                text = "Health Connect ist auf diesem Gerät nicht verfügbar. Bitte installiere die Health Connect App aus dem Play Store.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFF44336)
            )
        } else if (status.sdkAvailable) {
            // Berechtigungen anfordern / Status laden
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.refreshHealthConnectStatus() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A2A),
                        contentColor = Color.White
                    ),
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonYellow
                        )
                    } else {
                        Text("Status prüfen", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = {
                        healthPermissionLauncher.launch(viewModel.healthConnectManager.allPermissions)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonYellow,
                        contentColor = Color(0xFF1A1A00)
                    )
                ) {
                    Text("Berechtigungen", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Datentypen-Liste anzeigen
            if (status.dataTypes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Verfügbare Datentypen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                status.dataTypes.forEach { dataType ->
                    HealthDataTypeRow(dataType)
                }
            }
        }
    }
}

@Composable
private fun HealthDataTypeRow(dataType: HealthDataTypeInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dataType.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            if (dataType.available && dataType.sources.isNotEmpty()) {
                Text(
                    text = dataType.sources.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonYellow.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (dataType.available) {
                Text(
                    text = "Berechtigt (keine Daten in 24h)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when {
                        dataType.available && dataType.sources.isNotEmpty() -> Color(0xFF4CAF50)
                        dataType.available -> Color(0xFFFF9800)
                        else -> Color(0xFF666666)
                    },
                    shape = CircleShape
                )
        )
    }
}

private val HEALTH_SOURCE_PER_TYPE_OPTIONS = listOf("local" to "Lokal (Uhr)", "healthconnect" to "Health Connect")

/**
 * Pro-Typ Gesundheitsdaten-Quelle: Dropdown (Lokal/Health Connect).
 * Bei Quelle "Health Connect" werden die verfügbaren Quell-Apps für diesen HC-Typ angezeigt.
 * defaultHcTypeKey: fester HC-Datentypschlüssel für diese Zeile (z.B. "heart_rate")
 * hcSourcePkg: ausgewählter Package-Name der Quell-App (leer = alle Quellen)
 */
@Composable
private fun HealthSourcePerTypeRow(
    label: String,
    source: String,
    onSourceChange: (String) -> Unit,
    defaultHcTypeKey: String,
    hcSourcePkg: String,
    onHcSourcePkgChange: (String) -> Unit,
    availableHcTypes: List<HealthDataTypeInfo>
) {
    val typeInfo = availableHcTypes.find { it.key == defaultHcTypeKey }
    Column(modifier = Modifier.padding(start = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
            HealthSourcePerTypeDropdown(
                selected = source,
                onSelect = onSourceChange,
                modifier = Modifier.weight(0.7f)
            )
        }
        if (source == "healthconnect" && typeInfo != null && typeInfo.sourcePackages.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Datenquelle", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA), modifier = Modifier.weight(1f))
                HealthConnectSourceDropdown(
                    labels = typeInfo.sources,
                    packages = typeInfo.sourcePackages,
                    selectedPkg = hcSourcePkg,
                    onSelect = onHcSourcePkgChange,
                    modifier = Modifier.weight(0.7f)
                )
            }
        }
    }
}

/**
 * Generisches Dropdown für (Wert → Label)-Optionen, gleiches Erscheinungsbild wie die Quellen-Auswahl.
 */
@Composable
private fun GenericValueDropdown(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = options.firstOrNull { it.first == selected }?.second ?: selected
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(displayName, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            options.forEach { (value, lbl) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = lbl,
                            color = if (value == selected) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HealthSourcePerTypeDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = HEALTH_SOURCE_PER_TYPE_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(displayName, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            HEALTH_SOURCE_PER_TYPE_OPTIONS.forEach { (value, lbl) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = lbl,
                            color = if (value == selected) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Dropdown für verfügbare Health-Connect-Quell-Apps eines bestimmten Datentyps.
 * "" = Alle Quellen (Standard).
 * labels: App-Labels (für Anzeige), packages: Package-Namen (für Filterung) – gleiche Reihenfolge.
 */
@Composable
private fun HealthConnectSourceDropdown(
    labels: List<String>,
    packages: List<String>,
    selectedPkg: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (selectedPkg.isEmpty()) "Alle Quellen"
        else labels.getOrElse(packages.indexOf(selectedPkg)) { selectedPkg }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Alle Quellen",
                        color = if (selectedPkg.isEmpty()) NeonYellow else Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                onClick = { onSelect(""); expanded = false }
            )
            packages.forEachIndexed { index, pkg ->
                val label = labels.getOrElse(index) { pkg }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (pkg == selectedPkg) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = { onSelect(pkg); expanded = false }
                )
            }
        }
    }
}

private val HEALTH_SOURCE_OPTIONS = listOf("local" to "Lokal (Uhr)", "phone" to "Smartphone")

@Composable
fun HealthSourceDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = HEALTH_SOURCE_OPTIONS.firstOrNull { it.first == selected }?.second ?: selected
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444444))
        ) {
            Text(displayName, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            HEALTH_SOURCE_OPTIONS.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (value == selected) NeonYellow else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Hilfsfunktionen ───────────────────────────────────────────────────────────

/** Wandelt einen Farbschlüssel (wie er in der Config gespeichert ist) in eine Compose-Color um. */
private fun pillColorFromKey(key: String): Color = when (key) {
    "cyan"       -> Color(0xFF00BCD4)
    "green"      -> Color(0xFF4CAF50)
    "neon_yellow"-> Color(0xFFEAFF00)
    "white"      -> Color.White
    "red"        -> Color(0xFFF44336)
    "orange"     -> Color(0xFFFF9800)
    "purple"     -> Color(0xFF9C27B0)
    "light_gray" -> Color(0xFF888888)
    else         -> Color(0xFF888888)
}

/**
 * Zeigt den aktuellen Pillen-Zustand als farbigen Button.
 * Reagiert NUR auf Doppeltipp — einfaches Antippen tut nichts.
 */
@Composable
private fun DoubleTapPillButton(
    label: String,
    hint: String,
    pillState: Boolean,
    colorTrue: String,
    colorFalse: String,
    onDoubleTap: () -> Unit
) {
    val activeColor = pillColorFromKey(if (pillState) colorTrue else colorFalse)
    var lastTapMs by remember { mutableStateOf(0L) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hint,  style = MaterialTheme.typography.labelSmall,  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastTapMs < 500L) {
                        onDoubleTap()
                        lastTapMs = 0L  // Reset damit kein Triple-Tap feuert
                    } else {
                        lastTapMs = now
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (pillState) "AN" else "AUS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = activeColor
            )
            Box(
                modifier = Modifier
                    .size(32.dp, 14.dp)
                    .background(activeColor, RoundedCornerShape(7.dp))
            )
        }
    }
}
