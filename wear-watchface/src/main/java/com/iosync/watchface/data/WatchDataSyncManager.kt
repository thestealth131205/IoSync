package com.iosync.watchface.data

import android.util.Log
import com.iosync.watchface.datalayer.CachedState
import com.iosync.watchface.datalayer.SmartHomeStateCache
import com.iosync.watchface.datalayer.WatchFaceConfigCache
import com.iosync.watchface.health.HealthDataCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private const val TAG = "WatchDataSyncManager"

/**
 * Zentraler Daten-Orchestrator auf der Uhr (ab v5).
 *
 * Früher pollte das Handy alle Werte und schickte sie per Data Layer an die Uhr.
 * Jetzt fragt die Uhr selbst:
 *  - die ioBroker-Datenpunkte (Slots, Pillen, Balken, Wetter-/Schlaf-Quelle)
 *    über den IoSync-Adapter ([WatchIoSyncClient])
 *  - das Wetter direkt über OpenWeather ([WatchWeatherService])
 * und schreibt Befehle (Pillen/Slider) direkt via setState an den Adapter.
 *
 * Health-Daten liegen ohnehin schon lokal (HealthDataCache); sie werden in die
 * vom Renderer gelesenen `phone*`-Felder gespiegelt, damit die bestehende
 * Renderer-Logik (Quelle "healthconnect") unverändert weiterläuft.
 *
 * Das Handy überträgt nur noch die Verbindungs-/Datenpunkt-Konfiguration
 * (siehe [WatchFaceConfigCache.updateConnectionFromDataMap]).
 *
 * Die Werte landen in [WatchFaceConfigCache] / [SmartHomeStateCache]; nach jedem
 * Update wird [invalidate] aufgerufen, damit der Renderer neu zeichnet.
 */
object WatchDataSyncManager {

    private var scope: CoroutineScope? = null
    private var fetchJob: Job? = null
    private var weatherJob: Job? = null
    private var healthJob: Job? = null
    private var pushJob: Job? = null
    private var pushDebounceJob: Job? = null

    private var invalidate: (() -> Unit)? = null
    private val fetchMutex = Mutex()

    @Volatile private var running = false
    @Volatile private var pushSignature = ""

    fun start(invalidate: () -> Unit) {
        if (running) return
        running = true
        this.invalidate = invalidate
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        fetchJob   = s.launch { fetchLoop() }
        weatherJob = s.launch { weatherLoop() }
        healthJob  = s.launch { healthMirrorLoop() }
        pushJob    = s.launch { pushLoop() }
        Log.d(TAG, "WatchDataSyncManager gestartet")
    }

    fun stop() {
        running = false
        WatchIoSyncPushClient.stop()
        pushSignature = ""
        scope?.let { sc ->
            fetchJob?.cancel(); weatherJob?.cancel(); healthJob?.cancel()
            pushJob?.cancel(); pushDebounceJob?.cancel()
        }
        scope = null
    }

    /** Sofortiger Einmal-Abruf (z.B. wenn das Display aktiviert wird). */
    fun syncNow() {
        scope?.launch {
            runFetch()
            runWeather()
        }
    }

    // ── Datenpunkt-Abruf ──────────────────────────────────────────────────────

    private suspend fun fetchLoop() {
        while (running) {
            try {
                runFetch()
                val interval = WatchFaceConfigCache.slotIntervalSec.coerceAtLeast(10)
                delay(interval * 1_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "fetchLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    private suspend fun runFetch() {
        val c = WatchFaceConfigCache
        if (!c.ioUseAdapter || c.ioHost.isBlank()) return
        fetchMutex.withLock {
            WatchIoSyncClient.fetchDataPoints(
                c.ioHost, c.ioPort, c.ioUseHttps, c.ioUsername, c.ioPassword
            ).onSuccess { states ->
                resolveAll(states)
                invalidate?.invoke()
            }
        }
    }

    /** Alle datenpunktabhängigen Werte aus der abgerufenen Liste auflösen. */
    private fun resolveAll(states: List<CachedState>) {
        val c = WatchFaceConfigCache
        fun valueOf(id: String): String? =
            if (id.isBlank()) null else states.firstOrNull { it.id == id }?.value
        fun boolOf(id: String, current: Boolean): Boolean {
            val v = valueOf(id) ?: return current
            return v == "true" || v == "1"
        }

        // Volle Zustandsliste (Seite-1-„ioBroker-Daten"-Anzeige)
        if (c.showIoBrokerData) {
            SmartHomeStateCache.updateFromCachedStates(states)
        }

        // Seite-1 Custom-Slots
        if (c.showCustomSlots) {
            c.customSlot1Value = formatSlotValue(valueOf(c.conSlot1Id))
            c.customSlot2Value = formatSlotValue(valueOf(c.conSlot2Id))
            c.customSlot3Value = formatSlotValue(valueOf(c.conSlot3Id))
            c.customSlot4Value = formatSlotValue(valueOf(c.conSlot4Id))
        }

        // Seite-2 Slots + Balken
        c.p2Slot1Value = formatSlotValue(valueOf(c.conP2Slot1Id))
        c.p2Slot2Value = formatSlotValue(valueOf(c.conP2Slot2Id))
        c.p2Slot3Value = formatSlotValue(valueOf(c.conP2Slot3Id))
        c.p2Slot4Value = formatSlotValue(valueOf(c.conP2Slot4Id))
        if (c.conP2BarId.isNotBlank()) c.p2BarValue = formatSlotValue(valueOf(c.conP2BarId))

        // Pillen-Status (aus Datenpunktwert)
        if (c.actionPillEnabled && c.actionPillIoBrokerId.isNotBlank()) {
            c.actionPillState = boolOf(c.actionPillIoBrokerId, c.actionPillState)
        }
        if (c.p2PillEnabled && c.p2PillIoBrokerId.isNotBlank()) {
            c.p2Pill1State = boolOf(c.p2PillIoBrokerId, c.p2Pill1State)
        }
        if (c.p2Pill2Enabled && c.p2Pill2IoBrokerId.isNotBlank()) {
            c.p2Pill2State = boolOf(c.p2Pill2IoBrokerId, c.p2Pill2State)
        }

        // Wetter-Temperatur aus ioBroker
        if (c.showWeather && c.weatherTempSource == "iobroker" && c.weatherIoBrokerId.isNotBlank()) {
            valueOf(c.weatherIoBrokerId)?.toDoubleOrNull()?.let {
                c.weatherTemp = it.toInt()
                c.weatherCondition = "clear"
            }
        }

        // Schlafdauer aus ioBroker
        if (c.sleepSource == "iobroker" && c.conSleepId.isNotBlank()) {
            valueOf(c.conSleepId)?.toDoubleOrNull()?.toInt()?.let {
                if (it > 0) c.phoneSleepMinutes = it
            }
        }

        // Boden-Komplikation 1 (ioBroker-Quelle)
        if (c.bc1UseIoBroker && c.conBc1Id.isNotBlank()) {
            c.bc1IoValue = formatSlotValue(valueOf(c.conBc1Id))
        }
        // Boden-Komplikation 2 (ioBroker-Quelle)
        if (c.bc2UseIoBroker && c.conBc2Id.isNotBlank()) {
            c.bc2IoValue = formatSlotValue(valueOf(c.conBc2Id))
        }
    }

    private fun formatSlotValue(value: String?): String {
        if (value == null) return "--"
        val num = value.toDoubleOrNull() ?: return value.take(6)
        return String.format(Locale.US, "%.1f", num)
    }

    // ── Wetter (OpenWeather direkt auf der Uhr) ───────────────────────────────

    private suspend fun weatherLoop() {
        while (running) {
            var nextDelay = WatchFaceConfigCache.weatherIntervalSec.coerceAtLeast(60) * 1_000L
            try {
                if (!runWeather()) nextDelay = 60_000L
                delay(nextDelay)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "weatherLoop Ausnahme, Neuversuch in 60s: ${e.message}", e)
                delay(60_000L)
            }
        }
    }

    /** @return true bei Erfolg/übersprungen, false bei fehlgeschlagenem Abruf (kürzerer Retry). */
    private suspend fun runWeather(): Boolean {
        val c = WatchFaceConfigCache
        if (!c.showWeather || c.weatherTempSource != "openweather") return true
        if (!c.weatherUseFixed || c.weatherLat.isNaN() || c.weatherLon.isNaN()) return true
        var ok = true
        WatchWeatherService.fetchWeather(c.weatherLat, c.weatherLon)
            .onSuccess {
                c.weatherTemp = it.temperature
                c.weatherCondition = it.condition
                invalidate?.invoke()
            }
            .onFailure {
                Log.w(TAG, "Wetter-Abruf fehlgeschlagen: ${it.message}")
                ok = false
            }
        return ok
    }

    // ── Health-Spiegelung (lokale Sensoren → phone*-Felder) ───────────────────

    private suspend fun healthMirrorLoop() {
        while (running) {
            try {
                val c = WatchFaceConfigCache
                c.phoneHeartRate = HealthDataCache.heartRate
                c.phoneCalories  = HealthDataCache.calories
                c.phoneSpO2      = HealthDataCache.spO2
                // Schlaf: ioBroker-Quelle hat in resolveAll Vorrang
                if (c.sleepSource != "iobroker") {
                    c.phoneSleepMinutes = HealthDataCache.sleepMinutes
                }
                c.phoneHealthLastReceived = System.currentTimeMillis()
                delay(30_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "healthMirrorLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    // ── Echtzeit-Push (optional, SSE vom Adapter) ─────────────────────────────

    private suspend fun pushLoop() {
        while (running) {
            try {
                val c = WatchFaceConfigCache
                if (c.ioUseAdapter && c.ioUsePush && c.ioHost.isNotBlank()) {
                    val sig = "${c.ioHost}|${c.ioPort}|${c.ioUseHttps}|${c.ioUsername}|${c.ioPassword}"
                    if (sig != pushSignature) {
                        pushSignature = sig
                        WatchIoSyncPushClient.start(
                            c.ioHost, c.ioPort, c.ioUseHttps, c.ioUsername, c.ioPassword,
                            onEvent = { onPushEventDebounced() }
                        )
                    }
                } else {
                    WatchIoSyncPushClient.stop()
                    pushSignature = ""
                }
                delay(60_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "pushLoop Ausnahme, Neuversuch in 30s: ${e.message}", e)
                delay(30_000L)
            }
        }
    }

    @Synchronized
    private fun onPushEventDebounced() {
        pushDebounceJob?.cancel()
        pushDebounceJob = scope?.launch {
            delay(150L)
            runFetch()
        }
    }

    // ── Befehle (setState direkt an den Adapter) ──────────────────────────────

    /** Aktions-Pille (Seite 1) schalten. */
    fun toggleActionPill() {
        val c = WatchFaceConfigCache
        sendPillCommand(
            id = c.actionPillIoBrokerId,
            valueMode = c.actionPillValueMode,
            fixedValue = c.actionPillFixedValue,
            currentState = c.actionPillState,
            applyState = { c.actionPillState = it }
        )
    }

    /** Seite-2-Pille 1 schalten. */
    fun toggleP2Pill1() {
        val c = WatchFaceConfigCache
        sendPillCommand(
            id = c.p2PillIoBrokerId,
            valueMode = c.p2PillValueMode,
            fixedValue = c.p2PillFixedValue,
            currentState = c.p2Pill1State,
            applyState = { c.p2Pill1State = it }
        )
    }

    /** Seite-2-Pille 2 schalten. */
    fun toggleP2Pill2() {
        val c = WatchFaceConfigCache
        sendPillCommand(
            id = c.p2Pill2IoBrokerId,
            valueMode = c.p2Pill2ValueMode,
            fixedValue = c.p2Pill2FixedValue,
            currentState = c.p2Pill2State,
            applyState = { c.p2Pill2State = it }
        )
    }

    /** Slider-/Balken-Wert (Seite 2) in den ioBroker-Datenpunkt schreiben. */
    fun setBarValue(value: Int) {
        val c = WatchFaceConfigCache
        if (!c.ioUseAdapter || c.ioHost.isBlank() || c.conP2BarId.isBlank()) return
        c.p2BarValue = value.toString()
        invalidate?.invoke()
        scope?.launch {
            WatchIoSyncClient.setState(
                c.ioHost, c.ioPort, c.ioUseHttps, c.ioUsername, c.ioPassword,
                c.conP2BarId, value.toString()
            )
        }
    }

    private fun sendPillCommand(
        id: String,
        valueMode: String,
        fixedValue: String,
        currentState: Boolean,
        applyState: (Boolean) -> Unit
    ) {
        val c = WatchFaceConfigCache
        if (!c.ioUseAdapter || c.ioHost.isBlank() || id.isBlank()) return
        val valueToSend = when (valueMode) {
            "true"   -> "true"
            "false"  -> "false"
            "fixed"  -> fixedValue
            "toggle" -> if (currentState) "false" else "true"
            else     -> return
        }
        val newState = when (valueMode) {
            "toggle" -> !currentState
            "true"   -> true
            "false"  -> false
            else     -> currentState
        }
        // Optimistisches UI-Update; bei Fehlschlag zurücksetzen.
        applyState(newState)
        invalidate?.invoke()
        scope?.launch {
            WatchIoSyncClient.setState(
                c.ioHost, c.ioPort, c.ioUseHttps, c.ioUsername, c.ioPassword, id, valueToSend
            ).onFailure {
                Log.e(TAG, "Pillen-Befehl fehlgeschlagen: ${it.message}")
                applyState(currentState)
                invalidate?.invoke()
            }
        }
    }
}
