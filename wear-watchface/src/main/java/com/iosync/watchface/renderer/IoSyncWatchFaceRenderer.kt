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
import com.iosync.watchface.datalayer.SmartHomeStateCache
import com.iosync.watchface.datalayer.WatchFaceConfigCache
import com.iosync.watchface.health.HealthSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val FRAME_PERIOD_MS_DEFAULT = 16L
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

    private var accentColor: Int = Color.parseColor("#EAFF00")

    // ── Hintergrund ───────────────────────────────────────────────────────────
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }
    private var backgroundBitmap: Bitmap? = null
    private var backgroundBitmapScaled: Bitmap? = null
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

    // ── Seite 2 ───────────────────────────────────────────────────────────────
    private var currentPage = 0          // 0 = Hauptseite, 1 = Zweite Seite
    private var nineOClockTapBounds = RectF()
    private var page2LastTapTime = 0L
    private var page2TouchActive = false
    private var page2TouchReleasedAt = 0L
    private val PAGE2_TOUCH_FADE_MS = 500L
    private var page2Pill1Bounds = RectF()
    private var page2Pill2Bounds = RectF()
    private var page2Pill1TapBounds = RectF()
    private var page2Pill2TapBounds = RectF()

    private val page2OverlayPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
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
    companion object {
        private const val DOUBLE_TAP_MS   = 400L
        private const val PATH_ACTION_TRIGGER = "/iosync/watchface/action_trigger"
        private const val TAG_PILL        = "WatchFacePill"
        private const val CONFIRM_DURATION_MS = 2000L

        // Data Layer Pfade (gespiegelt aus WatchFaceDataListenerService)
        private const val PATH_WATCHFACE_CONFIG  = "/iosync/watchface/config"
        private const val PATH_WEATHER           = "/iosync/watchface/weather"
        private const val PATH_PHONE_BATTERY     = "/iosync/phone/battery"
        private const val PATH_CUSTOM_SLOTS      = "/iosync/watchface/custom_slots"
        private const val PATH_CUSTOM_SLOTS_P2   = "/iosync/watchface/custom_slots_p2"
        private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
        private const val PATH_STATES            = "/iosync/smarthome/states"
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
                        }
                    }
                }
                dataItems.release()
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
                }
                PATH_WEATHER -> {
                    WatchFaceConfigCache.weatherTemp = dataMap.getInt("weather_temp", 0)
                    dataMap.getString("weather_condition")?.let { WatchFaceConfigCache.weatherCondition = it }
                }
                PATH_PHONE_BATTERY -> {
                    WatchFaceConfigCache.phoneBatteryLevel = dataMap.getInt("battery_level", -1)
                    WatchFaceConfigCache.phoneBatteryCharging = dataMap.getBoolean("is_charging", false)
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
                }
                PATH_ACTION_PILL_STATE -> {
                    WatchFaceConfigCache.actionPillState = dataMap.getBoolean("pill_state", false)
                }
                PATH_STATES -> {
                    dataMap.getString("states_json")?.let { SmartHomeStateCache.updateFromJson(it) }
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
                }
            }
        }
    }

    override suspend fun createSharedAssets(): SharedAssets = object : SharedAssets {
        override fun onDestroy() {}
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        val config    = WatchFaceConfigCache

        canvas.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), backgroundPaint)

        if (!isAmbient && config.showBackground) {
            val w = bounds.width()
            val h = bounds.height()
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

        val timeStr      = timeFormatter.format(zonedDateTime)
        val timeFontSize = radius * 0.437f

        try {

        if (!isAmbient && config.showSecondsRing) {
            drawSecondsRing(canvas, bounds, zonedDateTime.second)
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

            val timeBaseline = cy + timeFontSize * 0.15f

            // weekdayBottomY wird je nach Layout (mit/ohne Sekunden) gesetzt
            val weekdayBottomY: Float

            if (config.showSeconds) {
                val secStr      = secondsFormatter.format(zonedDateTime)
                val secFontSize = timeFontSize * 0.475f
                secondsPaint.color    = if (config.secondsNumberColorId == "dim_time") dimColor(timeColor, 0.75f) else colorFromId(config.secondsNumberColorId)
                secondsPaint.textSize = secFontSize

                val timeWidth = timePaint.measureText(timeStr)
                val secWidth  = secondsPaint.measureText(secStr)
                val gap       = radius * 0.025f
                val startX    = cx - (timeWidth + gap + secWidth) / 2f
                val secX      = startX + timeWidth + gap

                canvas.drawText(timeStr, startX, timeBaseline, timePaint)

                val timeFm      = timePaint.fontMetrics
                val secFm       = secondsPaint.fontMetrics
                val secBaseline = timeBaseline + timeFm.ascent - secFm.ascent
                canvas.drawText(secStr, secX, secBaseline, secondsPaint)

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
                canvas.drawText(timeStr, cx - timeWidth / 2f, timeBaseline, timePaint)

                // Ohne Sekunden: Datum zentriert unterhalb der Zeit
                weekdayBottomY = if (config.showWeekday) {
                    val dateStr      = "${weekdayShort(zonedDateTime)} ${zonedDateTime.dayOfMonth}"
                    val dateFontSize = timeFontSize * 0.252f
                    weekdayPaint.color     = dateColor
                    weekdayPaint.textSize  = dateFontSize
                    weekdayPaint.textAlign = Paint.Align.CENTER
                    val dateY = cy + timeFontSize * 0.15f + radius * 0.33f
                    canvas.drawText(dateStr, cx, dateY, weekdayPaint)
                    dateY + weekdayPaint.fontMetrics.descent
                } else {
                    cy + timeFontSize * 0.15f + radius * 0.18f
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

            // Gesundheitsdaten (Puls, SpO2, Kalorien)
            drawHealthData(canvas, cx, cy, radius, bx, by)

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
            // Warnstufen: nur die Startfarbe (von 0 an) wird ersetzt, der Verlauf zur
            // Endfarbe bleibt bestehen. Schwelle 0 = deaktiviert; Stufe 2 hat Vorrang.
            val cfg = WatchFaceConfigCache
            var startColor = color1
            if (cfg.batteryWarn1Threshold > 0 && level <= cfg.batteryWarn1Threshold) {
                startColor = colorFromId(cfg.batteryWarn1Color)
            }
            if (cfg.batteryWarn2Threshold > 0 && level <= cfg.batteryWarn2Threshold) {
                startColor = colorFromId(cfg.batteryWarn2Color)
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


    /** Sendet einen Trigger an die verbundene Android App via Wearable MessageClient. */
    private fun triggerPillAction() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(TAG_PILL, "Kein verbundenes Gerät gefunden")
                    return@launch
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, PATH_ACTION_TRIGGER, byteArrayOf())
                        .await()
                    Log.d(TAG_PILL, "Aktions-Trigger an ${node.displayName} gesendet")
                }
            } catch (e: Exception) {
                Log.w(TAG_PILL, "Aktions-Trigger fehlgeschlagen: ${e.message}")
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

        // Hintergrund: Vertiefungs-Effekt + dunkler Kreis
        drawEmbossedCircleRecess(canvas, circleCx, circleCy, circleRadius * 1.08f)
        canvas.drawCircle(circleCx, circleCy, circleRadius, weatherCircleBgPaint)

        // Wettersymbol zeichnen
        val iconSize = circleRadius * 0.80f
        val iconCy = circleCy - circleRadius * 0.22f
        drawWeatherIcon(canvas, circleCx, iconCy, iconSize, config.weatherCondition)

        // Temperatur
        val weatherScale = config.weatherTextScale / 100f
        weatherTempPaint.textSize = circleRadius * 0.72f * weatherScale
        val tempText = "${config.weatherTemp}°"
        canvas.drawText(tempText, circleCx, circleCy + circleRadius * 0.55f + 5f * density, weatherTempPaint)
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

    private fun drawHealthData(canvas: Canvas, cx: Float, cy: Float, radius: Float, bx: Float = 0f, by: Float = 0f) {
        val config = WatchFaceConfigCache
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

        // Kalorien: Komplikation (falls gewählt) > Health Connect > lokaler Sensor
        if (config.showCalories) {
            val kcal = readComplicationNumber(config.kcalComplication)
                ?: if (config.kcalSource == "healthconnect") {
                    if (phoneDataFresh) config.phoneCalories else 0
                } else {
                    healthSensorManager.calories
                }
            val kcalText = if (kcal > 0) "$kcal" else "--"
            items.add(HealthItem("KCAL", kcalText, colorFromId(config.kcalColor), "flame"))
        }

        // SpO2: Komplikation (falls gewählt) > Health Connect > lokaler Sensor
        if (config.showOxygen) {
            val o2 = readComplicationNumber(config.oxygenComplication)
                ?: if (config.oxygenSource == "healthconnect") {
                    if (phoneDataFresh) config.phoneSpO2 else 0
                } else {
                    healthSensorManager.spO2
                }
            val o2Text = if (o2 > 0) "$o2%" else "--%"
            items.add(HealthItem("OXYGEN", o2Text, colorFromId(config.oxygenColor), "oxygen"))
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

                drawHealthIcon(canvas, labelStartX + iconSize / 2f, baseY - labelSize * 0.30f, iconSize, item.icon)

                healthLabelPaint.color     = Color.parseColor("#AAAAAA")
                healthLabelPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(item.label, labelStartX + iconSize + iconGap, baseY, healthLabelPaint)
                healthLabelPaint.textAlign = Paint.Align.CENTER

                healthValuePaint.color = item.color
                canvas.drawText(item.value, x, baseY + valueSize * 1.2f, healthValuePaint)

            }
        }
    }

    /**
     * Zeichnet Schritte (links) und Schlafdauer (rechts, gespiegelt) oberhalb der Uhrzeit.
     * Schriftgröße entspricht dem Puls-Wert (radius * 0.130). Schlafdauer als "Xh Ym".
     */
    private fun drawStepsAndSleep(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val scale     = config.stepsTextScale / 100f
        val valueSize = radius * 0.130f * scale   // wie Puls-Wert
        val iconSize  = radius * 0.090f * scale
        val gap       = iconSize * 0.45f
        val rowY      = cy - radius * 0.30f

        // Schritte: aus Komplikation (System-Schrittzähler) oder lokalem Sensor
        val steps = readComplicationNumber("6") ?: healthSensorManager.steps.takeIf { it > 0 }
        val stepsText = steps?.toString() ?: "--"

        // Schlafdauer in Stunden/Minuten (vom Handy via Health Connect)
        val sleepMin = config.phoneSleepMinutes
        val sleepText = if (sleepMin > 0) "${sleepMin / 60}h ${sleepMin % 60}m" else "--"

        healthValuePaint.textSize  = valueSize
        healthValuePaint.textAlign = Paint.Align.LEFT

        // ── Schritte (links) ──────────────────────────────────────────────
        healthValuePaint.color = colorFromId(config.stepsColor)
        val stepsW  = healthValuePaint.measureText(stepsText)
        val stepsLeft = (cx - radius * 0.30f) - (iconSize + gap + stepsW) / 2f
        drawHealthIcon(canvas, stepsLeft + iconSize / 2f, rowY - valueSize * 0.30f, iconSize, "steps")
        canvas.drawText(stepsText, stepsLeft + iconSize + gap, rowY, healthValuePaint)

        // ── Schlaf (rechts, gespiegelt) ───────────────────────────────────
        healthValuePaint.color = colorFromId(config.sleepColor)
        val sleepW  = healthValuePaint.measureText(sleepText)
        val sleepLeft = (cx + radius * 0.30f) - (iconSize + gap + sleepW) / 2f
        drawHealthIcon(canvas, sleepLeft + iconSize / 2f, rowY - valueSize * 0.30f, iconSize, "sleep")
        canvas.drawText(sleepText, sleepLeft + iconSize + gap, rowY, healthValuePaint)

        healthValuePaint.textAlign = Paint.Align.CENTER
    }

    // Wiederverwendbarer Paint + Path für Health-Icons (keine Allocation pro Frame)
    private val healthIconPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val healthIconPath = android.graphics.Path()

    /** Zeichnet ein kleines Icon (Herz, Flamme, O2-Tropfen) für die Gesundheitsanzeige. */
    private fun drawHealthIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, type: String) {
        healthIconPath.reset()
        when (type) {
            "heart" -> {
                healthIconPaint.color = Color.parseColor("#AAAAAA")
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
            // Füllung
            if (fraction > 0f) {
                val fillPaint = Paint().apply {
                    color = barColor
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
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
                    page2TouchActive = false
                    invalidate()
                } else {
                    page2LastTapTime = now
                }
            }
            return
        }

        // ── Seite 2: Touch-Overlay + Pillen ─────────────────────────────────
        if (currentPage == 1) {
            if (tapType == TapType.DOWN) {
                page2TouchActive = true
                invalidate()
                return
            }
            if (tapType == TapType.UP) {
                page2TouchActive = false
                page2TouchReleasedAt = System.currentTimeMillis()
                // Pille 7 Uhr oder 5 Uhr → Aktion auslösen
                if (page2Pill1TapBounds.contains(x, y) || page2Pill2TapBounds.contains(x, y)) {
                    triggerPillAction()
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
        // Touch-Overlay: unsichtbar bis berührt, danach stark transparentes Weiß
        val alpha = if (page2TouchActive) {
            48
        } else {
            val elapsed = System.currentTimeMillis() - page2TouchReleasedAt
            if (elapsed < PAGE2_TOUCH_FADE_MS) {
                (48 * (1f - elapsed.toFloat() / PAGE2_TOUCH_FADE_MS)).toInt()
            } else 0
        }
        if (alpha > 0) {
            page2OverlayPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, radius, page2OverlayPaint)
            invalidate() // weiter animieren während Fade-out
        }

        // 4 ioBroker-Slots (oben, 2 × 2 Gitter)
        drawPage2IoBrokerSlots(canvas, cx, cy, radius)

        // 2 halbe Pillen (7 Uhr und 5 Uhr)
        drawPage2Pills(canvas, cx, cy, radius)
    }

    /**
     * Zeichnet 4 ioBroker-Slots auf Seite 2 in einem 2×2 Gitter.
     * Obere Hälfte des Zifferblatts, über den Pillen.
     */
    private fun drawPage2IoBrokerSlots(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val labelSize = radius * 0.072f
        val valueSize = radius * 0.100f
        val colOffset = radius * 0.30f

        // Zeile 1: oberer Bereich; Zeile 2: knapp unterhalb der Mitte
        val row1Y = cy - radius * 0.20f
        val row2Y = cy + radius * 0.22f

        data class P2Slot(val label: String, val value: String, val slotCx: Float, val baseY: Float)
        val slots = listOf(
            P2Slot(config.p2Slot1Label, config.p2Slot1Value, cx - colOffset, row1Y),
            P2Slot(config.p2Slot2Label, config.p2Slot2Value, cx + colOffset, row1Y),
            P2Slot(config.p2Slot3Label, config.p2Slot3Value, cx - colOffset, row2Y),
            P2Slot(config.p2Slot4Label, config.p2Slot4Value, cx + colOffset, row2Y)
        )

        for (slot in slots) {
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
     * Farbe wie die Aktions-Pille (State-abhängig).
     */
    private fun drawPage2Pills(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config  = WatchFaceConfigCache
        val halfW   = radius * 0.133f   // halb so lang wie Seite-1-Pille (0.266)
        val halfH   = radius * 0.060f
        val tapPad  = halfH * 1.5f

        // 7 Uhr: 210° von 12 → Einheitsvektor (cos120°, sin120°) = (−0.5, 0.866)
        // 5 Uhr: 150° von 12 → Einheitsvektor (cos 60°, sin 60°) = (+0.5, 0.866)
        val dist  = radius * 0.63f
        val pill7X = cx - dist * 0.50f
        val pill7Y = cy + dist * 0.866f
        val pill5X = cx + dist * 0.50f
        val pill5Y = cy + dist * 0.866f

        val stateColor = colorFromPillId(
            if (config.actionPillState) config.actionPillColorTrue else config.actionPillColorFalse
        )

        page2PillFillPaint.color = Color.argb(
            180,
            Color.red(stateColor), Color.green(stateColor), Color.blue(stateColor)
        )
        page2PillStrokePaint.color = stateColor

        // Pille bei 7 Uhr
        page2Pill1Bounds.set(pill7X - halfW, pill7Y - halfH, pill7X + halfW, pill7Y + halfH)
        page2Pill1TapBounds.set(
            pill7X - halfW - tapPad, pill7Y - halfH - tapPad,
            pill7X + halfW + tapPad, pill7Y + halfH + tapPad
        )
        canvas.drawRoundRect(page2Pill1Bounds, halfH, halfH, page2PillFillPaint)
        canvas.drawRoundRect(page2Pill1Bounds, halfH, halfH, page2PillStrokePaint)

        // Pille bei 5 Uhr
        page2Pill2Bounds.set(pill5X - halfW, pill5Y - halfH, pill5X + halfW, pill5Y + halfH)
        page2Pill2TapBounds.set(
            pill5X - halfW - tapPad, pill5Y - halfH - tapPad,
            pill5X + halfW + tapPad, pill5Y + halfH + tapPad
        )
        canvas.drawRoundRect(page2Pill2Bounds, halfH, halfH, page2PillFillPaint)
        canvas.drawRoundRect(page2Pill2Bounds, halfH, halfH, page2PillStrokePaint)
    }

    override fun onDestroy() {
        dataClient.removeListener(this)
        healthSensorManager.stop()
        scope.cancel()
        super.onDestroy()
    }

    fun updateBurnInProtection() {
        burnInFrame   = (burnInFrame + 1) % 4
        burnInOffsetX = when (burnInFrame) { 0 -> -4f; 1 -> 4f; 2 -> -4f; else -> 4f }
        burnInOffsetY = when (burnInFrame) { 0 -> -4f; 1 -> -4f; 2 -> 4f; else -> 4f }
    }
}
