package com.iosync.watchface.datalayer

import android.content.Context
import android.util.Log

/**
 * Persistiert die Verbindungs-Konfiguration in SharedPreferences auf der Uhr,
 * damit sie nach einem Watchface-Prozess-Neustart auch ohne aktive Handy-
 * Verbindung sofort verfügbar ist.
 *
 * Problem ohne Persistierung:
 * - WatchFaceConfigCache hält die Verbindungs-Config nur im RAM.
 * - Beim Neustart lädt loadInitialConfig() die Config aus dem Data Layer.
 * - Ist Bluetooth beim Start getrennt oder das Data Layer nach einem Update/
 *   Neuinstallation leer, bleibt ioHost="" → fetchLoop bricht sofort ab
 *   (if c.ioHost.isBlank() return) → keine Datenabrufe, eingefrorene Werte.
 *
 * Lösung: Connection-Config bei jedem Empfang vom Handy hier speichern.
 * Beim Watchface-Start wird restore() synchron im init-Block aufgerufen
 * (vor WatchDataSyncManager.start()), sodass sofort ein gültiger ioHost
 * vorhanden ist. Data Layer überschreibt diesen Wert später falls verfügbar.
 */
object WatchConnectionPrefs {

    private const val TAG = "WatchConnectionPrefs"
    private const val PREFS_NAME = "iosync_connection"
    private const val KEY_INITIALIZED = "initialized"

    // Verbindung
    private const val KEY_IO_USE_ADAPTER = "io_use_adapter"
    private const val KEY_IO_HOST        = "io_host"
    private const val KEY_IO_PORT        = "io_port"
    private const val KEY_IO_USE_HTTPS   = "io_use_https"
    private const val KEY_IO_USERNAME    = "io_username"
    private const val KEY_IO_PASSWORD    = "io_password"
    private const val KEY_IO_USE_PUSH    = "io_use_push"

    // Slot-IDs Seite 1
    private const val KEY_CON_SLOT1_ID = "con_slot1_id"
    private const val KEY_CON_SLOT2_ID = "con_slot2_id"
    private const val KEY_CON_SLOT3_ID = "con_slot3_id"
    private const val KEY_CON_SLOT4_ID = "con_slot4_id"

    // Slot-IDs Seite 2
    private const val KEY_CON_P2_SLOT1_ID = "con_p2_slot1_id"
    private const val KEY_CON_P2_SLOT2_ID = "con_p2_slot2_id"
    private const val KEY_CON_P2_SLOT3_ID = "con_p2_slot3_id"
    private const val KEY_CON_P2_SLOT4_ID = "con_p2_slot4_id"
    private const val KEY_CON_P2_BAR_ID   = "con_p2_bar_id"
    private const val KEY_CON_P2_COLOR_ID = "con_p2_color_id"

    // Sonstige Datenpunkt-IDs
    private const val KEY_CON_SLEEP_ID = "con_sleep_id"
    private const val KEY_CON_BC1_ID   = "con_bc1_id"
    private const val KEY_CON_BC2_ID   = "con_bc2_id"

    // Wetter
    private const val KEY_WEATHER_USE_FIXED = "weather_use_fixed"
    private const val KEY_WEATHER_LAT       = "weather_lat"
    private const val KEY_WEATHER_LON       = "weather_lon"
    private const val KEY_WEATHER_INTERVAL  = "weather_interval_sec"

    // Abruf-Intervalle
    private const val KEY_SLOT_INTERVAL  = "slot_interval_sec"
    private const val KEY_PAGE2_INTERVAL = "page2_interval_sec"
    private const val KEY_HR_INTERVAL    = "hr_interval_sec"

    // Klipper
    private const val KEY_KLIPPER_ENABLED     = "klipper_enabled"
    private const val KEY_KLIPPER_HOST        = "klipper_host"
    private const val KEY_KLIPPER_PORT        = "klipper_port"
    private const val KEY_KLIPPER_API_KEY     = "klipper_api_key"
    private const val KEY_KLIPPER_CHAMBER_OBJ = "klipper_chamber_obj"
    private const val KEY_KLIPPER_INTERVAL    = "klipper_interval_sec"

    /**
     * Speichert die aktuelle Verbindungs-Config aus [WatchFaceConfigCache] in
     * SharedPreferences. Wird nach jedem Empfang vom Handy aufgerufen.
     */
    fun save(context: Context) {
        val c = WatchFaceConfigCache
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().also { ed ->
            ed.putBoolean(KEY_INITIALIZED, true)
            ed.putBoolean(KEY_IO_USE_ADAPTER, c.ioUseAdapter)
            ed.putString(KEY_IO_HOST, c.ioHost)
            ed.putInt(KEY_IO_PORT, c.ioPort)
            ed.putBoolean(KEY_IO_USE_HTTPS, c.ioUseHttps)
            ed.putString(KEY_IO_USERNAME, c.ioUsername)
            ed.putString(KEY_IO_PASSWORD, c.ioPassword)
            ed.putBoolean(KEY_IO_USE_PUSH, c.ioUsePush)
            ed.putString(KEY_CON_SLOT1_ID, c.conSlot1Id)
            ed.putString(KEY_CON_SLOT2_ID, c.conSlot2Id)
            ed.putString(KEY_CON_SLOT3_ID, c.conSlot3Id)
            ed.putString(KEY_CON_SLOT4_ID, c.conSlot4Id)
            ed.putString(KEY_CON_P2_SLOT1_ID, c.conP2Slot1Id)
            ed.putString(KEY_CON_P2_SLOT2_ID, c.conP2Slot2Id)
            ed.putString(KEY_CON_P2_SLOT3_ID, c.conP2Slot3Id)
            ed.putString(KEY_CON_P2_SLOT4_ID, c.conP2Slot4Id)
            ed.putString(KEY_CON_P2_BAR_ID, c.conP2BarId)
            ed.putString(KEY_CON_P2_COLOR_ID, c.conP2ColorId)
            ed.putString(KEY_CON_SLEEP_ID, c.conSleepId)
            ed.putString(KEY_CON_BC1_ID, c.conBc1Id)
            ed.putString(KEY_CON_BC2_ID, c.conBc2Id)
            ed.putBoolean(KEY_WEATHER_USE_FIXED, c.weatherUseFixed)
            // Double wird als String gespeichert (SharedPreferences kennt kein Double)
            ed.putString(KEY_WEATHER_LAT, c.weatherLat.toString())
            ed.putString(KEY_WEATHER_LON, c.weatherLon.toString())
            ed.putInt(KEY_WEATHER_INTERVAL, c.weatherIntervalSec)
            ed.putInt(KEY_SLOT_INTERVAL, c.slotIntervalSec)
            ed.putInt(KEY_PAGE2_INTERVAL, c.page2IntervalSec)
            ed.putInt(KEY_HR_INTERVAL, c.heartRateIntervalSec)
            ed.putBoolean(KEY_KLIPPER_ENABLED, c.klipperEnabled)
            ed.putString(KEY_KLIPPER_HOST, c.klipperHost)
            ed.putInt(KEY_KLIPPER_PORT, c.klipperPort)
            ed.putString(KEY_KLIPPER_API_KEY, c.klipperApiKey)
            ed.putString(KEY_KLIPPER_CHAMBER_OBJ, c.klipperChamberObject)
            ed.putInt(KEY_KLIPPER_INTERVAL, c.klipperIntervalSec)
            ed.apply()
        }
        Log.d(TAG, "Connection-Config gespeichert (host=${c.ioHost})")
    }

    /**
     * Lädt die gespeicherte Verbindungs-Config in [WatchFaceConfigCache].
     *
     * Wird synchron im Renderer-init aufgerufen, bevor [WatchDataSyncManager]
     * gestartet wird. So hat der SyncManager beim ersten fetchLoop-Durchlauf
     * bereits einen gültigen ioHost, auch wenn das Data Layer noch nicht
     * geladen wurde (oder nicht verfügbar ist).
     *
     * @return true wenn gespeicherte Daten vorhanden waren und geladen wurden.
     */
    fun restore(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) return false
        val c = WatchFaceConfigCache
        c.ioUseAdapter    = prefs.getBoolean(KEY_IO_USE_ADAPTER, false)
        c.ioHost          = prefs.getString(KEY_IO_HOST, "") ?: ""
        c.ioPort          = prefs.getInt(KEY_IO_PORT, 7443)
        c.ioUseHttps      = prefs.getBoolean(KEY_IO_USE_HTTPS, false)
        c.ioUsername      = prefs.getString(KEY_IO_USERNAME, "") ?: ""
        c.ioPassword      = prefs.getString(KEY_IO_PASSWORD, "") ?: ""
        c.ioUsePush       = prefs.getBoolean(KEY_IO_USE_PUSH, false)
        c.conSlot1Id      = prefs.getString(KEY_CON_SLOT1_ID, "") ?: ""
        c.conSlot2Id      = prefs.getString(KEY_CON_SLOT2_ID, "") ?: ""
        c.conSlot3Id      = prefs.getString(KEY_CON_SLOT3_ID, "") ?: ""
        c.conSlot4Id      = prefs.getString(KEY_CON_SLOT4_ID, "") ?: ""
        c.conP2Slot1Id    = prefs.getString(KEY_CON_P2_SLOT1_ID, "") ?: ""
        c.conP2Slot2Id    = prefs.getString(KEY_CON_P2_SLOT2_ID, "") ?: ""
        c.conP2Slot3Id    = prefs.getString(KEY_CON_P2_SLOT3_ID, "") ?: ""
        c.conP2Slot4Id    = prefs.getString(KEY_CON_P2_SLOT4_ID, "") ?: ""
        c.conP2BarId      = prefs.getString(KEY_CON_P2_BAR_ID, "") ?: ""
        c.conP2ColorId    = prefs.getString(KEY_CON_P2_COLOR_ID, "") ?: ""
        c.conSleepId      = prefs.getString(KEY_CON_SLEEP_ID, "") ?: ""
        c.conBc1Id        = prefs.getString(KEY_CON_BC1_ID, "") ?: ""
        c.conBc2Id        = prefs.getString(KEY_CON_BC2_ID, "") ?: ""
        c.weatherUseFixed = prefs.getBoolean(KEY_WEATHER_USE_FIXED, false)
        c.weatherLat      = prefs.getString(KEY_WEATHER_LAT, "NaN")?.toDoubleOrNull() ?: Double.NaN
        c.weatherLon      = prefs.getString(KEY_WEATHER_LON, "NaN")?.toDoubleOrNull() ?: Double.NaN
        c.weatherIntervalSec  = prefs.getInt(KEY_WEATHER_INTERVAL, 600)
        c.slotIntervalSec     = prefs.getInt(KEY_SLOT_INTERVAL, 120)
        c.page2IntervalSec    = prefs.getInt(KEY_PAGE2_INTERVAL, 120)
        c.heartRateIntervalSec = prefs.getInt(KEY_HR_INTERVAL, 600)
        c.klipperEnabled      = prefs.getBoolean(KEY_KLIPPER_ENABLED, false)
        c.klipperHost         = prefs.getString(KEY_KLIPPER_HOST, "") ?: ""
        c.klipperPort         = prefs.getInt(KEY_KLIPPER_PORT, 7125)
        c.klipperApiKey       = prefs.getString(KEY_KLIPPER_API_KEY, "") ?: ""
        c.klipperChamberObject = prefs.getString(KEY_KLIPPER_CHAMBER_OBJ, "") ?: ""
        c.klipperIntervalSec  = prefs.getInt(KEY_KLIPPER_INTERVAL, 15)
        Log.d(TAG, "Connection-Config wiederhergestellt (host=${c.ioHost})")
        return true
    }
}
