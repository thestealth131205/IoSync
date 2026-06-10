package com.iosync.watchface.renderer

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSetting
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.iosync.watchface.data.WatchDataSyncManager
import com.iosync.watchface.datalayer.SmartHomeStateCache
import com.iosync.watchface.datalayer.WatchFaceConfigCache
import com.iosync.watchface.health.HealthDataCache
import com.iosync.watchface.health.HealthSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.google.android.gms.wearable.PutDataMapRequest

private const val FRAME_PERIOD_MS_DEFAULT = 2L

// ── NTP-Offset Rücksendung (Uhr → App) ───────────────────────────────────────
private const val PATH_NTP_OFFSET_FROM_WATCH = "/iosync/watchface/ntp_offset"
private const val KEY_NTP_OFFSET_MS          = "ntp_offset_ms"
private const val FRAME_PERIOD_AMBIENT_MS = 60_000L

/**
 * Canvas-Renderer für das IoSync Watchface.
 *
 * Layout (aktiver Modus):
 *   - Zifferblatt-Striche (optional)
 *   - Digitale Uhrzeit HH:mm + ss (optional)
 *   - Wochentag (optional)
 *   - ioBroker-Datenpunkte: bis zu 2 Zeilen Text + Fortschrittsbalken für %-Werte (optional)
 *   - Handy-Akkustand oben links (optional)
 *   - 4 Komplikationsslots
 *
 * Alle Optionen sind vom Smartphone per Wearable Data Layer konfigurierbar.
 */
class IoSyncWatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
    val healthSensorManager: HealthSensorManager = HealthSensorManager(context)
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
), WatchFace.TapListener, DataClient.OnDataChangedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val density = context.resources.displayMetrics.density
    private val dataClient: DataClient = Wearable.getDataClient(context)
    // Persistenter Speicher für den NTP-Offset: überlebt Watchface-Neustarts, sodass
    // beim Aufwachen sofort der zuletzt bekannte Offset gilt (ohne Netz abzuwarten).
    private val ntpPrefs: android.content.SharedPreferences =
        context.getSharedPreferences(NTP_PREFS_NAME, Context.MODE_PRIVATE)

    private var accentColor: Int = Color.parseColor("#EAFF00")

    // ── Hintergrund ───────────────────────────────────────────────────────────
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }
    private var backgroundBitmap: Bitmap? = null
    private var backgroundBitmapScaled: Bitmap? = null
    private var backgroundBitmapP2: Bitmap? = null
    private var backgroundBitmapP2Scaled: Bitmap? = null
    private val backgroundBitmapPaint = Paint().apply { isAntiAlias = true }

    // ── Hauptzeit HH:mm ───────────────────────────────────────────────────────
    private val timePaint = Paint().apply {
        color = Color.parseColor("#E8E8E8")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // ── Sekunden ss ───────────────────────────────────────────────────────────
    private val secondsPaint = Paint().apply {
        color = Color.parseColor("#AEAEAE")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // ── Wochentag ─────────────────────────────────────────────────────────────
    private val weekdayPaint = Paint().apply {
        color = Color.parseColor("#888888")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ── Ambient-Modus ─────────────────────────────────────────────────────────
    private val ambientTimePaint = Paint().apply {
        color = Color.parseColor("#AAAAAA")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = false
        textAlign = Paint.Align.CENTER
    }

    // ── Zifferblatt-Striche ───────────────────────────────────────────────────
    private val tickPaint = Paint().apply {
        color = Color.parseColor("#333333")
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val accentTickPaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // ── ioBroker-Datenpunkt-Text ──────────────────────────────────────────────
    private val dpLabelPaint = Paint().apply {
        color = Color.parseColor("#666666")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val dpValuePaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ── Fortschrittsbalken ────────────────────────────────────────────────────
    private val progressBgPaint = Paint().apply {
        color = Color.parseColor("#2A2A2A")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val progressFgPaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // ── Sekundenring ──────────────────────────────────────────────────────────
    private val secondsRingBgPaint = Paint().apply {
        color = Color.parseColor("#1AFFFFFF")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val secondsRingFgPaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // Glow-Schein nach innen — wird pro Frame wiederverwendet
    private val secondsGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    // ── Handy-Akku ────────────────────────────────────────────────────────────
    private val batteryPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val batteryChargingPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val phoneIconStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val phoneScreenFillPaint = Paint().apply {
        color = Color.argb(55, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val overlayBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // ── Akku-Ringe ────────────────────────────────────────────────────────────
    private val batteryRingBgPaint = Paint().apply {
        color = Color.argb(50, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val batteryRingFgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val batteryPercentPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ── 3D Skeuomorphismus / Eingetiefte Vertiefungen ─────────────────────────
    private val recessFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(12, 12, 12)
        style = Paint.Style.FILL
    }
    private val recessInnerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val recessRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }
    private val recessTempRect = RectF()
    private val recessShadowAlphas = intArrayOf(55, 30, 14, 6)

    // ── Wetter-Kreis ──────────────────────────────────────────────────────────
    private val weatherCircleBgPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val weatherCircleStrokePaint = Paint().apply {
        color = Color.parseColor("#333333")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val weatherTempPaint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val weatherIconPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // ── Gesundheitsdaten ────────────────────────────────────────────────────
    private val healthLabelPaint = Paint().apply {
        color = Color.parseColor("#666666")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val healthValuePaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ── Boden-Komplikationen (BC1 links, BC2 rechts) ──────────────────────────
    private val bcValuePaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val bcLabelPaint = Paint().apply {
        color = Color.parseColor("#888888")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val bcRingBgPaint = Paint().apply {
        color = Color.argb(50, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bcRingFgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ── Aktions-Pille ─────────────────────────────────────────────────────────
    private val pillFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val pillStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val pillTextPaint = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private var pillBounds = RectF()
    private var pillTapBounds = RectF()
    private var weatherTapBounds = RectF()
    private var lastTapTime = 0L
    private var pillPressed = false
    private var pillPressedAt = 0L
    private val PILL_PRESS_DURATION_MS = 300L

    // ── Stoppuhr ───────────────────────────────────────────────────────────────
    private enum class StopwatchMode { OFF, READY, RUNNING, STOPPED }
    private var stopwatchMode = StopwatchMode.OFF
    private var stopwatchAccumMs = 0L    // bereits gestoppte Zeit (vor letztem Start)
    private var stopwatchStartRt = 0L    // elapsedRealtime() beim letzten Start
    private var lastTimeTapTime = 0L
    private var pendingTimeTapJob: Job? = null

    private var timeTapBounds = RectF()        // Stunden/Minuten → Stoppuhr
    private var daySecondsTapBounds = RectF()  // Tages-Sekunden → Kalender-App
    private var bpmTapBounds = RectF()         // Puls → Health-App
    private var kcalTapBounds = RectF()        // Kalorien → Fitness-App

    private val stopwatchRingFgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E5FF")
    }
    private val stopwatchRingBgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(40, 0, 229, 255)
    }

    // ── Seite 2 ───────────────────────────────────────────────────────────────
    private var currentPage = 0          // 0 = Hauptseite, 1 = Zweite Seite
    private var nineOClockTapBounds = RectF()
    private var page2LastTapTime = 0L
    private var page2Pill1LastTapTime = 0L
    private var page2Pill2LastTapTime = 0L
    private var page2Pill1Bounds = RectF()
    private var page2Pill2Bounds = RectF()
    private var page2Pill1TapBounds = RectF()
    private var page2Pill2TapBounds = RectF()
    private var page2SliderBounds = RectF()    // exakte Balken-Geometrie (für Wert-Mapping)
    private var page2SliderTapBounds = RectF() // großzügige Tap-Zone (Balken + Werte-Zahlen links)
    private var page2Pill1Pressed = false
    private var page2Pill1PressedAt = 0L
    private var page2Pill2Pressed = false
    private var page2Pill2PressedAt = 0L

    private val page2PillFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val page2PillStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val page2SlotLabelPaint = Paint().apply {
        color = Color.parseColor("#888888")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val page2SlotValuePaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ── Config-Bestätigung (2 s Overlay) ───────────────────────────────────────
    private val confirmPaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val confirmBgPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    // Diagnose-Anzeige (selbst-versteckend): erscheint nur bei einem Verbindungsproblem.
    private val diagPaint = Paint().apply {
        color = Color.parseColor("#FF6B6B")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    companion object {
        private const val DOUBLE_TAP_MS   = 400L
        // NTP-Zeitkorrektur
        // Die Quarz-Drift einer Smartwatch verschiebt sich in wenigen Stunden nur um
        // wenige Millisekunden – eine seltene Hintergrund-Abfrage (6 h) genügt also
        // und schont Akku/Netz. Der zuletzt ermittelte Offset wird persistiert und
        // beim Aufwachen sofort wieder verwendet.
        private const val NTP_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L  // alle 6 h
        private const val NTP_TIMEOUT_MS          = 5000
        private const val NTP_EPOCH_OFFSET_SEC    = 2208988800L      // Sekunden zwischen 1900 und 1970
        private const val NTP_PREFS_NAME          = "iosync_ntp"
        private const val NTP_PREFS_KEY_OFFSET    = "ntp_offset_ms"
        private const val TAG_PILL        = "WatchFacePill"
        private const val CONFIRM_DURATION_MS = 2000L

        // Data Layer Pfade (gespiegelt aus WatchFaceDataListenerService)
        private const val PATH_WATCHFACE_CONFIG  = "/iosync/watchface/config"
        private const val PATH_WEATHER           = "/iosync/watchface/weather"
        private const val PATH_PHONE_BATTERY     = "/iosync/phone/battery"
        private const val PATH_CUSTOM_SLOTS      = "/iosync/watchface/custom_slots"
        private const val PATH_CUSTOM_SLOTS_P2   = "/iosync/watchface/custom_slots_p2"
        private const val PATH_CONFIG_P2         = "/iosync/watchface/config_p2"
        private const val PATH_CONNECTION_CONFIG = "/iosync/watchface/connection"
        private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
        private const val PATH_P2_PILL_STATES    = "/iosync/watchface/p2_pill_states"
        private const val PATH_STATES            = "/iosync/smarthome/states"
        private const val PATH_PHONE_HEALTH      = "/iosync/watchface/phone_health"
        private const val KEY_PHONE_HEART_RATE     = "phone_heart_rate"
        private const val KEY_PHONE_SPO2           = "phone_spo2"
        private const val KEY_PHONE_CALORIES       = "phone_calories"
        private const val KEY_PHONE_SLEEP_MINUTES  = "phone_sleep_minutes"
        private const val KEY_TIMESTAMP            = "timestamp"
    }

    // ── Formatter ─────────────────────────────────────────────────────────────
    private val timeFormatter    = DateTimeFormatter.ofPattern("HH:mm")
    private val secondsFormatter = DateTimeFormatter.ofPattern("ss")

    private fun weekdayShort(dt: ZonedDateTime): String = when (dt.dayOfWeek) {
        java.time.DayOfWeek.MONDAY    -> "MO"
        java.time.DayOfWeek.TUESDAY   -> "DI"
        java.time.DayOfWeek.WEDNESDAY -> "MI"
        java.time.DayOfWeek.THURSDAY  -> "DO"
        java.time.DayOfWeek.FRIDAY    -> "FR"
        java.time.DayOfWeek.SATURDAY  -> "SA"
        java.time.DayOfWeek.SUNDAY    -> "SO"
    }

    // ── Burn-in-Schutz ────────────────────────────────────────────────────────
    private var burnInOffsetX = 0f
    private var burnInOffsetY = 0f
    private var burnInFrame   = 0

    init {
        // DataClient-Listener registrieren für Echtzeit-Updates
        dataClient.addListener(this)

        // Bestehende Config aus dem Data Layer laden (beim Start)
        loadInitialConfig()

        // Daten-Orchestrator starten: die Uhr fragt ioBroker-Datenpunkte + Wetter
        // ab v5 selbst ab (statt sie vom Handy zu empfangen) und zeichnet bei jedem
        // Update neu.
        WatchDataSyncManager.start(invalidate = { invalidate() })

        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                userStyle[UserStyleSetting.Id("color_style")]?.let { option ->
                    accentColor = when (String(option.id.value)) {
                        "white" -> Color.WHITE
                        "cyan"  -> Color.parseColor("#00BCD4")
                        else    -> Color.parseColor("#EAFF00")
                    }
                    accentTickPaint.color = accentColor
                }
                invalidate()
            }
        }

        // Puls-Messung (MeasureClient) nur bei sichtbarem, nicht-ambientem Watchface
        // aktiv halten → kontinuierliche HR-Samples ohne Dauer-Akkuverbrauch.
        scope.launch {
            combine(watchState.isVisible, watchState.isAmbient) { visible, ambient ->
                visible == true && ambient != true
            }.collect { active ->
                if (active) healthSensorManager.startHeartRate()
                else healthSensorManager.stopHeartRate()
            }
        }

        // Bei jedem Aktiv-Werden (Handgelenk heben / Aufwachen aus Ambient) die
        // neuesten Data-Layer-Werte erneut einlesen. Der Renderer-Prozess kann im
        // Schlaf/Ambient-Zustand Live-Updates (onDataChanged) verpassen – dann blieben
        // z.B. die 4 Seite-2-Werte auf dem zuletzt empfangenen Stand „eingefroren".
        // Wichtig: auf den Aktiv-Zustand (visible && !ambient) reagieren, NICHT nur auf
        // isVisible – beim Ambient→Aktiv-Wechsel bleibt isVisible oft durchgehend true,
        // sodass ein reiner isVisible-Collector nicht erneut auslöst.
        scope.launch {
            combine(watchState.isVisible, watchState.isAmbient) { visible, ambient ->
                visible == true && ambient != true
            }.distinctUntilChanged().collect { active ->
                if (active) {
                    // 1. Zuletzt vom Handy gepushte Werte sofort aus dem Data Layer einlesen
                    loadInitialConfig()
                    // 2. Handy zusätzlich um einen frischen Abruf bitten (Wetter, alle
                    //    Akku-Stände, Button-States) – damit beim Display-Einschalten
                    //    nicht nur veraltete Cache-Werte, sondern aktuelle Daten erscheinen.
                    requestPhoneRefresh()
                }
            }
        }

        // Zuletzt persistierten NTP-Offset sofort wiederherstellen, damit das
        // Watchface direkt nach einem (Neu-)Start mit korrigierter Zeit zeichnet,
        // ohne auf die erste Netz-Abfrage zu warten.
        restoreNtpOffsetFromPrefs()

        // NTP-Zeitkorrektur: bei aktivierter Korrektur den Offset gegenüber der
        // Systemzeit selten (alle 6 h) im Hintergrund (IO-Thread, nie in der
        // Zeichenschleife) neu vom NTP-Server ermitteln.
        scope.launch(Dispatchers.IO) {
            while (true) {
                refreshNtpOffset()
                delay(NTP_REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Liest beim Watchface-Start die bestehende Konfiguration aus dem Data Layer,
     * damit Einstellungen auch nach einem Neustart erhalten bleiben.
     */
    private fun loadInitialConfig() {
        scope.launch(Dispatchers.IO) {
            try {
                val dataItems = dataClient.dataItems.await()
                dataItems.forEach { item ->
                    when (item.uri.path) {
                        PATH_WATCHFACE_CONFIG -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.updateFromDataMap(dataMap)
                            Log.d(TAG_PILL, "Initiale Watchface-Config aus Data Layer geladen")
                        }
                        PATH_WEATHER -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.weatherTemp = dataMap.getInt("weather_temp", 0)
                            dataMap.getString("weather_condition")?.let { WatchFaceConfigCache.weatherCondition = it }
                        }
                        PATH_PHONE_BATTERY -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.phoneBatteryLevel = dataMap.getInt("battery_level", -1)
                            WatchFaceConfigCache.phoneBatteryCharging = dataMap.getBoolean("is_charging", false)
                            if (dataMap.containsKey("show_phone_battery")) {
                                WatchFaceConfigCache.showPhoneBattery = dataMap.getBoolean("show_phone_battery", false)
                            }
                        }
                        PATH_CUSTOM_SLOTS -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            dataMap.getString("wf_custom_slot1_label")?.let { WatchFaceConfigCache.customSlot1Label = it }
                            dataMap.getString("wf_custom_slot1_value")?.let { WatchFaceConfigCache.customSlot1Value = it }
                            dataMap.getString("wf_custom_slot2_label")?.let { WatchFaceConfigCache.customSlot2Label = it }
                            dataMap.getString("wf_custom_slot2_value")?.let { WatchFaceConfigCache.customSlot2Value = it }
                            dataMap.getString("wf_custom_slot3_label")?.let { WatchFaceConfigCache.customSlot3Label = it }
                            dataMap.getString("wf_custom_slot3_value")?.let { WatchFaceConfigCache.customSlot3Value = it }
                            dataMap.getString("wf_custom_slot4_label")?.let { WatchFaceConfigCache.customSlot4Label = it }
                            dataMap.getString("wf_custom_slot4_value")?.let { WatchFaceConfigCache.customSlot4Value = it }
                            dataMap.getString("wf_custom_slot4_bar_color")?.let { WatchFaceConfigCache.customSlot4BarColor = it }
                            if (dataMap.containsKey("wf_custom_slot4_bar_min")) WatchFaceConfigCache.customSlot4BarMin = dataMap.getFloat("wf_custom_slot4_bar_min")
                            if (dataMap.containsKey("wf_custom_slot4_bar_max")) WatchFaceConfigCache.customSlot4BarMax = dataMap.getFloat("wf_custom_slot4_bar_max")
                            if (dataMap.containsKey("wf_custom_slot4_bar_is_slider")) WatchFaceConfigCache.customSlot4BarIsSlider = dataMap.getBoolean("wf_custom_slot4_bar_is_slider")
                        }
                        PATH_ACTION_PILL_STATE -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.actionPillState = dataMap.getBoolean("pill_state", false)
                        }
                        PATH_CUSTOM_SLOTS_P2 -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            dataMap.getString("wf_p2_slot1_label")?.let { WatchFaceConfigCache.p2Slot1Label = it }
                            dataMap.getString("wf_p2_slot1_value")?.let { WatchFaceConfigCache.p2Slot1Value = it }
                            dataMap.getString("wf_p2_slot2_label")?.let { WatchFaceConfigCache.p2Slot2Label = it }
                            dataMap.getString("wf_p2_slot2_value")?.let { WatchFaceConfigCache.p2Slot2Value = it }
                            dataMap.getString("wf_p2_slot3_label")?.let { WatchFaceConfigCache.p2Slot3Label = it }
                            dataMap.getString("wf_p2_slot3_value")?.let { WatchFaceConfigCache.p2Slot3Value = it }
                            dataMap.getString("wf_p2_slot4_label")?.let { WatchFaceConfigCache.p2Slot4Label = it }
                            dataMap.getString("wf_p2_slot4_value")?.let { WatchFaceConfigCache.p2Slot4Value = it }
                            dataMap.getString("wf_p2_bar_value")?.let   { WatchFaceConfigCache.p2BarValue   = it }
                        }
                        PATH_CONFIG_P2 -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.updateP2ConfigFromDataMap(dataMap)
                            Log.d(TAG_PILL, "Initiale Seite-2-Konfig geladen")
                        }
                        PATH_CONNECTION_CONFIG -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.updateConnectionFromDataMap(dataMap)
                            Log.d(TAG_PILL, "Initiale Verbindungs-Konfig geladen")
                        }
                        PATH_STATES -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            dataMap.getString("states_json")?.let { SmartHomeStateCache.updateFromJson(it) }
                        }
                        PATH_PHONE_HEALTH -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            if (dataMap.containsKey(KEY_PHONE_HEART_RATE))    WatchFaceConfigCache.phoneHeartRate    = dataMap.getInt(KEY_PHONE_HEART_RATE)
                            if (dataMap.containsKey(KEY_PHONE_SPO2))          WatchFaceConfigCache.phoneSpO2         = dataMap.getInt(KEY_PHONE_SPO2)
                            if (dataMap.containsKey(KEY_PHONE_CALORIES))      WatchFaceConfigCache.phoneCalories     = dataMap.getInt(KEY_PHONE_CALORIES)
                            if (dataMap.containsKey(KEY_PHONE_SLEEP_MINUTES)) WatchFaceConfigCache.phoneSleepMinutes = dataMap.getInt(KEY_PHONE_SLEEP_MINUTES)
                            // Frische-Zeitstempel mitsetzen: ohne ihn gilt der initial geladene
                            // Wert als veraltet (phoneDataFresh = false) und der Health-Slot zeigt
                            // dauerhaft "--" – bis zufaellig ein Live-onDataChanged eintrifft.
                            // Den Sende-Zeitstempel des Data-Items verwenden, damit echt alte
                            // Werte (>30 min) weiterhin korrekt als "--" behandelt werden.
                            val ts = dataMap.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
                            WatchFaceConfigCache.phoneHealthLastReceived = if (ts > 0) ts else System.currentTimeMillis()
                        }
                        PATH_P2_PILL_STATES -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            if (dataMap.containsKey("wf_p2_pill1_state")) WatchFaceConfigCache.p2Pill1State = dataMap.getBoolean("wf_p2_pill1_state")
                            if (dataMap.containsKey("wf_p2_pill2_state")) WatchFaceConfigCache.p2Pill2State = dataMap.getBoolean("wf_p2_pill2_state")
                        }
                    }
                }
                dataItems.release()
                // Beim Aufwachen den persistierten NTP-Offset wieder anwenden, falls
                // der flüchtige Cache nach einem Neustart noch 0 ist.
                restoreNtpOffsetFromPrefs()
                // Nach dem (Neu-)Einlesen sofort neu zeichnen, damit aufgefrischte
                // Werte beim Aufwachen direkt sichtbar werden.
                invalidate()
            } catch (e: Exception) {
                Log.e(TAG_PILL, "Initiale Config konnte nicht geladen werden", e)
            }
        }
    }

    /**
     * Direkter DataClient-Listener — zuverlässiger als der manifest-registrierte
     * WearableListenerService, da er im selben Prozess wie der Renderer läuft.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                PATH_WATCHFACE_CONFIG -> {
                    Log.d(TAG_PILL, "Watchface-Config via DataClient-Listener empfangen")
                    WatchFaceConfigCache.updateFromDataMap(dataMap)
                    // NTP-Korrektur ggf. sofort (neu) ermitteln, statt bis zu 30 min zu warten
                    refreshNtpOffset()
                }
                PATH_WEATHER -> {
                    WatchFaceConfigCache.weatherTemp = dataMap.getInt("weather_temp", 0)
                    dataMap.getString("weather_condition")?.let { WatchFaceConfigCache.weatherCondition = it }
                }
                PATH_PHONE_BATTERY -> {
                    WatchFaceConfigCache.phoneBatteryLevel = dataMap.getInt("battery_level", -1)
                    WatchFaceConfigCache.phoneBatteryCharging = dataMap.getBoolean("is_charging", false)
                    if (dataMap.containsKey("show_phone_battery")) {
                        WatchFaceConfigCache.showPhoneBattery = dataMap.getBoolean("show_phone_battery", false)
                    }
                }
                PATH_CUSTOM_SLOTS -> {
                    dataMap.getString("wf_custom_slot1_label")?.let { WatchFaceConfigCache.customSlot1Label = it }
                    dataMap.getString("wf_custom_slot1_value")?.let { WatchFaceConfigCache.customSlot1Value = it }
                    dataMap.getString("wf_custom_slot2_label")?.let { WatchFaceConfigCache.customSlot2Label = it }
                    dataMap.getString("wf_custom_slot2_value")?.let { WatchFaceConfigCache.customSlot2Value = it }
                    dataMap.getString("wf_custom_slot3_label")?.let { WatchFaceConfigCache.customSlot3Label = it }
                    dataMap.getString("wf_custom_slot3_value")?.let { WatchFaceConfigCache.customSlot3Value = it }
                    dataMap.getString("wf_custom_slot4_label")?.let { WatchFaceConfigCache.customSlot4Label = it }
                    dataMap.getString("wf_custom_slot4_value")?.let { WatchFaceConfigCache.customSlot4Value = it }
                    dataMap.getString("wf_custom_slot4_bar_color")?.let { WatchFaceConfigCache.customSlot4BarColor = it }
                    if (dataMap.containsKey("wf_custom_slot4_bar_min")) WatchFaceConfigCache.customSlot4BarMin = dataMap.getFloat("wf_custom_slot4_bar_min")
                    if (dataMap.containsKey("wf_custom_slot4_bar_max")) WatchFaceConfigCache.customSlot4BarMax = dataMap.getFloat("wf_custom_slot4_bar_max")
                    if (dataMap.containsKey("wf_custom_slot4_bar_is_slider")) WatchFaceConfigCache.customSlot4BarIsSlider = dataMap.getBoolean("wf_custom_slot4_bar_is_slider")
                }
                PATH_ACTION_PILL_STATE -> {
                    WatchFaceConfigCache.actionPillState = dataMap.getBoolean("pill_state", false)
                }
                PATH_P2_PILL_STATES -> {
                    if (dataMap.containsKey("wf_p2_pill1_state")) WatchFaceConfigCache.p2Pill1State = dataMap.getBoolean("wf_p2_pill1_state")
                    if (dataMap.containsKey("wf_p2_pill2_state")) WatchFaceConfigCache.p2Pill2State = dataMap.getBoolean("wf_p2_pill2_state")
                }
                PATH_STATES -> {
                    dataMap.getString("states_json")?.let { SmartHomeStateCache.updateFromJson(it) }
                }
                PATH_PHONE_HEALTH -> {
                    val newHr   = if (dataMap.containsKey(KEY_PHONE_HEART_RATE)) dataMap.getInt(KEY_PHONE_HEART_RATE) else WatchFaceConfigCache.phoneHeartRate
                    val newSpO2 = if (dataMap.containsKey(KEY_PHONE_SPO2))       dataMap.getInt(KEY_PHONE_SPO2)       else WatchFaceConfigCache.phoneSpO2
                    val newKcal = if (dataMap.containsKey(KEY_PHONE_CALORIES))   dataMap.getInt(KEY_PHONE_CALORIES)   else WatchFaceConfigCache.phoneCalories
                    WatchFaceConfigCache.phoneHeartRate = newHr
                    WatchFaceConfigCache.phoneSpO2      = newSpO2
                    WatchFaceConfigCache.phoneCalories  = newKcal
                    if (dataMap.containsKey(KEY_PHONE_SLEEP_MINUTES)) WatchFaceConfigCache.phoneSleepMinutes = dataMap.getInt(KEY_PHONE_SLEEP_MINUTES)
                    // Immer als "frisch" markieren – 0 bedeutet "kein Messwert", nicht "offline"
                    WatchFaceConfigCache.phoneHealthLastReceived = System.currentTimeMillis()
                }
                PATH_CUSTOM_SLOTS_P2 -> {
                    dataMap.getString("wf_p2_slot1_label")?.let { WatchFaceConfigCache.p2Slot1Label = it }
                    dataMap.getString("wf_p2_slot1_value")?.let { WatchFaceConfigCache.p2Slot1Value = it }
                    dataMap.getString("wf_p2_slot2_label")?.let { WatchFaceConfigCache.p2Slot2Label = it }
                    dataMap.getString("wf_p2_slot2_value")?.let { WatchFaceConfigCache.p2Slot2Value = it }
                    dataMap.getString("wf_p2_slot3_label")?.let { WatchFaceConfigCache.p2Slot3Label = it }
                    dataMap.getString("wf_p2_slot3_value")?.let { WatchFaceConfigCache.p2Slot3Value = it }
                    dataMap.getString("wf_p2_slot4_label")?.let { WatchFaceConfigCache.p2Slot4Label = it }
                    dataMap.getString("wf_p2_slot4_value")?.let { WatchFaceConfigCache.p2Slot4Value = it }
                    dataMap.getString("wf_p2_bar_value")?.let   { WatchFaceConfigCache.p2BarValue   = it }
                }
                PATH_CONFIG_P2 -> {
                    WatchFaceConfigCache.updateP2ConfigFromDataMap(dataMap)
                }
                PATH_CONNECTION_CONFIG -> {
                    WatchFaceConfigCache.updateConnectionFromDataMap(dataMap)
                    // Neue Verbindungs-/Datenpunkt-Konfig → sofort frisch abrufen
                    WatchDataSyncManager.syncNow()
                }
            }
        }
        // Empfangene Updates sofort zeichnen — sonst bleiben neue Werte bei
        // Display-aus/Ambient unsichtbar, da dort kein Sekunden-invalidate läuft.
        invalidate()
    }

    override suspend fun createSharedAssets(): SharedAssets = object : SharedAssets {
        override fun onDestroy() {}
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        frameworkTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        val config    = WatchFaceConfigCache

        // NTP-Zeitkorrektur: bei aktivierter Korrektur den ermittelten Offset auf
        // die Systemzeit aufaddieren, damit das Watchface die exakte Zeit zeigt.
        val zonedDateTime: ZonedDateTime =
            if (config.ntpEnabled && config.ntpOffsetMs != 0L)
                frameworkTime.plus(config.ntpOffsetMs, ChronoUnit.MILLIS)
            else frameworkTime

        canvas.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), backgroundPaint)

        if (!isAmbient) {
            val w = bounds.width()
            val h = bounds.height()
            if (currentPage == 1) {
                // Seite 2: eigenes Hintergrundbild (unabhängig von Seite 1)
                if (backgroundBitmapP2Scaled?.width != w || backgroundBitmapP2Scaled?.height != h) {
                    if (backgroundBitmapP2 == null) {
                        backgroundBitmapP2 = BitmapFactory.decodeResource(context.resources, com.iosync.watchface.R.drawable.watchface_background_p2)
                    }
                    backgroundBitmapP2Scaled = backgroundBitmapP2?.let {
                        Bitmap.createScaledBitmap(it, w, h, true)
                    }
                }
                backgroundBitmapP2Scaled?.let { canvas.drawBitmap(it, 0f, 0f, backgroundBitmapPaint) }
            } else if (config.showBackground) {
                // Seite 1: konfigurierbares Hintergrundbild
                if (backgroundBitmapScaled?.width != w || backgroundBitmapScaled?.height != h) {
                    if (backgroundBitmap == null) {
                        backgroundBitmap = BitmapFactory.decodeResource(context.resources, com.iosync.watchface.R.drawable.watchface_background)
                    }
                    backgroundBitmapScaled = backgroundBitmap?.let {
                        Bitmap.createScaledBitmap(it, w, h, true)
                    }
                }
                backgroundBitmapScaled?.let { canvas.drawBitmap(it, 0f, 0f, backgroundBitmapPaint) }
            }
        }

        val cx     = bounds.exactCenterX()
        val cy     = bounds.exactCenterY()
        val radius = minOf(cx, cy)

        // 9-Uhr-Tipp-Zone aktualisieren (linke Seite, mittlere Höhe)
        nineOClockTapBounds.set(0f, cy - radius * 0.28f, cx * 0.50f, cy + radius * 0.28f)

        // Seite 2 im aktiven Modus rendern (überspringt gesamte Seite-1-Logik)
        if (!isAmbient && currentPage == 1) {
            drawPage2(canvas, cx, cy, radius)
            return
        }

        // Stoppuhr-Modus aktiv? (nur auf Seite 1, nicht im Ambient)
        val stopwatchActive = !isAmbient && currentPage == 0 && stopwatchMode != StopwatchMode.OFF
        val swElapsed = if (stopwatchActive) stopwatchElapsedMs() else 0L

        val timeStr      = if (stopwatchActive) formatStopwatchMain(swElapsed)
                           else timeFormatter.format(zonedDateTime)
        val timeFontSize = radius * 0.437f

        try {

        if (!isAmbient) {
            if (stopwatchActive) {
                drawStopwatchRing(canvas, bounds, swElapsed)
            } else if (config.showSecondsRing) {
                drawSecondsRing(canvas, bounds, zonedDateTime.second)
            }
        }

        if (!isAmbient && config.showTicks) {
            drawTickMarks(canvas, cx, cy, radius)
        }

        if (isAmbient) updateBurnInProtection()

        val bx = if (isAmbient) burnInOffsetX else 0f
        val by = if (isAmbient) burnInOffsetY else 0f

        if (isAmbient) {
            ambientTimePaint.textSize  = timeFontSize
            ambientTimePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(timeStr, cx + bx, cy + by + timeFontSize * 0.30f, ambientTimePaint)
        } else {
            val timeColor = colorFromId(config.timeColorId)
            val dateColor = colorFromId(config.dateColorId)
            timePaint.color    = timeColor
            timePaint.textSize = timeFontSize

            // Uhr etwas nach links und oben verschoben
            val timeCx       = cx - radius * 0.04f
            val timeBaseline = cy + timeFontSize * 0.03f

            // weekdayBottomY wird je nach Layout (mit/ohne Sekunden) gesetzt
            val weekdayBottomY: Float

            if (config.showSeconds) {
                val secStr      = if (stopwatchActive) formatStopwatchCenti(swElapsed)
                                  else secondsFormatter.format(zonedDateTime)
                val secFontSize = timeFontSize * 0.475f
                secondsPaint.color    = if (config.secondsNumberColorId == "dim_time") dimColor(timeColor, 0.75f) else colorFromId(config.secondsNumberColorId)
                secondsPaint.textSize = secFontSize

                val timeWidth = timePaint.measureText(timeStr)
                val secWidth  = secondsPaint.measureText(secStr)
                val gap       = radius * 0.025f
                val startX    = timeCx - (timeWidth + gap + secWidth) / 2f
                val secX      = startX + timeWidth + gap

                canvas.drawText(timeStr, startX, timeBaseline, timePaint)

                val timeFm      = timePaint.fontMetrics
                val secFm       = secondsPaint.fontMetrics
                val secBaseline = timeBaseline + timeFm.ascent - secFm.ascent
                canvas.drawText(secStr, secX, secBaseline, secondsPaint)

                // Tipp-Zonen merken: Stunden/Minuten → Stoppuhr, Tages-Sekunden → Kalender
                val tapPadV = timeFontSize * 0.12f
                timeTapBounds.set(startX, timeBaseline + timeFm.ascent - tapPadV,
                    startX + timeWidth, timeBaseline + timeFm.descent + tapPadV)
                daySecondsTapBounds.set(secX - gap, secBaseline + secFm.ascent - tapPadV,
                    secX + secWidth + gap, secBaseline + secFm.descent + tapPadV)

                // Datum "MO DD" unterhalb der Sekunden, linksbündig mit Sekunden
                weekdayBottomY = if (config.showWeekday) {
                    val dateStr      = "${weekdayShort(zonedDateTime)} ${zonedDateTime.dayOfMonth}"
                    val dateFontSize = secFontSize * 0.72f
                    weekdayPaint.color     = dateColor
                    weekdayPaint.textSize  = dateFontSize
                    weekdayPaint.textAlign = Paint.Align.LEFT
                    val dateY = secBaseline + (secFm.descent - secFm.ascent) * 0.88f
                    canvas.drawText(dateStr, secX, dateY, weekdayPaint)
                    dateY + weekdayPaint.fontMetrics.descent
                } else {
                    secBaseline + secFm.descent
                }
            } else {
                val timeWidth = timePaint.measureText(timeStr)
                canvas.drawText(timeStr, timeCx - timeWidth / 2f, timeBaseline, timePaint)

                // Tipp-Zone Stunden/Minuten merken; ohne Sekunden keine Kalender-Zone
                val timeFm  = timePaint.fontMetrics
                val tapPadV = timeFontSize * 0.12f
                timeTapBounds.set(timeCx - timeWidth / 2f, timeBaseline + timeFm.ascent - tapPadV,
                    timeCx + timeWidth / 2f, timeBaseline + timeFm.descent + tapPadV)
                daySecondsTapBounds.setEmpty()

                // Ohne Sekunden: Datum zentriert unterhalb der Zeit
                weekdayBottomY = if (config.showWeekday) {
                    val dateStr      = "${weekdayShort(zonedDateTime)} ${zonedDateTime.dayOfMonth}"
                    val dateFontSize = timeFontSize * 0.252f
                    weekdayPaint.color     = dateColor
                    weekdayPaint.textSize  = dateFontSize
                    weekdayPaint.textAlign = Paint.Align.CENTER
                    val dateY = cy + timeFontSize * 0.03f + radius * 0.33f
                    canvas.drawText(dateStr, timeCx, dateY, weekdayPaint)
                    dateY + weekdayPaint.fontMetrics.descent
                } else {
                    cy + timeFontSize * 0.03f + radius * 0.18f
                }
            }

            // ioBroker-Datenpunkte (optional, vom Smartphone aktivierbar)
            if (config.showIoBrokerData) {
                drawIoBrokerData(canvas, cx, cy, radius, weekdayBottomY)
            }

            // Layout oben: Watch-Akku (links) — Wetter (Mitte) — Phone-Akku (rechts)
            val watchBatteryScale = config.watchBatteryTextScale / 100f
            val baseRingRadius = radius * 0.115f
            val ringRadius = baseRingRadius * watchBatteryScale
            val ringCy     = cy - radius * 0.64f
            val color1 = colorFromId(config.batteryRingColor1)
            val color2 = colorFromId(config.batteryRingColor2)
            // Watch-Akku links
            drawBatteryRing(canvas, cx - radius * 0.42f, ringCy, ringRadius,
                getWatchBatteryLevel(), color1, color2)
            // Phone-Akku rechts (gespiegelt)
            if (config.showPhoneBattery) {
                drawBatteryRing(canvas, cx + radius * 0.42f, ringCy, ringRadius,
                    config.phoneBatteryLevel, color1, color2)
            }

            // Wetter-Kreis oben mittig (unter 12-Uhr-Markierung)
            if (config.showWeather) {
                drawWeatherCircle(canvas, cx, cy, radius)
            }

            // Custom ioBroker-Slots (2 Datenpunkte unterhalb der Uhrzeit)
            if (config.showCustomSlots) {
                drawCustomSlots(canvas, cx, cy, radius, weekdayBottomY)
            }

            // Boden-Komplikationen (2 Kreistaschen unten: Puls + wählbare Metrik)
            drawBottomComplications(canvas, cx, cy, radius)

            // Schritte (links) und Schlafdauer (rechts, gespiegelt) oberhalb der Uhrzeit
            if (config.showSteps) {
                drawStepsAndSleep(canvas, cx, cy, radius)
            }

        }

        drawComplications(canvas, zonedDateTime, isAmbient)

        // Aktions-Pille nach Komplikationen — liegt visuell und taktil oben
        if (!isAmbient && config.actionPillEnabled) {
            drawActionPill(canvas, cx, cy, radius)
        }

        // Config-Bestätigung anzeigen (2 s nach Empfang)
        if (!isAmbient) {
            val elapsed = System.currentTimeMillis() - config.lastConfigReceivedAt
            if (elapsed in 0 until CONFIRM_DURATION_MS) {
                val alpha = ((1f - elapsed.toFloat() / CONFIRM_DURATION_MS) * 255).toInt()
                val text = "Config empfangen"
                confirmPaint.textSize = radius * 0.10f
                confirmPaint.alpha = alpha
                confirmBgPaint.alpha = (alpha * 0.78f).toInt()
                val textY = cy - radius * 0.72f
                val textBounds = Rect()
                confirmPaint.getTextBounds(text, 0, text.length, textBounds)
                val padH = radius * 0.06f
                val padV = radius * 0.03f
                canvas.drawRoundRect(
                    RectF(
                        cx - textBounds.width() / 2f - padH,
                        textY + textBounds.top - padV,
                        cx + textBounds.width() / 2f + padH,
                        textY + textBounds.bottom + padV
                    ),
                    radius * 0.03f, radius * 0.03f, confirmBgPaint
                )
                canvas.drawText(text, cx, textY, confirmPaint)
            }
        }

        // Diagnose-Anzeige (selbst-versteckend): nur sichtbar, wenn der Adapter
        // genutzt werden soll, aber etwas nicht stimmt. Sagt direkt, woran es liegt.
        if (!isAmbient && config.ioUseAdapter) {
            val diagText = when {
                config.ioHost.isBlank() ->
                    "Keine Verbindungs-Config"
                WatchDataSyncManager.lastFetchAt > 0L && !WatchDataSyncManager.lastFetchOk ->
                    WatchDataSyncManager.lastFetchError
                        .takeIf { it.isNotBlank() }
                        ?.let { "Fehler: ${it.take(40)}" }
                        ?: "Adapter nicht erreichbar"
                else -> null
            }
            if (diagText != null) {
                diagPaint.textSize = radius * 0.09f
                diagPaint.alpha = 255
                confirmBgPaint.alpha = 200
                val diagY = cy + radius * 0.86f
                val diagBounds = Rect()
                diagPaint.getTextBounds(diagText, 0, diagText.length, diagBounds)
                val padH = radius * 0.06f
                val padV = radius * 0.03f
                canvas.drawRoundRect(
                    RectF(
                        cx - diagBounds.width() / 2f - padH,
                        diagY + diagBounds.top - padV,
                        cx + diagBounds.width() / 2f + padH,
                        diagY + diagBounds.bottom + padV
                    ),
                    radius * 0.03f, radius * 0.03f, confirmBgPaint
                )
                canvas.drawText(diagText, cx, diagY, diagPaint)
            }
        }

        } catch (e: Exception) {
            // Fallback: Uhrzeit immer anzeigen, auch wenn andere Elemente crashen
            Log.e("WatchFaceRenderer", "Render-Fehler: ${e.message}", e)
            timePaint.color = Color.parseColor("#E8E8E8")
            timePaint.textSize = timeFontSize
            timePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(timeStr, cx, cy + timeFontSize * 0.15f, timePaint)
            timePaint.textAlign = Paint.Align.LEFT
        }
    }

    /**
     * Zeichnet bis zu 2 ioBroker-Datenpunkte unterhalb des Wochentags.
     * Für %-Werte: horizontaler Fortschrittsbalken mit Farbkodierung.
     * Für alle anderen Werte: Textanzeige.
     */
    private fun drawIoBrokerData(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        startY: Float
    ) {
        val states = SmartHomeStateCache.states.take(2)
        if (states.isEmpty()) return

        val scaleFactor = 1.0f
        val labelSize   = radius * 0.085f * scaleFactor
        val valueSize   = radius * 0.115f * scaleFactor
        val barHeight   = radius * 0.042f
        val barWidth    = radius * 0.68f
        val lineSpacing = radius * 0.055f
        // Untere Grenze: oberhalb des unteren Komplikations-Slots
        val maxY = cy + radius * 0.62f

        var yPos = startY + lineSpacing

        for (state in states) {
            if (yPos + valueSize + barHeight + lineSpacing > maxY) break

            // Datenpunkt-Name (Label)
            dpLabelPaint.textSize = labelSize
            canvas.drawText(state.displayLabel, cx, yPos, dpLabelPaint)
            yPos += labelSize * 1.15f

            if (state.isPercent && state.numericValue != null) {
                // ── Fortschrittsbalken für %-Werte ────────────────────────────
                val fraction  = (state.numericValue!! / 100f).coerceIn(0f, 1f)
                val barLeft   = cx - barWidth / 2f
                val barRight  = cx + barWidth / 2f
                val barCorner = barHeight / 2f

                canvas.drawRoundRect(
                    RectF(barLeft, yPos, barRight, yPos + barHeight),
                    barCorner, barCorner, progressBgPaint
                )

                if (fraction > 0f) {
                    progressFgPaint.color = when {
                        fraction < 0.25f -> Color.parseColor("#E53935")
                        fraction < 0.50f -> Color.parseColor("#FF9800")
                        else             -> Color.parseColor("#EAFF00")
                    }
                    val minFill = barLeft + barCorner * 2
                    canvas.drawRoundRect(
                        RectF(barLeft, yPos, (barLeft + barWidth * fraction).coerceAtLeast(minFill), yPos + barHeight),
                        barCorner, barCorner, progressFgPaint
                    )
                }

                dpValuePaint.textSize  = labelSize * 0.9f
                dpValuePaint.textAlign = Paint.Align.CENTER
                dpValuePaint.color     = Color.parseColor("#AAAAAA")
                canvas.drawText(state.displayValue, cx, yPos + barHeight + labelSize, dpValuePaint)
                yPos += barHeight + labelSize * 1.2f + lineSpacing * 0.4f

            } else {
                // ── Textwert ──────────────────────────────────────────────────
                dpValuePaint.textSize  = valueSize
                dpValuePaint.textAlign = Paint.Align.CENTER
                dpValuePaint.color     = Color.parseColor("#CCCCCC")
                canvas.drawText(state.displayValue, cx, yPos + valueSize * 0.85f, dpValuePaint)
                yPos += valueSize * 1.0f + lineSpacing * 0.3f
            }

            yPos += lineSpacing * 0.5f
        }
    }

    /**
     * Liest den aktuellen Akkustand der Uhr (0–100) via BatteryManager.
     * Gibt -1 zurück wenn der Wert nicht verfügbar ist.
     */
    private fun getWatchBatteryLevel(): Int = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) level * 100 / scale else -1
    } catch (e: Exception) { -1 }

    /**
     * Zeichnet einen kleinen Akku-Ring mit Prozentzahl mittig.
     * Hintergrundring (gedimmt) + Vordergrundring als Farbverlauf-Bogen von color1→color2.
     */
    private fun drawBatteryRing(
        canvas: Canvas,
        ringCx: Float,
        ringCy: Float,
        ringRadius: Float,
        level: Int,
        color1: Int,
        color2: Int
    ) {
        val strokeW = ringRadius * 0.20f * (WatchFaceConfigCache.batteryRingStrokeScale / 100f)
        batteryRingBgPaint.strokeWidth = strokeW
        batteryRingFgPaint.strokeWidth = strokeW

        // Vertiefungs-Effekt hinter dem Ring
        val recessR = ringRadius + strokeW * 1.6f
        drawEmbossedCircleRecess(canvas, ringCx, ringCy, recessR)

        val oval = RectF(
            ringCx - ringRadius, ringCy - ringRadius,
            ringCx + ringRadius, ringCy + ringRadius
        )

        // Hintergrundring (voller Kreis, gedimmt)
        canvas.drawArc(oval, -90f, 360f, false, batteryRingBgPaint)

        // Füll-Bogen mit Gradient (von 12 Uhr im Uhrzeigersinn)
        if (level > 0) {
            val sweepAngle = level / 100f * 360f
            // Warnstufen: nur die Startfarbe (von 0 an) wird ersetzt.
            // Priorität: die Warnstufe mit dem KLEINEREN Schwellwert (kritischer) hat Vorrang.
            // Liegt der Akkustand über ALLEN Schwellen, wird immer color1 (konfigurierte Farbe) angezeigt.
            val cfg = WatchFaceConfigCache
            val w1Active = cfg.batteryWarn1Threshold > 0 && level <= cfg.batteryWarn1Threshold
            val w2Active = cfg.batteryWarn2Threshold > 0 && level <= cfg.batteryWarn2Threshold
            val startColor = when {
                w1Active && w2Active -> {
                    // Beide aktiv → die mit dem kleineren Schwellwert gewinnt (kritischer)
                    if (cfg.batteryWarn1Threshold <= cfg.batteryWarn2Threshold)
                        colorFromId(cfg.batteryWarn1Color)
                    else
                        colorFromId(cfg.batteryWarn2Color)
                }
                w1Active -> colorFromId(cfg.batteryWarn1Color)
                w2Active -> colorFromId(cfg.batteryWarn2Color)
                else     -> color1  // über allen Schwellen → zurück zur konfigurierten Farbe
            }
            val shader = SweepGradient(ringCx, ringCy, intArrayOf(startColor, color2), null)
            val matrix = Matrix()
            matrix.postRotate(-90f, ringCx, ringCy)
            shader.setLocalMatrix(matrix)
            batteryRingFgPaint.shader = shader
            canvas.drawArc(oval, -90f, sweepAngle, false, batteryRingFgPaint)
            batteryRingFgPaint.shader = null
        }

        // Prozentzahl in der Mitte des Rings
        val text = if (level >= 0) "$level%" else "--"
        batteryPercentPaint.textSize = ringRadius * 0.88f
        val fm = batteryPercentPaint.fontMetrics
        canvas.drawText(text, ringCx, ringCy - (fm.ascent + fm.descent) / 2f, batteryPercentPaint)
    }

    /**
     * Zeichnet die Aktions-Pille bei 6 Uhr, oberhalb des unteren Komplikations-Slots.
     * Farbe: Cyan (true/aktiv) oder Rot (false/inaktiv), konfigurierbar via Android App.
     * Doppelklick sendet eine Message an die verbundene Android App.
     */
    private fun drawActionPill(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config   = WatchFaceConfigCache
        val halfW    = radius * 0.266f
        val halfH    = radius * 0.060f
        // Pille: klar unterhalb der Health-Daten, genug Abstand zum Notification-Dot
        val centerY  = cy + radius * 0.72f

        pillBounds.set(cx - halfW, centerY - halfH, cx + halfW, centerY + halfH)
        // Tapp-Zone: nach oben/unten/seitlich erweitert für einfaches Antippen
        val tapPad = radius * 0.06f
        pillTapBounds.set(
            pillBounds.left  - tapPad,
            pillBounds.top   - tapPad,
            pillBounds.right + tapPad,
            pillBounds.bottom + tapPad
        )

        // Gedrückt-Zustand nach PILL_PRESS_DURATION_MS automatisch zurücksetzen
        val isPressedNow = pillPressed && (System.currentTimeMillis() - pillPressedAt < PILL_PRESS_DURATION_MS)

        val stateColor = colorFromPillId(if (config.actionPillState) config.actionPillColorTrue else config.actionPillColorFalse)
        // Beim Drücken: Aktiv-Farbe (True-Farbe) aufleuchten, sonst Zustandsfarbe
        val activeColor = if (isPressedNow) colorFromPillId(config.actionPillColorTrue) else stateColor

        // Gefüllte Pille (beim Drücken voller, sonst leicht transparent)
        val fillAlpha = if (isPressedNow) 240 else 180
        pillFillPaint.color = Color.argb(
            fillAlpha,
            Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor)
        )
        canvas.drawRoundRect(pillBounds, halfH, halfH, pillFillPaint)

        // Rand in voller Farbe
        pillStrokePaint.color = activeColor
        canvas.drawRoundRect(pillBounds, halfH, halfH, pillStrokePaint)
    }

    private fun colorFromPillId(id: String): Int = when (id) {
        "cyan"        -> Color.parseColor("#00BCD4")
        "red"         -> Color.parseColor("#F44336")
        "green"       -> Color.parseColor("#4CAF50")
        "neon_yellow" -> Color.parseColor("#EAFF00")
        "orange"      -> Color.parseColor("#FF9800")
        "white"       -> Color.WHITE
        "purple"      -> Color.parseColor("#9C27B0")
        else          -> Color.parseColor("#00BCD4")
    }


    /**
     * Fordert beim Display-Einschalten einen sofortigen Daten-Refresh an.
     * Ab v5 fragt die Uhr selbst (ioBroker-Slots/States, Wetter) – kein Handy nötig.
     */
    private fun requestPhoneRefresh() {
        WatchDataSyncManager.syncNow()
    }

    /** Schaltet die Aktions-Pille (Seite 1) direkt am ioBroker-Adapter. */
    private fun triggerPillAction() {
        WatchDataSyncManager.toggleActionPill()
    }

    /** Schaltet eine Seite-2-Pille (pill=1 → 7-Uhr, pill=2 → 5-Uhr) direkt am Adapter. */
    private fun triggerP2PillAction(pill: Int) {
        if (pill == 1) WatchDataSyncManager.toggleP2Pill1() else WatchDataSyncManager.toggleP2Pill2()
    }

    /** Schreibt den getippten Slider-Wert (Seite 2) direkt in den ioBroker-Datenpunkt. */
    private fun sendSliderValue(value: Int) {
        WatchDataSyncManager.setBarValue(value)
    }

    /** Sendet den aktuellen NTP-Offset via DataItem an die verbundene Android App. */
    private fun sendNtpOffsetToPhone(offsetMs: Long, server: String) {
        scope.launch {
            try {
                val request = PutDataMapRequest.create(PATH_NTP_OFFSET_FROM_WATCH).apply {
                    dataMap.putLong(KEY_NTP_OFFSET_MS, offsetMs)
                    dataMap.putString("ntp_server", server)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
                Log.d(TAG_PILL, "NTP-Offset an App gesendet: $offsetMs ms ($server)")
            } catch (e: Exception) {
                Log.w(TAG_PILL, "NTP-Offset senden fehlgeschlagen: ${e.message}")
            }
        }
    }

    // ── Wetter-Kreis (oben mittig, unter 12-Uhr-Markierung) ──────────────────

    /**
     * Zeichnet einen kleinen Kreis oben mittig mit Wettersymbol und Temperatur.
     * Positioniert zwischen den beiden Akku-Ringen.
     */
    private fun drawWeatherCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val circleRadius = radius * 0.18f
        val circleCx = cx
        // nach oben verschoben, Steps-Anzeige liegt darunter direkt über der Uhrzeit
        val circleCy = cy - radius * 0.64f - 20f * density

        // Tap-Bereich für Wetter-App-Start merken (etwas größer als der Kreis).
        // Seiten/oben großzügig, unten (Richtung kcal) klein, damit kcal nicht getroffen wird.
        val weatherTapPad = circleRadius * 0.40f
        val weatherTapPadBottom = circleRadius * 0.05f
        weatherTapBounds.set(
            circleCx - circleRadius - weatherTapPad,
            circleCy - circleRadius - weatherTapPad,
            circleCx + circleRadius + weatherTapPad,
            circleCy + circleRadius + weatherTapPadBottom
        )

        // Wettersymbol zeichnen
        val iconSize = circleRadius * 0.80f
        val iconCy = circleCy - circleRadius * 0.22f
        drawWeatherIcon(canvas, circleCx, iconCy, iconSize, config.weatherCondition)

        // Temperatur: aus OpenWeather oder aus ioBroker-Datenpunkt
        val weatherScale = config.weatherTextScale / 100f
        weatherTempPaint.textSize = circleRadius * 0.72f * weatherScale
        val displayTemp: String = if (config.weatherTempSource == "iobroker" && config.weatherIoBrokerId.isNotBlank()) {
            val state = SmartHomeStateCache.states.firstOrNull { it.id == config.weatherIoBrokerId }
            val rawVal = state?.value ?: config.weatherTemp.toString()
            val num = rawVal.toFloatOrNull()
            if (num != null) "${num.toInt()}°" else "$rawVal°"
        } else {
            "${config.weatherTemp}°"
        }
        canvas.drawText(displayTemp, circleCx, circleCy + circleRadius * 0.55f + 5f * density, weatherTempPaint)
    }

    /**
     * Zeichnet ein einfaches Wettersymbol mit Canvas-Primitiven.
     */
    private fun drawWeatherIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, condition: String) {
        when (condition) {
            "clear" -> {
                // Sonne: gelber Kreis + Strahlen
                weatherIconPaint.color = Color.parseColor("#FFD600")
                weatherIconPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, size * 0.35f, weatherIconPaint)
                weatherIconPaint.style = Paint.Style.STROKE
                weatherIconPaint.strokeWidth = size * 0.08f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    canvas.drawLine(
                        cx + size * 0.50f * cos, cy + size * 0.50f * sin,
                        cx + size * 0.75f * cos, cy + size * 0.75f * sin,
                        weatherIconPaint
                    )
                }
                weatherIconPaint.style = Paint.Style.FILL
            }
            "partly_cloudy" -> {
                // Kleine Sonne + Wolke davor
                weatherIconPaint.color = Color.parseColor("#FFD600")
                weatherIconPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx - size * 0.15f, cy - size * 0.15f, size * 0.25f, weatherIconPaint)
                drawCloudShape(canvas, cx + size * 0.1f, cy + size * 0.1f, size * 0.7f, Color.parseColor("#BBBBBB"))
            }
            "cloudy" -> {
                drawCloudShape(canvas, cx, cy, size, Color.parseColor("#999999"))
            }
            "rain" -> {
                drawCloudShape(canvas, cx, cy - size * 0.15f, size * 0.8f, Color.parseColor("#888888"))
                // Regentropfen
                weatherIconPaint.color = Color.parseColor("#42A5F5")
                weatherIconPaint.style = Paint.Style.STROKE
                weatherIconPaint.strokeWidth = size * 0.08f
                weatherIconPaint.strokeCap = Paint.Cap.ROUND
                for (i in -1..1) {
                    val dx = i * size * 0.25f
                    canvas.drawLine(cx + dx, cy + size * 0.25f, cx + dx - size * 0.05f, cy + size * 0.55f, weatherIconPaint)
                }
                weatherIconPaint.style = Paint.Style.FILL
            }
            "snow" -> {
                drawCloudShape(canvas, cx, cy - size * 0.15f, size * 0.8f, Color.parseColor("#AAAAAA"))
                // Schneepunkte
                weatherIconPaint.color = Color.WHITE
                weatherIconPaint.style = Paint.Style.FILL
                val dotR = size * 0.06f
                for (i in -1..1) {
                    canvas.drawCircle(cx + i * size * 0.25f, cy + size * 0.35f, dotR, weatherIconPaint)
                    canvas.drawCircle(cx + i * size * 0.20f + size * 0.10f, cy + size * 0.55f, dotR, weatherIconPaint)
                }
            }
            "frost" -> {
                // Eiskristall-Symbol
                weatherIconPaint.color = Color.parseColor("#80D8FF")
                weatherIconPaint.style = Paint.Style.STROKE
                weatherIconPaint.strokeWidth = size * 0.08f
                weatherIconPaint.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    canvas.drawLine(cx, cy, cx + size * 0.55f * cos, cy + size * 0.55f * sin, weatherIconPaint)
                }
                weatherIconPaint.style = Paint.Style.FILL
            }
            "thunderstorm" -> {
                drawCloudShape(canvas, cx, cy - size * 0.2f, size * 0.8f, Color.parseColor("#666666"))
                // Blitz
                weatherIconPaint.color = Color.parseColor("#FFD600")
                weatherIconPaint.style = Paint.Style.STROKE
                weatherIconPaint.strokeWidth = size * 0.10f
                weatherIconPaint.strokeCap = Paint.Cap.ROUND
                val path = android.graphics.Path()
                path.moveTo(cx + size * 0.05f, cy + size * 0.05f)
                path.lineTo(cx - size * 0.10f, cy + size * 0.35f)
                path.lineTo(cx + size * 0.10f, cy + size * 0.35f)
                path.lineTo(cx - size * 0.05f, cy + size * 0.65f)
                canvas.drawPath(path, weatherIconPaint)
                weatherIconPaint.style = Paint.Style.FILL
            }
            else -> {
                drawCloudShape(canvas, cx, cy, size, Color.parseColor("#999999"))
            }
        }
    }

    /** Zeichnet eine einfache Wolkenform aus überlappenden Kreisen. */
    private fun drawCloudShape(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        weatherIconPaint.color = color
        weatherIconPaint.style = Paint.Style.FILL
        val r = size * 0.22f
        canvas.drawCircle(cx - size * 0.20f, cy, r, weatherIconPaint)
        canvas.drawCircle(cx + size * 0.15f, cy, r, weatherIconPaint)
        canvas.drawCircle(cx - size * 0.05f, cy - r * 0.6f, r * 1.1f, weatherIconPaint)
        canvas.drawCircle(cx + size * 0.05f, cy + r * 0.2f, r * 0.8f, weatherIconPaint)
    }

    // ── Gesundheitsdaten ────────────────────────────────────────────────────────

    /**
     * Zeichnet Gesundheitswerte (Puls, SpO2, Kalorien) unterhalb der Uhrzeit.
     * Positioniert sich am unteren Rand des Zifferblatts.
     */
    // Label, Value, Color, IconType
    private data class HealthItem(val label: String, val value: String, val color: Int, val icon: String)

    /**
     * Liest den aktuellen Zahlenwert einer Komplikation aus dem angegebenen Slot.
     * @param slotIdStr Slot-ID als String ("" = keine Komplikation gewählt)
     * @return Ganzzahliger Wert oder null, wenn nichts gewählt/lesbar ist.
     */
    private fun readComplicationNumber(slotIdStr: String): Int? {
        val id = slotIdStr.toIntOrNull() ?: return null
        val slot = complicationSlotsManager.complicationSlots[id] ?: return null
        val data = slot.complicationData.value
        val now = java.time.Instant.now()
        val raw: CharSequence? = when (data) {
            is RangedValueComplicationData -> return data.value.toInt()
            is ShortTextComplicationData   -> data.text.getTextAt(context.resources, now)
            is LongTextComplicationData    -> data.text.getTextAt(context.resources, now)
            else -> null
        }
        val str = raw?.toString() ?: return null
        val num = Regex("-?\\d+([.,]\\d+)?").find(str)?.value?.replace(',', '.')?.toFloatOrNull() ?: return null
        return num.toInt()
    }

    /**
     * Formatiert einen Health-Wert passend zur Einheit der gewählten Metrik.
     * - "%"    → Prozentanzeige (SpO2)
     * - "kcal" → kompaktes K-Format ab 1000 (z. B. 2360 → 2K4)
     * - "°C"   → Wert kommt in Zehntel-Grad (366 → 36,6°)
     * - sonst  → reine Zahl
     * value <= 0 bedeutet "kein Messwert" → Platzhalter.
     */
    private fun formatHealthValue(value: Int, unit: String): String {
        if (value <= 0) return if (unit == "%") "--%" else "--"
        return when (unit) {
            "%"  -> "$value%"
            "°C" -> "${value / 10},${value % 10}°"
            "kcal" -> if (value < 1000) "$value" else {
                val rounded   = Math.ceil(value / 100.0).toInt() * 100
                val thousands = rounded / 1000
                val hundreds  = (rounded % 1000) / 100
                if (hundreds == 0) "${thousands}K" else "${thousands}K${hundreds}"
            }
            else -> "$value"
        }
    }

    /**
     * Zeichnet die beiden Boden-Komplikationen in die unteren Kreistaschen des Hintergrundbilds.
     * BC1 (links): Puls oder ioBroker-Datenpunkt.
     * BC2 (rechts): wählbare Metrik (kcal/oxygen/bloodpressure/training) oder ioBroker-Datenpunkt.
     * Beide haben einen konfigurierbaren Fortschrittsring (ein-/ausschaltbar, Farben, Min/Max).
     *
     * Positionen (relativ zum Uhrmittelpunkt) passend zu den Kreistaschen im Hintergrundbild:
     *   leftCx  = cx - radius * 0.39, compCy = cy + radius * 0.52
     *   rightCx = cx + radius * 0.39, compCy = cy + radius * 0.52
     */
    private fun drawBottomComplications(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        if (!config.showBottomComp) return

        val compCy     = cy + radius * 0.52f
        val leftCx     = cx - radius * 0.39f
        val rightCx    = cx + radius * 0.39f
        val compRadius = radius * 0.215f

        // ── BC1 (links) – Puls oder ioBroker ──────────────────────────────────
        val bc1NumValue: Float
        val bc1Text: String
        if (config.bc1UseIoBroker) {
            bc1Text     = config.bc1IoValue
            bc1NumValue = config.bc1IoValue.replace(',', '.').toFloatOrNull() ?: 0f
        } else {
            val hr = HealthDataCache.heartRate.takeIf { it > 0 }
                ?: healthSensorManager.heartRate.takeIf { it > 0 }
                ?: config.phoneHeartRate.takeIf {
                    it > 0 && (System.currentTimeMillis() - config.phoneHealthLastReceived) < 1_800_000L
                }
            bc1NumValue = hr?.toFloat() ?: 0f
            bc1Text     = if (hr != null && hr > 0) "$hr" else "--"
        }
        val (bc1Col1, bc1Col2) = applyRingThreshold(
            colorFromId(config.bc1RingColor1), colorFromId(config.bc1RingColor2),
            bc1NumValue, config.bc1RingThreshEnabled, config.bc1RingThreshValue,
            config.bc1RingThreshDir, config.bc1RingThreshTarget, config.bc1RingThreshColor
        )
        drawBottomComp(
            canvas, leftCx, compCy, compRadius,
            label       = config.bc1Label,
            valueText   = bc1Text,
            numValue    = bc1NumValue,
            valueColor  = colorFromId(config.bc1Color),
            ringEnabled = config.bc1RingEnabled,
            ringColor1  = bc1Col1,
            ringColor2  = bc1Col2,
            ringMin     = config.bc1RingMin,
            ringMax     = config.bc1RingMax,
            ringWidth   = config.bc1RingWidth
        )

        // ── BC2 (rechts) – wählbare Metrik oder ioBroker ──────────────────────
        val bc2NumValue: Float
        val bc2Text: String
        if (config.bc2UseIoBroker) {
            bc2Text     = config.bc2IoValue
            bc2NumValue = config.bc2IoValue.replace(',', '.').toFloatOrNull() ?: 0f
        } else {
            val phoneDataFresh = (System.currentTimeMillis() - config.phoneHealthLastReceived) < 1_800_000L
            when (config.bc2Metric) {
                "kcal" -> {
                    val kcal = HealthDataCache.calories.takeIf { it > 0 }
                        ?: config.phoneCalories.takeIf { it > 0 && phoneDataFresh }
                    bc2NumValue = kcal?.toFloat() ?: 0f
                    bc2Text     = kcal?.toString() ?: "--"
                }
                "oxygen" -> {
                    val o2 = HealthDataCache.spO2.takeIf { it > 0 }
                        ?: healthSensorManager.spO2.takeIf { it > 0 }
                        ?: config.phoneSpO2.takeIf { it > 0 && phoneDataFresh }
                    bc2NumValue = o2?.toFloat() ?: 0f
                    bc2Text     = if (o2 != null && o2 > 0) "$o2%" else "--"
                }
                else -> {
                    // "bloodpressure" und "training": kein lokaler Sensor → ioBroker-Quelle nutzen
                    bc2NumValue = 0f
                    bc2Text     = "--"
                }
            }
        }
        val (bc2Col1, bc2Col2) = applyRingThreshold(
            colorFromId(config.bc2RingColor1), colorFromId(config.bc2RingColor2),
            bc2NumValue, config.bc2RingThreshEnabled, config.bc2RingThreshValue,
            config.bc2RingThreshDir, config.bc2RingThreshTarget, config.bc2RingThreshColor
        )
        drawBottomComp(
            canvas, rightCx, compCy, compRadius,
            label       = config.bc2Label,
            valueText   = bc2Text,
            numValue    = bc2NumValue,
            valueColor  = colorFromId(config.bc2Color),
            ringEnabled = config.bc2RingEnabled,
            ringColor1  = bc2Col1,
            ringColor2  = bc2Col2,
            ringMin     = config.bc2RingMin,
            ringMax     = config.bc2RingMax,
            ringWidth   = config.bc2RingWidth
        )
    }

    /**
     * Wendet den Schwellenwert-Farbumschlag auf die beiden Ring-Farben an.
     * Ist der Schwellenwert aktiv und (je nach Richtung) über- bzw. unterschritten,
     * wird die gewählte Zielfarbe (erste oder zweite Verlaufsfarbe) durch threshColor ersetzt.
     * Gibt das ggf. angepasste Farbpaar (color1, color2) zurück.
     */
    private fun applyRingThreshold(
        color1: Int, color2: Int,
        value: Float, enabled: Boolean, threshold: Float,
        dir: String, target: String, threshColorId: String
    ): Pair<Int, Int> {
        if (!enabled) return color1 to color2
        val triggered = if (dir == "below") value <= threshold else value >= threshold
        if (!triggered) return color1 to color2
        val tc = colorFromId(threshColorId)
        return if (target == "color1") tc to color2 else color1 to tc
    }

    /**
     * Zeichnet eine einzelne Boden-Komplikation:
     * 1) Skeuomorphische Vertiefung (3D-Recess) als Hintergrund
     * 2) Optionaler Fortschrittsring (Gradient, von ringMin bis ringMax)
     * 3) Wert-Text zentriert
     * 4) Label-Text darunter (klein, gedimmt)
     */
    private fun drawBottomComp(
        canvas: Canvas,
        compCx: Float, compCy: Float, compRadius: Float,
        label: String,
        valueText: String,
        numValue: Float,
        valueColor: Int,
        ringEnabled: Boolean,
        ringColor1: Int, ringColor2: Int,
        ringMin: Float, ringMax: Float,
        ringWidth: Int
    ) {
        val strokeW = ringWidth * density

        // 3D-Vertiefungs-Kreis hinter dem Ring
        drawEmbossedCircleRecess(canvas, compCx, compCy, compRadius + strokeW * 1.6f)

        val oval = RectF(
            compCx - compRadius, compCy - compRadius,
            compCx + compRadius, compCy + compRadius
        )

        if (ringEnabled) {
            // Hintergrundring (voller Kreis, gedimmt)
            bcRingBgPaint.strokeWidth = strokeW
            canvas.drawArc(oval, -90f, 360f, false, bcRingBgPaint)

            // Füll-Bogen proportional zum Wert zwischen Min und Max
            val fraction = if (ringMax > ringMin) {
                ((numValue - ringMin) / (ringMax - ringMin)).coerceIn(0f, 1f)
            } else 0f

            if (fraction > 0f) {
                val sweepAngle = fraction * 360f
                val shader = SweepGradient(compCx, compCy, intArrayOf(ringColor1, ringColor2), null)
                val matrix = Matrix()
                matrix.postRotate(-90f, compCx, compCy)
                shader.setLocalMatrix(matrix)
                bcRingFgPaint.strokeWidth = strokeW
                bcRingFgPaint.shader = shader
                canvas.drawArc(oval, -90f, sweepAngle, false, bcRingFgPaint)
                bcRingFgPaint.shader = null
            }
        }

        // Wert-Text (z. B. "72" oder "--")
        bcValuePaint.color    = valueColor
        bcValuePaint.textSize = compRadius * 0.68f
        val fm     = bcValuePaint.fontMetrics
        val valueY = compCy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(valueText, compCx, valueY, bcValuePaint)

        // Label (z. B. "BPM" oder "KCAL")
        if (label.isNotBlank()) {
            bcLabelPaint.textSize = compRadius * 0.28f
            val labelY = valueY + bcValuePaint.fontMetrics.descent + compRadius * 0.16f
            canvas.drawText(label.take(6).uppercase(), compCx, labelY, bcLabelPaint)
        }
    }

    private fun drawHealthData(canvas: Canvas, cx: Float, cy: Float, radius: Float, bx: Float = 0f, by: Float = 0f) {
        val config = WatchFaceConfigCache
        // Tipp-Zonen zurücksetzen; werden nur gesetzt wenn das Element gezeichnet wird
        bpmTapBounds.setEmpty()
        kcalTapBounds.setEmpty()
        // Daten gelten als "frisch" wenn in den letzten 30 Minuten empfangen
        val phoneDataFresh = (System.currentTimeMillis() - config.phoneHealthLastReceived) < 1_800_000L
        val items = mutableListOf<HealthItem>()

        // Puls: Komplikation (falls gewählt) > Health Connect > lokaler Sensor
        if (config.showHeartRate) {
            val hr = readComplicationNumber(config.hrComplication)
                ?: if (config.hrSource == "healthconnect") {
                    if (phoneDataFresh) config.phoneHeartRate else 0
                } else {
                    healthSensorManager.heartRate
                }
            val hrText = if (hr > 0) "$hr" else "--"
            items.add(HealthItem("BPM", hrText, colorFromId(config.hrColor), "heart"))
        }

        // Kalorien: Komplikation (falls gewählt) > Phone Health Connect > Watch Health Connect > lokaler Sensor
        if (config.showCalories) {
            val kcal: Int? = readComplicationNumber(config.kcalComplication)
                ?: if (config.kcalSource == "healthconnect") {
                    if (phoneDataFresh) config.phoneCalories.takeIf { it > 0 } else null
                } else {
                    // "local": Watch-eigene HC-Daten (zuverlässiger als Passive Listener auf Mobvoi Atlas)
                    // Passive Listener als Fallback, falls HC keine Daten hat
                    HealthDataCache.calories.takeIf { it > 0 }
                        ?: healthSensorManager.calories.takeIf { it > 0 }
                }
            // Label + Format ergeben sich aus der vom Handy gewählten Metrik.
            val kcalText = if (kcal != null) formatHealthValue(kcal, config.kcalUnit) else "--"
            items.add(HealthItem(config.kcalLabel, kcalText, colorFromId(config.kcalColor), "flame"))
        }

        // SpO2: Komplikation (falls gewählt) > Health Connect > lokaler Sensor
        if (config.showOxygen) {
            val o2 = readComplicationNumber(config.oxygenComplication)
                ?: if (config.oxygenSource == "healthconnect") {
                    if (phoneDataFresh) config.phoneSpO2 else 0
                } else {
                    healthSensorManager.spO2
                }
            val o2Text = formatHealthValue(o2, config.oxygenUnit)
            items.add(HealthItem(config.oxygenLabel, o2Text, colorFromId(config.oxygenColor), "oxygen"))
        }

        if (items.isEmpty()) return

        val hrScale    = config.hrTextScale / 100f
        val kcalScale  = config.kcalTextScale / 100f
        val stepsScale = config.stepsTextScale / 100f

        // Abstände je nach Anzahl aktiver Health-Elemente anpassen:
        // 2 Elemente → weiter auseinander, 3 Elemente → äußere etwas näher zur Mitte
        val itemWidth = when (items.size) {
            2    -> radius * 1.00f
            3    -> radius * 0.72f
            else -> radius * 0.80f
        }
        val totalWidth = items.size * itemWidth
        val startX     = cx - totalWidth / 2f + itemWidth / 2f + bx
        val baseY      = cy + radius * 0.44f + by

        for ((index, item) in items.withIndex()) {
            val scaleFactor = when (item.icon) {
                "heart" -> hrScale
                "flame" -> kcalScale
                "steps" -> stepsScale
                else    -> 1f
            }
            val isSteps   = item.icon == "steps"
            val labelSize = radius * 0.070f * scaleFactor
            val valueSize = radius * 0.130f * scaleFactor
            val iconSize  = radius * 0.065f * scaleFactor

            val dp = context.resources.displayMetrics.density
            val x = startX + index * itemWidth + when (item.icon) {
                "heart" -> -5f * dp
                "flame" -> 5f * dp
                else    -> 0f
            }

            healthLabelPaint.textSize = labelSize
            healthValuePaint.textSize = valueSize

            if (isSteps) {
                // Schritte: Symbol + Zahl inline, versetzt
                val stepsValueSize = radius * 0.105f * scaleFactor
                val stepsOffsetX = 112f * dp
                val stepsOffsetY = 29f * dp

                healthValuePaint.textSize = stepsValueSize
                healthValuePaint.color    = item.color

                val valueWidth = healthValuePaint.measureText(item.value)
                val totalW = iconSize + iconSize * 0.35f + valueWidth
                val leftX = x - totalW / 2f + stepsOffsetX

                drawHealthIcon(canvas, leftX + iconSize / 2f, baseY + stepsOffsetY - iconSize * 0.15f, iconSize, "steps")

                healthValuePaint.textAlign = Paint.Align.LEFT
                canvas.drawText(item.value, leftX + iconSize + iconSize * 0.35f, baseY + stepsOffsetY + stepsValueSize * 0.35f, healthValuePaint)
                healthValuePaint.textAlign = Paint.Align.CENTER
            } else {
                val labelWidth      = healthLabelPaint.measureText(item.label)
                val iconGap         = iconSize * 0.35f
                val totalLabelWidth = iconSize + iconGap + labelWidth
                val labelStartX     = x - totalLabelWidth / 2f

                drawHealthIcon(canvas, labelStartX + iconSize / 2f, baseY - labelSize * 0.30f, iconSize, item.icon,
                    colorOverride = if (item.icon == "heart") item.color else null)

                healthLabelPaint.color     = Color.parseColor("#AAAAAA")
                healthLabelPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(item.label, labelStartX + iconSize + iconGap, baseY, healthLabelPaint)
                healthLabelPaint.textAlign = Paint.Align.CENTER

                healthValuePaint.color = item.color
                canvas.drawText(item.value, x, baseY + valueSize * 1.2f, healthValuePaint)

                // Tipp-Zone für Puls/Kalorien merken (Symbol + Label + Wert umschließen)
                if (item.icon == "heart" || item.icon == "flame") {
                    val valueWidth = healthValuePaint.measureText(item.value)
                    val pad   = radius * 0.04f
                    val left  = minOf(labelStartX, x - valueWidth / 2f) - pad
                    val right = maxOf(labelStartX + totalLabelWidth, x + valueWidth / 2f) + pad
                    val top    = baseY - labelSize - pad
                    val bottom = baseY + valueSize * 1.2f + valueSize * 0.4f + pad
                    if (item.icon == "heart") bpmTapBounds.set(left, top, right, bottom)
                    else kcalTapBounds.set(left, top, right, bottom)
                }
            }
        }
    }

    /**
     * Zeichnet Schritte oberhalb der Uhrzeit (zentriert).
     * Schlafdauer wurde auf Seite 2 verschoben.
     */
    private fun drawStepsAndSleep(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val scale     = config.stepsTextScale / 100f
        val valueSize = radius * 0.130f * scale
        val iconSize  = radius * 0.090f * scale
        val gap       = iconSize * 0.45f
        val rowY      = cy - radius * 0.30f - 5f * density

        // Schritte: aus Komplikation (System-Schrittzähler) oder lokalem Sensor
        val steps = readComplicationNumber("6") ?: healthSensorManager.steps.takeIf { it > 0 }
        val stepsText = steps?.toString() ?: "--"

        healthValuePaint.textSize  = valueSize
        healthValuePaint.textAlign = Paint.Align.LEFT

        // ── Schritte (zentriert, da Schlaf auf Seite 2) ──────────────────
        healthValuePaint.color = colorFromId(config.stepsColor)
        val stepsW    = healthValuePaint.measureText(stepsText)
        val stepsLeft = cx - (iconSize + gap + stepsW) / 2f
        drawHealthIcon(canvas, stepsLeft + iconSize / 2f, rowY - valueSize * 0.30f, iconSize, "steps")
        canvas.drawText(stepsText, stepsLeft + iconSize + gap, rowY, healthValuePaint)

        healthValuePaint.textAlign = Paint.Align.CENTER
    }

    // Wiederverwendbarer Paint + Path für Health-Icons (keine Allocation pro Frame)
    private val healthIconPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val healthIconPath = android.graphics.Path()

    /** Zeichnet ein kleines Icon (Herz, Flamme, O2-Tropfen) für die Gesundheitsanzeige.
     *  colorOverride: optionale Farbe (z. B. für das Herz, das die Puls-Schriftfarbe trägt). */
    private fun drawHealthIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, type: String, colorOverride: Int? = null) {
        healthIconPath.reset()
        when (type) {
            "heart" -> {
                healthIconPaint.color = colorOverride ?: Color.parseColor("#AAAAAA")
                val s = size * 0.55f
                healthIconPath.moveTo(cx, cy + s * 0.6f)
                healthIconPath.cubicTo(cx - s * 1.3f, cy - s * 0.2f, cx - s * 0.6f, cy - s * 1.2f, cx, cy - s * 0.5f)
                healthIconPath.cubicTo(cx + s * 0.6f, cy - s * 1.2f, cx + s * 1.3f, cy - s * 0.2f, cx, cy + s * 0.6f)
                healthIconPath.close()
                canvas.drawPath(healthIconPath, healthIconPaint)
            }
            "flame" -> {
                healthIconPaint.color = Color.parseColor("#FF9800")
                val s = size * 0.55f
                healthIconPath.moveTo(cx, cy + s * 0.7f)
                healthIconPath.cubicTo(cx - s * 0.8f, cy + s * 0.1f, cx - s * 0.5f, cy - s * 0.6f, cx, cy - s * 0.9f)
                healthIconPath.cubicTo(cx + s * 0.5f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.1f, cx, cy + s * 0.7f)
                healthIconPath.close()
                canvas.drawPath(healthIconPath, healthIconPaint)
            }
            "oxygen" -> {
                healthIconPaint.color = Color.parseColor("#AAAAAA")
                val s = size * 0.50f
                healthIconPath.moveTo(cx, cy - s * 0.9f)
                healthIconPath.cubicTo(cx - s * 0.8f, cy + s * 0.1f, cx - s * 0.6f, cy + s * 0.8f, cx, cy + s * 0.8f)
                healthIconPath.cubicTo(cx + s * 0.6f, cy + s * 0.8f, cx + s * 0.8f, cy + s * 0.1f, cx, cy - s * 0.9f)
                healthIconPath.close()
                canvas.drawPath(healthIconPath, healthIconPaint)
            }
            "steps" -> {
                // Zwei diagonale Striche als einfaches Schritt-Symbol
                healthIconPaint.color = Color.parseColor("#AAAAAA")
                healthIconPaint.style = Paint.Style.STROKE
                healthIconPaint.strokeWidth = size * 0.22f
                healthIconPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(cx - size * 0.55f, cy - size * 0.35f, cx - size * 0.10f, cy + size * 0.45f, healthIconPaint)
                canvas.drawLine(cx + size * 0.10f, cy - size * 0.35f, cx + size * 0.55f, cy + size * 0.45f, healthIconPaint)
                healthIconPaint.style = Paint.Style.FILL
            }
            "sleep" -> {
                // Sichelmond (Mondsichel) durch zwei überlappende Kreise + kleine z's
                healthIconPaint.color = Color.parseColor("#AAAAAA")
                healthIconPaint.style = Paint.Style.FILL
                val r = size * 0.62f
                // Voller Mondkreis
                canvas.drawCircle(cx, cy, r, healthIconPaint)
                // Ausschnitt: Hintergrundfarbe ausstanzen → Sichelform
                val prevColor = healthIconPaint.color
                healthIconPaint.color = Color.parseColor("#000000")
                canvas.drawCircle(cx + r * 0.45f, cy - r * 0.25f, r * 0.85f, healthIconPaint)
                healthIconPaint.color = prevColor
                // Kleine "z"-Striche oben rechts
                healthIconPaint.style = Paint.Style.STROKE
                healthIconPaint.strokeWidth = size * 0.10f
                healthIconPaint.strokeCap = Paint.Cap.ROUND
                val zx = cx + r * 0.75f
                val zy = cy - r * 0.85f
                val zs = size * 0.28f
                canvas.drawLine(zx - zs * 0.5f, zy - zs * 0.5f, zx + zs * 0.5f, zy - zs * 0.5f, healthIconPaint)
                canvas.drawLine(zx + zs * 0.5f, zy - zs * 0.5f, zx - zs * 0.5f, zy + zs * 0.5f, healthIconPaint)
                canvas.drawLine(zx - zs * 0.5f, zy + zs * 0.5f, zx + zs * 0.5f, zy + zs * 0.5f, healthIconPaint)
                healthIconPaint.style = Paint.Style.FILL
            }
        }
    }

    // ── Custom ioBroker-Slots ──────────────────────────────────────────────────

    private val customSlotLabelPaint = Paint().apply {
        color = Color.parseColor("#888888")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }
    private val customSlotValuePaint = Paint().apply {
        color = Color.parseColor("#EAFF00")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    /**
     * Zeichnet bis zu 4 ioBroker-Werte-Slots:
     * - Slot 4: horizontaler Balken direkt unter der Uhrzeit (wenn Label gesetzt)
     * - Slots 1/2/3: nebeneinander unterhalb des Balkens (Label grau, Wert Neon-Gelb)
     */
    private fun drawCustomSlots(canvas: Canvas, cx: Float, cy: Float, radius: Float, clockBottomY: Float) {
        val config = WatchFaceConfigCache
        // Slot-Wertfarbe aus Konfiguration anwenden
        customSlotValuePaint.color = colorFromId(config.slotColor)

        val dp7 = 7f * context.resources.displayMetrics.density
        val gap = radius * 0.035f
        var nextY = clockBottomY + dp7

        // ── Slot 4: Balken-Graph ───────────────────────────────────────────
        if (config.customSlot4Label.isNotBlank()) {
            val slot4Scale = config.slot4TextScale / 100f
            val barW     = radius * 0.88f
            val barH     = radius * 0.055f
            val barLeft  = cx - barW / 2f
            val barRight = cx + barW / 2f
            val barCorner = barH / 2f

            val minVal = config.customSlot4BarMin
            val maxVal = config.customSlot4BarMax
            val curVal = config.customSlot4Value.replace(',', '.').toFloatOrNull() ?: minVal
            val fraction = if (maxVal > minVal) ((curVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0f

            // Warnstufen: Balkenfarbe wechselt bei Unterschreiten der Schwelle (absoluter
            // Wert). NaN = deaktiviert; Stufe 2 hat Vorrang.
            var barColor = colorFromPillId(config.customSlot4BarColor)
            if (!config.slot4Warn1Value.isNaN() && curVal <= config.slot4Warn1Value) {
                barColor = colorFromPillId(config.slot4Warn1Color)
            }
            if (!config.slot4Warn2Value.isNaN() && curVal <= config.slot4Warn2Value) {
                barColor = colorFromPillId(config.slot4Warn2Color)
            }

            // Hintergrund
            canvas.drawRoundRect(
                RectF(barLeft, nextY, barRight, nextY + barH),
                barCorner, barCorner, progressBgPaint
            )
            // Füllung bzw. Slider-Knopf
            val fillPaint = Paint().apply {
                color = barColor
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            if (config.customSlot4BarIsSlider) {
                // Slider: gefüllte Strecke (links → Thumb) + Knopf im gleichen Design
                val barCy = nextY + barH / 2f
                val thumbX = (barLeft + barW * fraction).coerceIn(barLeft, barRight)
                if (thumbX > barLeft + barCorner) {
                    canvas.drawRoundRect(
                        RectF(barLeft, nextY, thumbX, nextY + barH),
                        barCorner, barCorner, fillPaint
                    )
                }
                val thumbR = barH * 0.95f
                canvas.drawCircle(thumbX, barCy, thumbR, fillPaint)
                canvas.drawCircle(thumbX, barCy, thumbR * 0.5f, progressBgPaint)
            } else if (fraction > 0f) {
                val minFill = barLeft + barCorner * 2
                canvas.drawRoundRect(
                    RectF(barLeft, nextY, (barLeft + barW * fraction).coerceAtLeast(minFill), nextY + barH),
                    barCorner, barCorner, fillPaint
                )
            }

            // Label + Wert als kleine Überschrift (optional)
            if (config.customSlot4BarShowLabel) {
                val labelSize = radius * 0.072f * slot4Scale
                customSlotLabelPaint.textSize = labelSize
                customSlotLabelPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(config.customSlot4Label.take(3).uppercase(), barLeft, nextY - labelSize * 0.18f, customSlotLabelPaint)
                customSlotValuePaint.textSize = labelSize
                customSlotValuePaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(config.customSlot4Value, barRight, nextY - labelSize * 0.18f, customSlotValuePaint)
            }

            nextY += barH + 3f * context.resources.displayMetrics.density
        }

        // ── Slots 1 / 2 / 3 nebeneinander (dynamisch zentriert) ─────────
        // Abstand zwischen Slot-Mittelpunkten skaliert mit max. Schriftgröße
        val maxSlotScale = listOfNotNull(
            if (config.customSlot1Label.isNotBlank()) config.slot1TextScale / 100f else null,
            if (config.customSlot2Label.isNotBlank()) config.slot2TextScale / 100f else null,
            if (config.customSlot3Label.isNotBlank()) config.slot3TextScale / 100f else null
        ).maxOrNull() ?: 1f
        val slotSpacing = radius * 0.33f * maxSlotScale

        fun drawSlot(label: String, value: String, slotCx: Float, slotScale: Float) {
            val fontSize = radius * 0.10f * slotScale
            customSlotLabelPaint.textSize = fontSize
            customSlotValuePaint.textSize = fontSize
            val fm = customSlotValuePaint.fontMetrics
            val slotY = nextY - fm.ascent
            val labelText = label.take(3).uppercase()
            // Abstand skaliert mit Schriftgröße, damit Beschriftung bei größerer Schrift mitläuft
            val scaledGap = fontSize * 0.55f
            customSlotLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labelText, slotCx - scaledGap / 2f, slotY, customSlotLabelPaint)
            customSlotValuePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, slotCx + scaledGap / 2f, slotY, customSlotValuePaint)
        }

        // Nur aktive Slots sammeln
        data class SlotData(val label: String, val value: String, val scale: Float)
        val activeSlots = mutableListOf<SlotData>()
        if (config.customSlot1Label.isNotBlank()) activeSlots.add(SlotData(config.customSlot1Label, config.customSlot1Value, config.slot1TextScale / 100f))
        if (config.customSlot2Label.isNotBlank()) activeSlots.add(SlotData(config.customSlot2Label, config.customSlot2Value, config.slot2TextScale / 100f))
        if (config.customSlot3Label.isNotBlank()) activeSlots.add(SlotData(config.customSlot3Label, config.customSlot3Value, config.slot3TextScale / 100f))

        // Dynamisch zentrieren: egal welche/wieviele Slots aktiv sind
        val count = activeSlots.size
        if (count > 0) {
            val totalWidth = (count - 1) * slotSpacing
            val startX = cx - totalWidth / 2f
            for ((i, slot) in activeSlots.withIndex()) {
                drawSlot(slot.label, slot.value, startX + i * slotSpacing, slot.scale)
            }
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private fun colorFromId(id: String): Int = when (id) {
        "white"       -> Color.WHITE
        "neon_yellow" -> Color.parseColor("#EAFF00")
        "cyan"        -> Color.parseColor("#00BCD4")
        "red"         -> Color.parseColor("#F44336")
        "orange"      -> Color.parseColor("#FF9800")
        "purple"      -> Color.parseColor("#9C27B0")
        "green"       -> Color.parseColor("#4CAF50")
        else          -> Color.parseColor("#E8E8E8")
    }

    private fun dimColor(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8)  and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color          and 0xFF) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // ── 3D Vertiefungs-Helfer ─────────────────────────────────────────────────

    /**
     * Zeichnet eine skeuomorphische Vertiefung (eingestanzter Panel-Look).
     * Schichten: 1) Dunkle Basis  2) Innenschatten-Strokes  3) Heller Rand-Reflex
     * Alle Paints sind Klassen-Member → keine Allokation pro Frame.
     */
    private fun drawEmbossedRecess(canvas: Canvas, rect: RectF, cornerRadius: Float) {
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, recessFillPaint)
        for ((i, alpha) in recessShadowAlphas.withIndex()) {
            val inset = i * 3.2f
            val cr = (cornerRadius - inset).coerceAtLeast(2f)
            recessInnerShadowPaint.color = Color.argb(alpha, 0, 0, 0)
            recessInnerShadowPaint.strokeWidth = 5f
            recessTempRect.set(
                rect.left + inset, rect.top + inset,
                rect.right - inset, rect.bottom - inset
            )
            canvas.drawRoundRect(recessTempRect, cr, cr, recessInnerShadowPaint)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, recessRimPaint)
    }

    /**
     * Zeichnet einen Vertiefungs-Kreis (quadratische RectF → vollständiger Radius als Eckenradius).
     */
    private fun drawEmbossedCircleRecess(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        recessTempRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawEmbossedRecess(canvas, recessTempRect, radius)
    }

    /**
     * Zeichnet einen Sekundenring am äußeren Rand des Zifferblatts.
     * Jede Sekunde füllt 6° (360° / 60 s), Startpunkt ist 12 Uhr (−90°).
     * Ringbreite und -farbe werden aus WatchFaceConfigCache gelesen.
     *
     * Glow-Effekt: Im gefüllten Bereich strahlt ein nach innen wachsender Schein
     * in der Ringfarbe — er wächst mit den Sekunden mit und endet exakt am selben
     * Winkel wie der sichtbare Bogen.
     */
    private fun drawSecondsRing(canvas: Canvas, bounds: Rect, seconds: Int) {
        val config = WatchFaceConfigCache
        val ringWidthPx = config.secondsRingWidth * density
        val inset = ringWidthPx / 2f
        val oval = RectF(
            bounds.left  + inset,
            bounds.top   + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )

        // Hintergrund-Track (voller Kreis, sehr transparent)
        secondsRingBgPaint.strokeWidth = ringWidthPx
        canvas.drawArc(oval, -90f, 360f, false, secondsRingBgPaint)

        val sweepAngle = seconds / 60f * 360f
        if (sweepAngle > 0f) {
            val ringColor = colorFromId(config.secondsRingColorId)
            val r = Color.red(ringColor)
            val g = Color.green(ringColor)
            val b = Color.blue(ringColor)

            // ── Glow-Schein nach innen ─────────────────────────────────────
            // Schein startet am inneren Rand des Rings und strahlt ~18 % des
            // Radius nach innen. Jede Schicht wird mit BUTT-Cap gezeichnet
            // damit der Schein exakt am selben Winkel endet wie der Ring.
            // glowFactor 0.0 = kein Schein, 1.0 = maximale Breite
            val glowFactor = config.secondsGlowWidth / 100f
            val glowSteps = 10
            val glowDepth = minOf(bounds.width(), bounds.height()) * 0.09f * glowFactor
            val layerWidth = (glowDepth / glowSteps) * 2.2f
            secondsGlowPaint.strokeWidth = layerWidth

            if (glowFactor > 0f) for (step in 0 until glowSteps) {
                val t = step.toFloat() / glowSteps   // 0 = dicht am Ring, 1 = weiter innen
                // Quadratische Abschwächung: hell am Ring, schnell transparent nach innen
                val alpha = ((1f - t) * (1f - t) * 140).toInt()
                if (alpha < 3) continue

                // Inset: innerer Rand des Rings + schrittweise nach innen
                val glowInset = ringWidthPx + glowDepth * t
                val glowOval = RectF(
                    bounds.left  + glowInset,
                    bounds.top   + glowInset,
                    bounds.right - glowInset,
                    bounds.bottom - glowInset
                )
                secondsGlowPaint.color = Color.argb(alpha, r, g, b)
                canvas.drawArc(glowOval, -90f, sweepAngle, false, secondsGlowPaint)
            }

            // ── Haupt-Arc (auf dem Glow, damit er scharf bleibt) ──────────
            secondsRingFgPaint.color = ringColor
            secondsRingFgPaint.strokeWidth = ringWidthPx
            canvas.drawArc(oval, -90f, sweepAngle, false, secondsRingFgPaint)
        }
    }

    private fun drawTickMarks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val tickRadius = radius * 0.92f
        for (i in 0 until 60) {
            val angle       = Math.toRadians((i * 6 - 90).toDouble())
            val cos         = Math.cos(angle).toFloat()
            val sin         = Math.sin(angle).toFloat()
            val isHour      = i % 5 == 0
            val innerRadius = if (isHour) tickRadius - radius * 0.10f else tickRadius - radius * 0.05f
            val paint       = if (isHour) accentTickPaint else tickPaint
            canvas.drawLine(
                cx + innerRadius * cos, cy + innerRadius * sin,
                cx + tickRadius  * cos, cy + tickRadius  * sin,
                paint
            )
        }
    }

    private fun drawComplications(
        canvas: Canvas,
        zonedDateTime: ZonedDateTime,
        isAmbient: Boolean
    ) {
        // Sunrise/Sunset-Komplikation (Slot 2 = links): Schriftgröße + Farbe aus Config anwenden
        val sunriseScale = WatchFaceConfigCache.sunriseTextScale / 100f
        val sunriseArgb  = colorFromId(WatchFaceConfigCache.sunriseColor)
        complicationSlotsManager.complicationSlots[2]?.let { slot ->
            (slot.renderer as? CanvasComplicationDrawable)?.drawable?.activeStyle?.apply {
                textSize     = (30f * sunriseScale).toInt().coerceAtLeast(8)
                titleSize    = (22f * sunriseScale).toInt().coerceAtLeast(6)
                textColor = sunriseArgb
            }
        }
        // Slot 6 (Schritte) wird nicht mehr nativ gerendert – die Schritte werden
        // zusammen mit der Schlafdauer eigens gezeichnet (drawStepsAndSleep). Der Slot
        // bleibt aktiv, damit der System-Schrittzähler weiter Daten liefert.
        complicationSlotsManager.complicationSlots.forEach { (id, slot) ->
            if (id == 6) return@forEach
            if (slot.enabled) slot.render(canvas, zonedDateTime, renderParameters)
        }
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        complicationSlotsManager.complicationSlots.forEach { (_, slot) ->
            if (slot.enabled) slot.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
        }
    }

    override fun onTapEvent(
        tapType: Int,
        tapEvent: TapEvent,
        complicationSlot: androidx.wear.watchface.ComplicationSlot?
    ) {
        val x = tapEvent.xPos.toFloat()
        val y = tapEvent.yPos.toFloat()
        val config = WatchFaceConfigCache

        // ── 9-Uhr Doppeltipp: zwischen Seite 1 und 2 wechseln ───────────────
        if (nineOClockTapBounds.contains(x, y)) {
            if (tapType == TapType.UP) {
                val now = System.currentTimeMillis()
                if (now - page2LastTapTime <= DOUBLE_TAP_MS) {
                    currentPage = 1 - currentPage
                    page2LastTapTime = 0L
                    // Beim Wechsel auf Seite 2 die aktuellsten Data-Layer-Werte
                    // nachladen, damit die 4 Werte sofort frisch sind – auch wenn
                    // zwischenzeitliche onDataChanged-Updates verpasst wurden.
                    if (currentPage == 1) loadInitialConfig()
                    invalidate()
                } else {
                    page2LastTapTime = now
                }
            }
            return
        }

        // ── Seite 2: Slider – Tippen springt zur getippten Position ─────────
        // Bewusst kein Ziehen (Drag), da das die Schnelleinstellungen / die
        // Benachrichtigungsleiste der Uhr herunterziehen würde. Stattdessen:
        // an die getippte Höhe springen. Reagiert auf DOWN (sofortiges Springen
        // schon beim Berühren) – ein nachfolgendes UP/CANCEL wird ignoriert,
        // damit der Wert nicht doppelt gesendet wird.
        if (currentPage == 1 && config.p2BarIsSlider && !page2SliderTapBounds.isEmpty) {
            if (page2SliderTapBounds.contains(x, y)) {
                if (tapType == TapType.DOWN) {
                    val frac = ((page2SliderBounds.bottom - y) /
                        (page2SliderBounds.bottom - page2SliderBounds.top)).coerceIn(0f, 1f)
                    val value = Math.round(config.p2BarMin + frac * (config.p2BarMax - config.p2BarMin))
                    // Optimistisch lokal aktualisieren für sofortiges Feedback
                    config.p2BarValue = value.toString()
                    invalidate()
                    sendSliderValue(value)
                }
                return
            }
        }

        // ── Seite 2: Pillen (Doppeltipp wie Seite-1-Pille) ──────────────────
        if (currentPage == 1) {
            // Visuelles Feedback: beim Berühren in der gewählten Farbe aufleuchten
            if (tapType == TapType.DOWN) {
                if (!page2Pill1TapBounds.isEmpty && page2Pill1TapBounds.contains(x, y)) {
                    page2Pill1Pressed = true
                    page2Pill1PressedAt = System.currentTimeMillis()
                    invalidate()
                    return
                } else if (!page2Pill2TapBounds.isEmpty && page2Pill2TapBounds.contains(x, y)) {
                    page2Pill2Pressed = true
                    page2Pill2PressedAt = System.currentTimeMillis()
                    invalidate()
                    return
                }
            }
            if (tapType == TapType.UP) {
                page2Pill1Pressed = false
                page2Pill2Pressed = false
                val now = System.currentTimeMillis()
                if (!page2Pill1TapBounds.isEmpty && page2Pill1TapBounds.contains(x, y)) {
                    if (now - page2Pill1LastTapTime <= DOUBLE_TAP_MS) {
                        page2Pill1LastTapTime = 0L
                        triggerP2PillAction(1)
                    } else {
                        page2Pill1LastTapTime = now
                        page2Pill2LastTapTime = 0L
                    }
                } else if (!page2Pill2TapBounds.isEmpty && page2Pill2TapBounds.contains(x, y)) {
                    if (now - page2Pill2LastTapTime <= DOUBLE_TAP_MS) {
                        page2Pill2LastTapTime = 0L
                        triggerP2PillAction(2)
                    } else {
                        page2Pill2LastTapTime = now
                        page2Pill1LastTapTime = 0L
                    }
                } else {
                    page2Pill1LastTapTime = 0L
                    page2Pill2LastTapTime = 0L
                }
                invalidate()
            }
            return
        }

        // ── Wetter-Anzeige: tippen öffnet die Wetter-App ─────────────────────
        if (tapType == TapType.UP && config.showWeather && weatherTapBounds.contains(x, y)) {
            openWeatherApp()
            return
        }

        // ── Puls: tippen öffnet die Health-/Puls-App ─────────────────────────
        if (tapType == TapType.UP && !bpmTapBounds.isEmpty && bpmTapBounds.contains(x, y)) {
            openHeartApp()
            return
        }

        // ── Kalorien: tippen öffnet die Fitness-App ──────────────────────────
        if (tapType == TapType.UP && !kcalTapBounds.isEmpty && kcalTapBounds.contains(x, y)) {
            openCaloriesApp()
            return
        }

        // ── Tages-Sekunden: tippen öffnet die Kalender-App ───────────────────
        if (tapType == TapType.UP && !daySecondsTapBounds.isEmpty && daySecondsTapBounds.contains(x, y)) {
            openCalendarApp()
            return
        }

        // ── Stunden/Minuten: Doppeltipp = Stoppuhr-Modus, Einzeltipp = Start/Stop
        if (tapType == TapType.UP && !timeTapBounds.isEmpty && timeTapBounds.contains(x, y)) {
            handleTimeZoneTap()
            return
        }

        // ── Aktions-Pille ────────────────────────────────────────────────────
        if (!config.actionPillEnabled) return

        if (tapType == TapType.DOWN && pillTapBounds.contains(x, y)) {
            // Visuelles Feedback: Pille leuchtet in Aktiv-Farbe auf
            pillPressed = true
            pillPressedAt = System.currentTimeMillis()
            invalidate()
            return
        }

        if (tapType != TapType.UP) return
        if (!pillTapBounds.contains(x, y)) {
            pillPressed = false
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTapTime <= DOUBLE_TAP_MS) {
            lastTapTime = 0L
            triggerPillAction()
        } else {
            lastTapTime = now
        }
        pillPressed = false
    }

    /**
     * Öffnet die auf der Uhr installierte Wetter-App.
     * Probiert zuerst bekannte Wetter-Pakete, sonst die erste startbare App,
     * deren Paketname "weather" enthält.
     */
    private fun openWeatherApp() {
        val pm = context.packageManager

        fun launch(pkg: String): Boolean {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG_PILL, "Wetter-App-Start fehlgeschlagen ($pkg): ${e.message}")
                false
            }
        }

        val knownPackages = listOf(
            "com.mobvoi.companion.weather",
            "com.mobvoi.wear.weather",
            "com.google.android.apps.weather",
            "com.google.android.gms.weather"
        )
        for (pkg in knownPackages) {
            if (launch(pkg)) return
        }

        // Fallback: erste startbare App mit "weather" im Paketnamen
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidate = pm.queryIntentActivities(mainIntent, 0)
            .firstOrNull { it.activityInfo.packageName.contains("weather", ignoreCase = true) }
        if (candidate != null) {
            launch(candidate.activityInfo.packageName)
        } else {
            Log.w(TAG_PILL, "Keine Wetter-App auf der Uhr gefunden")
        }
    }

    /**
     * Versucht eine App zu starten: zuerst über bekannte Paketnamen, dann optional
     * über eine Kategorie-Intent (z.B. CATEGORY_APP_CALENDAR), zuletzt über die erste
     * startbare App, deren Paketname einen der Hinweise enthält.
     */
    private fun launchAppByHints(
        knownPackages: List<String>,
        nameHints: List<String>,
        categoryIntent: Intent? = null
    ) {
        val pm = context.packageManager

        fun launch(pkg: String): Boolean {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG_PILL, "App-Start fehlgeschlagen ($pkg): ${e.message}")
                false
            }
        }

        for (pkg in knownPackages) {
            if (launch(pkg)) return
        }

        if (categoryIntent != null) {
            categoryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(categoryIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG_PILL, "Kategorie-Intent fehlgeschlagen: ${e.message}")
            }
        }

        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidate = pm.queryIntentActivities(mainIntent, 0)
            .firstOrNull { ri -> nameHints.any { ri.activityInfo.packageName.contains(it, ignoreCase = true) } }
        if (candidate != null) {
            launch(candidate.activityInfo.packageName)
        } else {
            Log.w(TAG_PILL, "Keine passende App gefunden (Hinweise: $nameHints)")
        }
    }

    /** Öffnet die Puls-/Health-App auf der Uhr (Mobvoi TicHealth/TicCare, sonst Fitness). */
    private fun openHeartApp() = launchAppByHints(
        knownPackages = listOf(
            "com.mobvoi.wear.health", "com.mobvoi.ticwear.health", "com.mobvoi.health",
            "com.google.android.apps.fitness", "com.google.android.wearable.fitness"
        ),
        nameHints = listOf("heart", "puls", "tichealth", "ticcare", "health")
    )

    /** Öffnet die Kalorien-/Fitness-App auf der Uhr. */
    private fun openCaloriesApp() = launchAppByHints(
        knownPackages = listOf(
            "com.google.android.apps.fitness", "com.google.android.wearable.fitness",
            "com.mobvoi.wear.health", "com.mobvoi.ticwear.health"
        ),
        nameHints = listOf("fitness", "workout", "exercise", "tichealth", "ticcare", "health")
    )

    /** Öffnet die Kalender-App auf der Uhr. */
    private fun openCalendarApp() = launchAppByHints(
        knownPackages = listOf(
            "com.google.android.calendar", "com.google.android.wearable.calendar"
        ),
        nameHints = listOf("calendar", "kalender"),
        categoryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
    )

    // ── Stoppuhr-Logik ───────────────────────────────────────────────────────────

    /** Aktuell verstrichene Stoppuhr-Zeit in Millisekunden. */
    // ── NTP-Zeitkorrektur ────────────────────────────────────────────────────
    @Volatile private var ntpQueryRunning = false

    /**
     * Ermittelt den Zeit-Offset gegenüber der Systemzeit per NTP und legt ihn im
     * [WatchFaceConfigCache] ab. Läuft auf einem IO-Thread, schlägt still fehl
     * (z. B. ohne Netzwerk) und behält dann den letzten bekannten Offset bei.
     */
    private fun refreshNtpOffset() {
        if (!WatchFaceConfigCache.ntpEnabled) {
            WatchFaceConfigCache.ntpOffsetMs = 0L
            return
        }
        if (ntpQueryRunning) return
        ntpQueryRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                val server = WatchFaceConfigCache.ntpServer.ifBlank { "pool.ntp.org" }
                val offset = queryNtpOffset(server)
                if (offset != null && WatchFaceConfigCache.ntpEnabled) {
                    // Mit dem alten Offset vergleichen: ist er identisch, wird nichts
                    // weiter getan – die Zeit läuft einfach mit dem bestehenden Offset
                    // weiter (kein Persistieren, kein Neuzeichnen nötig).
                    if (offset != WatchFaceConfigCache.ntpOffsetMs) {
                        WatchFaceConfigCache.ntpOffsetMs = offset
                        // Offset dauerhaft sichern, damit er einen Neustart übersteht.
                        ntpPrefs.edit().putLong(NTP_PREFS_KEY_OFFSET, offset).apply()
                        // Offset an die App zurücksenden (zur Anzeige in den Einstellungen)
                        sendNtpOffsetToPhone(offset, server)
                        invalidate()
                    }
                    Log.d(TAG_PILL, "NTP-Offset ($server): $offset ms")
                }
            } catch (e: Exception) {
                Log.w(TAG_PILL, "NTP-Abfrage fehlgeschlagen: ${e.message}")
            } finally {
                ntpQueryRunning = false
            }
        }
    }

    /**
     * Stellt den zuletzt persistierten NTP-Offset wieder her, sofern die Korrektur
     * aktiv ist und im flüchtigen Cache noch kein Offset steht (z. B. direkt nach
     * einem Watchface-Neustart). So gilt beim Aufwachen sofort die korrigierte Zeit.
     */
    private fun restoreNtpOffsetFromPrefs() {
        if (WatchFaceConfigCache.ntpEnabled && WatchFaceConfigCache.ntpOffsetMs == 0L) {
            val stored = ntpPrefs.getLong(NTP_PREFS_KEY_OFFSET, 0L)
            if (stored != 0L) {
                WatchFaceConfigCache.ntpOffsetMs = stored
                Log.d(TAG_PILL, "NTP-Offset aus Speicher wiederhergestellt: $stored ms")
            }
        }
    }

    /**
     * Minimaler SNTP-Client (RFC 4330). Sendet eine UDP-Anfrage an Port 123 und
     * berechnet den Offset über die halbe Round-Trip-Zeit:
     *   Offset = ((T2 - T1) + (T3 - T4)) / 2
     * mit T1/T4 = lokale Sende-/Empfangszeit, T2/T3 = Server-Empfangs-/Sendezeit.
     * Gibt den Offset in Millisekunden zurück oder null bei Fehler.
     */
    private fun queryNtpOffset(host: String): Long? {
        return try {
            val address = java.net.InetAddress.getByName(host)
            java.net.DatagramSocket().use { socket ->
                socket.soTimeout = NTP_TIMEOUT_MS
                val buf = ByteArray(48)
                buf[0] = 0x1B  // LI = 0, Version = 3, Mode = 3 (Client)
                val t1 = System.currentTimeMillis()
                writeNtpTimestamp(buf, 40, t1)  // Transmit Timestamp (Originate beim Server)
                socket.send(java.net.DatagramPacket(buf, buf.size, address, 123))
                val response = java.net.DatagramPacket(buf, buf.size)
                socket.receive(response)
                val t4 = System.currentTimeMillis()
                val t2 = readNtpTimestamp(buf, 32)  // Receive Timestamp
                val t3 = readNtpTimestamp(buf, 40)  // Transmit Timestamp
                ((t2 - t1) + (t3 - t4)) / 2
            }
        } catch (e: Exception) {
            Log.w(TAG_PILL, "NTP-Abfrage ($host) fehlgeschlagen: ${e.message}")
            null
        }
    }

    /** Liest einen 64-Bit-NTP-Zeitstempel (Sekunden seit 1900) als Millis seit 1970. */
    private fun readNtpTimestamp(buf: ByteArray, offset: Int): Long {
        var seconds = 0L
        var fraction = 0L
        for (i in 0 until 4) seconds  = (seconds  shl 8) or (buf[offset + i].toLong() and 0xff)
        for (i in 0 until 4) fraction = (fraction shl 8) or (buf[offset + 4 + i].toLong() and 0xff)
        return (seconds - NTP_EPOCH_OFFSET_SEC) * 1000L + (fraction * 1000L) / 0x100000000L
    }

    /** Schreibt Millis seit 1970 als 64-Bit-NTP-Zeitstempel (big-endian). */
    private fun writeNtpTimestamp(buf: ByteArray, offset: Int, millis: Long) {
        val seconds  = millis / 1000L + NTP_EPOCH_OFFSET_SEC
        val fraction = ((millis % 1000L) * 0x100000000L) / 1000L
        for (i in 0 until 4) buf[offset + i]     = ((seconds  shr (24 - i * 8)) and 0xff).toByte()
        for (i in 0 until 4) buf[offset + 4 + i] = ((fraction shr (24 - i * 8)) and 0xff).toByte()
    }

    private fun stopwatchElapsedMs(): Long = when (stopwatchMode) {
        StopwatchMode.RUNNING -> stopwatchAccumMs + (android.os.SystemClock.elapsedRealtime() - stopwatchStartRt)
        StopwatchMode.STOPPED -> stopwatchAccumMs
        else -> 0L
    }

    /** Doppeltipp auf Stunden/Minuten: Stoppuhr-Modus betreten (Reset auf 0) bzw. verlassen. */
    private fun onStopwatchDoubleTap() {
        if (stopwatchMode == StopwatchMode.OFF) {
            stopwatchAccumMs = 0L
            stopwatchStartRt = 0L
            stopwatchMode = StopwatchMode.READY
        } else {
            stopwatchMode = StopwatchMode.OFF
        }
        invalidate()
    }

    /** Einzeltipp auf Stunden/Minuten im Stoppuhr-Modus: starten / stoppen / fortsetzen. */
    private fun onStopwatchSingleTap() {
        when (stopwatchMode) {
            StopwatchMode.READY -> {
                stopwatchAccumMs = 0L
                stopwatchStartRt = android.os.SystemClock.elapsedRealtime()
                stopwatchMode = StopwatchMode.RUNNING
            }
            StopwatchMode.RUNNING -> {
                stopwatchAccumMs += android.os.SystemClock.elapsedRealtime() - stopwatchStartRt
                stopwatchMode = StopwatchMode.STOPPED
            }
            StopwatchMode.STOPPED -> {
                stopwatchStartRt = android.os.SystemClock.elapsedRealtime()
                stopwatchMode = StopwatchMode.RUNNING
            }
            StopwatchMode.OFF -> {}
        }
        invalidate()
    }

    /**
     * Verarbeitet einen Tipp im Stunden/Minuten-Bereich und unterscheidet Einzel- von
     * Doppeltipp. Ein Einzeltipp-Effekt wird um DOUBLE_TAP_MS verzögert, damit ein
     * folgender Tipp ihn noch zum Doppeltipp zusammenfassen kann.
     */
    private fun handleTimeZoneTap() {
        val now = System.currentTimeMillis()
        if (now - lastTimeTapTime <= DOUBLE_TAP_MS) {
            lastTimeTapTime = 0L
            pendingTimeTapJob?.cancel()
            pendingTimeTapJob = null
            onStopwatchDoubleTap()
        } else {
            lastTimeTapTime = now
            pendingTimeTapJob?.cancel()
            pendingTimeTapJob = scope.launch {
                delay(DOUBLE_TAP_MS)
                onStopwatchSingleTap()
                lastTimeTapTime = 0L
            }
        }
    }

    /** Hauptanzeige der Stoppuhr: "MM:SS" (Stunden-Slot = Minuten, Minuten-Slot = Sekunden). */
    private fun formatStopwatchMain(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1000
        val minutes  = (totalSec / 60) % 100
        val seconds  = totalSec % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** Sekunden-Slot der Stoppuhr: Hundertstelsekunden (Millisekunden-Darstellung). */
    private fun formatStopwatchCenti(elapsedMs: Long): String =
        "%02d".format((elapsedMs % 1000) / 10)

    /** Zeichnet den Sekundenring im Stoppuhr-Modus (Cyan, 360° = 1 Sekunde). */
    private fun drawStopwatchRing(canvas: Canvas, bounds: Rect, elapsedMs: Long) {
        val config = WatchFaceConfigCache
        val ringWidthPx = config.secondsRingWidth * density
        val inset = ringWidthPx / 2f
        val oval = RectF(
            bounds.left  + inset,
            bounds.top   + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        stopwatchRingBgPaint.strokeWidth = ringWidthPx
        canvas.drawArc(oval, -90f, 360f, false, stopwatchRingBgPaint)

        // 360° pro Sekunde → Bruchteil der laufenden Sekunde
        val sweepAngle = (elapsedMs % 1000) / 1000f * 360f
        if (sweepAngle > 0f) {
            stopwatchRingFgPaint.strokeWidth = ringWidthPx
            canvas.drawArc(oval, -90f, sweepAngle, false, stopwatchRingFgPaint)
        }
    }

    // ── Seite 2 ────────────────────────────────────────────────────────────────

    /**
     * Zeichnet die gesamte zweite Watchface-Seite.
     * Aufruf durch render() wenn currentPage == 1 und nicht Ambient.
     * Enthält:
     *   - Touch-Overlay (transparent → stark transparentes Weiß beim Berühren)
     *   - 4 ioBroker-Slots (2×2 Gitter, obere Hälfte)
     *   - 2 halbe Aktions-Pillen bei 7 Uhr und 5 Uhr
     */
    private fun drawPage2(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Schlafdauer (oben, über den Slots)
        drawPage2Sleep(canvas, cx, cy, radius)

        // 4 ioBroker-Slots (Mitte, 2 × 2 Gitter)
        drawPage2IoBrokerSlots(canvas, cx, cy, radius)

        // Vertikaler Balken (rechts, wenn Label gesetzt)
        if (WatchFaceConfigCache.p2BarLabel.isNotBlank()) {
            drawPage2VerticalBar(canvas, cx, cy, radius)
        } else {
            page2SliderBounds.setEmpty()
            page2SliderTapBounds.setEmpty()
        }

        // 2 halbe Pillen (7 Uhr und 5 Uhr)
        drawPage2Pills(canvas, cx, cy, radius)
    }

    /**
     * Zeichnet 4 ioBroker-Slots auf Seite 2 in einem 2×2 Gitter.
     * Obere Hälfte des Zifferblatts, über den Pillen.
     */
    private fun drawPage2IoBrokerSlots(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val baseLabelSize = radius * 0.072f
        val baseValueSize = radius * 0.100f
        val colOffset = radius * 0.30f
        // Alle 4 Slots etwas nach links und oben verschieben
        val xShift = radius * 0.06f
        val yShift = radius * 0.06f

        // Zeile 1: oberer Bereich; Zeile 2: knapp unterhalb der Mitte
        val row1Y = cy - radius * 0.20f - yShift
        val row2Y = cy + radius * 0.22f - yShift

        data class P2Slot(val label: String, val value: String, val slotCx: Float, val baseY: Float, val textScale: Float)
        val slots = listOf(
            P2Slot(config.p2Slot1Label, config.p2Slot1Value, cx - colOffset - xShift, row1Y, config.p2Slot1TextScale / 100f),
            P2Slot(config.p2Slot2Label, config.p2Slot2Value, cx + colOffset - xShift, row1Y, config.p2Slot2TextScale / 100f),
            P2Slot(config.p2Slot3Label, config.p2Slot3Value, cx - colOffset - xShift, row2Y, config.p2Slot3TextScale / 100f),
            P2Slot(config.p2Slot4Label, config.p2Slot4Value, cx + colOffset - xShift, row2Y, config.p2Slot4TextScale / 100f)
        )

        for (slot in slots) {
            val labelSize = baseLabelSize * slot.textScale
            val valueSize = baseValueSize * slot.textScale

            // Label (grau, klein)
            val labelText = if (slot.label.isBlank()) "---" else slot.label.take(6).uppercase()
            page2SlotLabelPaint.textSize = labelSize
            canvas.drawText(labelText, slot.slotCx, slot.baseY, page2SlotLabelPaint)

            // Wert (Neon-Gelb, größer)
            page2SlotValuePaint.textSize = valueSize
            canvas.drawText(slot.value, slot.slotCx, slot.baseY + valueSize * 1.15f, page2SlotValuePaint)
        }
    }

    /**
     * Zeichnet zwei halbe Aktions-Pillen auf Seite 2:
     *   - Pille bei 7 Uhr (unten links, ca. 210° von 12 Uhr)
     *   - Pille bei 5 Uhr (unten rechts, ca. 150° von 12 Uhr)
     * Halbbreite = 50 % der Seite-1-Pille; gleiche Höhe.
     * Farbe aus p2PillColorTrue/False (unabhängig von Seite-1-Pille konfigurierbar).
     */
    private fun drawPage2Pills(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config  = WatchFaceConfigCache
        val halfW   = radius * 0.200f
        val halfH   = radius * 0.095f
        val tapPad  = halfH * 1.5f

        val dist   = radius * 0.69f
        val pill7X = cx - dist * 0.50f
        val pill7Y = cy + dist * 0.866f
        val pill5X = cx + dist * 0.50f
        val pill5Y = cy + dist * 0.866f

        val pillTextSize = halfH * 1.1f
        val now = System.currentTimeMillis()

        // Pille 1 (7 Uhr) – unabhängige Konfiguration
        if (config.p2PillEnabled) {
            val c1True  = if (config.p2PillColorTrue.isNotBlank())  config.p2PillColorTrue  else "cyan"
            val c1False = if (config.p2PillColorFalse.isNotBlank()) config.p2PillColorFalse else "red"
            // Beim Antippen kurz in der gewählten Farbe (True-Farbe) aufleuchten
            val pressed1 = page2Pill1Pressed && (now - page2Pill1PressedAt < PILL_PRESS_DURATION_MS)
            val sc1 = colorFromPillId(if (pressed1 || config.p2Pill1State) c1True else c1False)
            page2PillFillPaint.color   = Color.argb(if (pressed1) 255 else 180, Color.red(sc1), Color.green(sc1), Color.blue(sc1))
            page2PillStrokePaint.color = sc1
            page2Pill1Bounds.set(pill7X - halfW, pill7Y - halfH, pill7X + halfW, pill7Y + halfH)
            page2Pill1TapBounds.set(pill7X - halfW - tapPad, pill7Y - halfH - tapPad, pill7X + halfW + tapPad, pill7Y + halfH + tapPad)
            canvas.drawRoundRect(page2Pill1Bounds, halfH, halfH, page2PillFillPaint)
            canvas.drawRoundRect(page2Pill1Bounds, halfH, halfH, page2PillStrokePaint)
            pillTextPaint.color    = Color.WHITE
            pillTextPaint.textSize = pillTextSize
            val fm1 = pillTextPaint.fontMetrics
            canvas.drawText(if (config.p2Pill1State) "AN" else "AUS", pill7X, pill7Y - (fm1.ascent + fm1.descent) / 2f, pillTextPaint)
        } else {
            page2Pill1TapBounds.setEmpty()
        }

        // Pille 2 (5 Uhr) – unabhängige Konfiguration
        if (config.p2Pill2Enabled) {
            val c2True  = if (config.p2Pill2ColorTrue.isNotBlank())  config.p2Pill2ColorTrue  else "cyan"
            val c2False = if (config.p2Pill2ColorFalse.isNotBlank()) config.p2Pill2ColorFalse else "red"
            // Beim Antippen kurz in der gewählten Farbe (True-Farbe) aufleuchten
            val pressed2 = page2Pill2Pressed && (now - page2Pill2PressedAt < PILL_PRESS_DURATION_MS)
            val sc2 = colorFromPillId(if (pressed2 || config.p2Pill2State) c2True else c2False)
            page2PillFillPaint.color   = Color.argb(if (pressed2) 255 else 180, Color.red(sc2), Color.green(sc2), Color.blue(sc2))
            page2PillStrokePaint.color = sc2
            page2Pill2Bounds.set(pill5X - halfW, pill5Y - halfH, pill5X + halfW, pill5Y + halfH)
            page2Pill2TapBounds.set(pill5X - halfW - tapPad, pill5Y - halfH - tapPad, pill5X + halfW + tapPad, pill5Y + halfH + tapPad)
            canvas.drawRoundRect(page2Pill2Bounds, halfH, halfH, page2PillFillPaint)
            canvas.drawRoundRect(page2Pill2Bounds, halfH, halfH, page2PillStrokePaint)
            pillTextPaint.color    = Color.WHITE
            pillTextPaint.textSize = pillTextSize
            val fm2 = pillTextPaint.fontMetrics
            canvas.drawText(if (config.p2Pill2State) "AN" else "AUS", pill5X, pill5Y - (fm2.ascent + fm2.descent) / 2f, pillTextPaint)
        } else {
            page2Pill2TapBounds.setEmpty()
        }
    }

    /**
     * Zeichnet die Schlafdauer auf Seite 2 (oben, über den Slots).
     */
    private fun drawPage2Sleep(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config    = WatchFaceConfigCache
        val scale     = config.sleepTextScale / 100f
        val valueSize = radius * 0.115f * scale
        val iconSize  = radius * 0.085f * scale
        val gap       = iconSize * 0.40f
        val rowY      = cy - radius * 0.62f

        // Schlaf: lokaler Health Connect (Watch) hat Vorrang vor Phone-Wert
        val sleepMin  = if (HealthDataCache.sleepMinutes > 0) HealthDataCache.sleepMinutes else config.phoneSleepMinutes
        val sleepText = if (sleepMin > 0) "${sleepMin / 60}h ${sleepMin % 60}m" else "--"

        healthValuePaint.color     = colorFromId(config.sleepColor)
        healthValuePaint.textSize  = valueSize
        healthValuePaint.textAlign = Paint.Align.LEFT

        val textW     = healthValuePaint.measureText(sleepText)
        val totalW    = iconSize + gap + textW
        val startX    = cx - totalW / 2f

        drawHealthIcon(canvas, startX + iconSize / 2f, rowY - valueSize * 0.25f, iconSize, "sleep")
        canvas.drawText(sleepText, startX + iconSize + gap, rowY, healthValuePaint)
        healthValuePaint.textAlign = Paint.Align.CENTER
    }

    /**
     * Zeichnet einen vertikalen Balken-Graph auf Seite 2 (rechts, mittig).
     * Beschriftung links: Label (oben) und Min/Max (oben/unten), Wert (Mitte).
     * Identisches Warnstufen-System wie Slot 4 auf Seite 1.
     */
    private fun drawPage2VerticalBar(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val scale    = config.p2BarTextScale / 100f
        val barW     = radius * 0.18f
        val barH     = radius * 0.68f
        val barCx    = cx + radius * 0.74f
        val barTop   = cy - barH / 2f
        val barBot   = cy + barH / 2f
        val barCorner = barW * 0.18f

        // Im Slider-Modus die Balken-Geometrie für Wert-Mapping merken
        // sowie eine großzügige Tap-Zone, die auch die Werte-Zahlen links vom
        // Balken einschließt – so tippt man bequem mittiger (weg vom Bildschirm-
        // rand, wo die System-Wischgesten liegen) auf die gewünschte Höhe.
        if (config.p2BarIsSlider) {
            page2SliderBounds.set(barCx - barW / 2f, barTop, barCx + barW / 2f, barBot)
            page2SliderTapBounds.set(
                barCx - barW / 2f - radius * 0.35f,
                barTop - radius * 0.04f,
                barCx + barW / 2f + radius * 0.10f,
                barBot + radius * 0.04f
            )
        } else {
            page2SliderBounds.setEmpty()
            page2SliderTapBounds.setEmpty()
        }

        val minVal = config.p2BarMin
        val maxVal = config.p2BarMax
        val curVal = config.p2BarValue.replace(',', '.').toFloatOrNull() ?: minVal
        val fraction = if (maxVal > minVal) ((curVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0f

        // Warnstufen
        var barColor = colorFromPillId(config.p2BarColor)
        if (!config.p2BarWarn1Value.isNaN() && curVal <= config.p2BarWarn1Value) {
            barColor = colorFromPillId(config.p2BarWarn1Color)
        }
        if (!config.p2BarWarn2Value.isNaN() && curVal <= config.p2BarWarn2Value) {
            barColor = colorFromPillId(config.p2BarWarn2Color)
        }

        // Hintergrund
        canvas.drawRoundRect(RectF(barCx - barW / 2f, barTop, barCx + barW / 2f, barBot),
            barCorner, barCorner, progressBgPaint)

        // Füllung (von unten nach oben) bzw. Slider-Knopf
        val fillPaint = Paint().apply {
            color = barColor; isAntiAlias = true; style = Paint.Style.FILL
        }
        if (config.p2BarIsSlider) {
            // Slider: gefüllte Strecke (unten → Thumb) + Knopf im gleichen Design
            val thumbY = (barBot - barH * fraction).coerceIn(barTop, barBot)
            if (thumbY < barBot - barCorner) {
                canvas.drawRoundRect(
                    RectF(barCx - barW / 2f, thumbY, barCx + barW / 2f, barBot),
                    barCorner, barCorner, fillPaint
                )
            }
            val thumbR = barW * 0.62f
            canvas.drawCircle(barCx, thumbY, thumbR, fillPaint)
            canvas.drawCircle(barCx, thumbY, thumbR * 0.5f, progressBgPaint)
        } else if (fraction > 0f) {
            val fillTop = barBot - barH * fraction
            val minFill = barBot - barCorner * 2
            canvas.drawRoundRect(
                RectF(barCx - barW / 2f, fillTop.coerceAtMost(minFill), barCx + barW / 2f, barBot),
                barCorner, barCorner, fillPaint
            )
        }

        if (config.p2BarShowLabel) {
            val labelSize = radius * 0.068f * scale
            val valueSize = radius * 0.090f * scale

            // Label (oben links vom Balken)
            page2SlotLabelPaint.textSize = labelSize
            page2SlotLabelPaint.textAlign = Paint.Align.RIGHT
            val labelText = config.p2BarLabel.take(6).uppercase()
            canvas.drawText(labelText, barCx - barW / 2f - radius * 0.04f, barTop + labelSize, page2SlotLabelPaint)

            // Max-Wert (oben links)
            page2SlotValuePaint.textSize = labelSize * 0.85f
            page2SlotValuePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(maxVal.toInt().toString(), barCx - barW / 2f - radius * 0.04f,
                barTop + labelSize * 2.2f, page2SlotValuePaint)

            // Min-Wert (unten links)
            canvas.drawText(minVal.toInt().toString(), barCx - barW / 2f - radius * 0.04f,
                barBot, page2SlotValuePaint)

            // Aktueller Wert (Mitte links)
            page2SlotValuePaint.textSize = valueSize
            canvas.drawText(config.p2BarValue, barCx - barW / 2f - radius * 0.04f,
                cy + valueSize * 0.4f, page2SlotValuePaint)

            page2SlotLabelPaint.textAlign = Paint.Align.CENTER
            page2SlotValuePaint.textAlign = Paint.Align.CENTER
        }
    }

    override fun onDestroy() {
        dataClient.removeListener(this)
        healthSensorManager.stop()
        WatchDataSyncManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    fun updateBurnInProtection() {
        burnInFrame   = (burnInFrame + 1) % 4
        burnInOffsetX = when (burnInFrame) { 0 -> -4f; 1 -> 4f; 2 -> -4f; else -> 4f }
        burnInOffsetY = when (burnInFrame) { 0 -> -4f; 1 -> -4f; 2 -> 4f; else -> 4f }
    }
}
