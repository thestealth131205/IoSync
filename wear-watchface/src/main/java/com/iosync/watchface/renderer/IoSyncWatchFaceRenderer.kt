package com.iosync.watchface.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
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
        color = Color.parseColor("#888888")
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
    private var lastTapTime = 0L

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
        private const val PATH_WATCHFACE_CONFIG = "/iosync/watchface/config"
        private const val PATH_WEATHER          = "/iosync/watchface/weather"
        private const val PATH_PHONE_BATTERY    = "/iosync/phone/battery"
        private const val PATH_CUSTOM_SLOTS     = "/iosync/watchface/custom_slots"
        private const val PATH_ACTION_PILL_STATE = "/iosync/watchface/action_pill_state"
        private const val PATH_STATES           = "/iosync/smarthome/states"
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
                        }
                        PATH_ACTION_PILL_STATE -> {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            WatchFaceConfigCache.actionPillState = dataMap.getBoolean("pill_state", false)
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
                }
                PATH_ACTION_PILL_STATE -> {
                    WatchFaceConfigCache.actionPillState = dataMap.getBoolean("pill_state", false)
                }
                PATH_STATES -> {
                    dataMap.getString("states_json")?.let { SmartHomeStateCache.updateFromJson(it) }
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

        val cx     = bounds.exactCenterX()
        val cy     = bounds.exactCenterY()
        val radius = minOf(cx, cy)

        if (!isAmbient && config.showSecondsRing) {
            drawSecondsRing(canvas, bounds, zonedDateTime.second)
        }

        if (!isAmbient && config.showTicks) {
            drawTickMarks(canvas, cx, cy, radius)
        }

        val bx = if (isAmbient) burnInOffsetX else 0f
        val by = if (isAmbient) burnInOffsetY else 0f

        val timeStr      = timeFormatter.format(zonedDateTime)
        val timeFontSize = radius * 0.437f

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
                secondsPaint.color    = dimColor(timeColor, 0.75f)
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

            // Handy-Akkustand (optional, vom Smartphone aktivierbar)
            if (config.showPhoneBattery) {
                drawPhoneBattery(canvas, cx, cy, radius, config.phoneBatteryLevel, config.phoneBatteryCharging)
            }

            // Wetter-Kreis oben links
            if (config.showWeather) {
                drawWeatherCircle(canvas, cx, cy, radius)
            }

            // Custom ioBroker-Slots (2 Datenpunkte unterhalb der Uhrzeit)
            if (config.showCustomSlots) {
                drawCustomSlots(canvas, cx, cy, radius, weekdayBottomY)
            }

            // Gesundheitsdaten (Puls, SpO2, Kalorien)
            drawHealthData(canvas, cx, cy, radius)

            // Aktions-Pille bei 6 Uhr (oberhalb des unteren Komplikations-Slots)
            if (config.actionPillEnabled) {
                drawActionPill(canvas, cx, cy, radius)
            }
        }

        drawComplications(canvas, zonedDateTime, isAmbient)

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

        val labelSize   = radius * 0.085f
        val valueSize   = radius * 0.115f
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
     * Zeichnet den Handy-Akkustand rechts neben dem Uhren-Akku (obere Komplikation).
     * Symbol: stilisiertes Smartphone-Rechteck (Canvas-gezeichnet), darunter die Prozentzahl.
     * Grün wenn das Gerät geladen wird, grau sonst. Zeigt "--" wenn noch kein Wert empfangen.
     */
    private fun drawPhoneBattery(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        level: Int,
        isCharging: Boolean
    ) {
        val paint = if (isCharging) batteryChargingPaint else batteryPaint
        phoneIconStrokePaint.color = paint.color

        // Icon-Dimensionen und Position (rechts neben der oberen Komplikation)
        val iconW   = radius * 0.095f
        val iconH   = radius * 0.155f
        val iconCx  = cx + radius * 0.575f
        val iconTop = cy - radius * 0.765f
        val iconBottom = iconTop + iconH
        val iconLeft   = iconCx - iconW / 2f
        val iconRight  = iconCx + iconW / 2f
        val cornerR    = iconW * 0.20f

        // Hintergrund-Pill
        val bgPad = radius * 0.025f
        canvas.drawRoundRect(
            RectF(iconLeft - bgPad, iconTop - bgPad, iconRight + bgPad, iconBottom + iconH * 0.22f + bgPad),
            cornerR + bgPad, cornerR + bgPad, overlayBgPaint
        )

        // Smartphone-Körper (Outline)
        canvas.drawRoundRect(RectF(iconLeft, iconTop, iconRight, iconBottom), cornerR, cornerR, phoneIconStrokePaint)

        // Bildschirm-Fläche (helles Innenrechteck)
        val sInset = iconW * 0.18f
        canvas.drawRect(
            iconLeft + sInset,
            iconTop + iconH * 0.10f,
            iconRight - sInset,
            iconBottom - iconH * 0.22f,
            phoneScreenFillPaint
        )

        // Home-Button (kleiner Kreis am unteren Innenrand)
        val homeR  = iconW * 0.13f
        val homeCy = iconBottom - iconH * 0.11f
        canvas.drawCircle(iconCx, homeCy, homeR, phoneIconStrokePaint)

        // Ladeblitz bei charging (+) über dem Screen
        if (isCharging) {
            paint.textSize = iconH * 0.28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("+", iconCx, iconTop + iconH * 0.52f, paint)
        }

        // Prozentzahl unterhalb des Icons
        val levelText = if (level >= 0) "$level%" else "--"
        paint.textSize = radius * 0.085f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(levelText, iconCx, iconBottom + radius * 0.115f, paint)
    }

    /**
     * Zeichnet die Aktions-Pille bei 6 Uhr, oberhalb des unteren Komplikations-Slots.
     * Farbe: Cyan (true/aktiv) oder Rot (false/inaktiv), konfigurierbar via Android App.
     * Doppelklick sendet eine Message an die verbundene Android App.
     */
    private fun drawActionPill(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config   = WatchFaceConfigCache
        val halfW    = radius * 0.266f   // 30% kürzer als vorher (0.38 * 0.7)
        val halfH    = radius * 0.055f
        val centerY  = cy + radius * 0.72f  // etwas weiter unten

        pillBounds.set(cx - halfW, centerY - halfH, cx + halfW, centerY + halfH)

        val activeColor = colorFromPillId(if (config.actionPillState) config.actionPillColorTrue else config.actionPillColorFalse)

        // Gefüllte Pille (leicht transparent)
        pillFillPaint.color = Color.argb(
            180,
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

    // ── Wetter-Kreis (oben links) ─────────────────────────────────────────────

    /**
     * Zeichnet einen kleinen Kreis oben links mit Wettersymbol und Temperatur.
     * Layout ähnlich wie auf dem Referenz-Bild: Kreis mit Icon + "24°"
     */
    private fun drawWeatherCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val circleRadius = radius * 0.18f
        val circleCx = cx - radius * 0.42f
        val circleCy = cy - radius * 0.50f

        // Hintergrund-Kreis
        canvas.drawCircle(circleCx, circleCy, circleRadius, weatherCircleBgPaint)
        canvas.drawCircle(circleCx, circleCy, circleRadius, weatherCircleStrokePaint)

        // Wettersymbol zeichnen
        val iconSize = circleRadius * 0.50f
        val iconCy = circleCy - circleRadius * 0.18f
        drawWeatherIcon(canvas, circleCx, iconCy, iconSize, config.weatherCondition)

        // Temperatur
        weatherTempPaint.textSize = circleRadius * 0.72f
        val tempText = "${config.weatherTemp}°"
        canvas.drawText(tempText, circleCx, circleCy + circleRadius * 0.55f, weatherTempPaint)
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

    private fun drawHealthData(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config = WatchFaceConfigCache
        val health = healthSensorManager
        val items = mutableListOf<HealthItem>()

        if (config.showHeartRate) {
            val hrText = if (health.heartRate > 0) "${health.heartRate}" else "--"
            items.add(HealthItem("PULS", hrText, Color.parseColor("#F44336"), "heart"))
        }
        if (config.showOxygen) {
            val o2Text = if (health.spO2 > 0) "${health.spO2}%" else "--%"
            items.add(HealthItem("OXYGEN", o2Text, Color.parseColor("#42A5F5"), "oxygen"))
        }
        if (config.showCalories) {
            val calText = if (health.calories > 0) "${health.calories}" else "0"
            items.add(HealthItem("KCAL", calText, Color.parseColor("#FF9800"), "flame"))
        }

        if (items.isEmpty()) return

        val labelSize = radius * 0.070f
        val valueSize = radius * 0.130f
        val iconSize  = radius * 0.065f
        val itemWidth = radius * 0.55f
        val totalWidth = items.size * itemWidth
        val startX = cx - totalWidth / 2f + itemWidth / 2f
        val baseY = cy + radius * 0.55f

        healthLabelPaint.textSize = labelSize
        healthValuePaint.textSize = valueSize

        for ((index, item) in items.withIndex()) {
            val x = startX + index * itemWidth

            // Symbol links vom Label
            val labelWidth = healthLabelPaint.measureText(item.label)
            val iconGap = iconSize * 0.35f
            val totalLabelWidth = iconSize + iconGap + labelWidth
            val labelStartX = x - totalLabelWidth / 2f

            drawHealthIcon(canvas, labelStartX + iconSize / 2f, baseY - labelSize * 0.30f, iconSize, item.icon)

            healthLabelPaint.color = Color.parseColor("#AAAAAA")
            healthLabelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(item.label, labelStartX + iconSize + iconGap, baseY, healthLabelPaint)
            healthLabelPaint.textAlign = Paint.Align.CENTER

            healthValuePaint.color = item.color
            canvas.drawText(item.value, x, baseY + valueSize * 1.2f, healthValuePaint)
        }
    }

    /** Zeichnet ein kleines Icon (Herz, Flamme, O2-Tropfen) für die Gesundheitsanzeige. */
    private fun drawHealthIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, type: String) {
        val iconPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        when (type) {
            "heart" -> {
                iconPaint.color = Color.parseColor("#AAAAAA")
                val path = android.graphics.Path()
                val s = size * 0.55f
                // Herz aus zwei Bögen + Spitze
                path.moveTo(cx, cy + s * 0.6f) // Spitze unten
                path.cubicTo(cx - s * 1.3f, cy - s * 0.2f, cx - s * 0.6f, cy - s * 1.2f, cx, cy - s * 0.5f)
                path.cubicTo(cx + s * 0.6f, cy - s * 1.2f, cx + s * 1.3f, cy - s * 0.2f, cx, cy + s * 0.6f)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            "flame" -> {
                iconPaint.color = Color.parseColor("#FF9800")
                val path = android.graphics.Path()
                val s = size * 0.55f
                // Flamme
                path.moveTo(cx, cy + s * 0.7f) // Basis unten
                path.cubicTo(cx - s * 0.8f, cy + s * 0.1f, cx - s * 0.5f, cy - s * 0.6f, cx, cy - s * 0.9f)
                path.cubicTo(cx + s * 0.5f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.1f, cx, cy + s * 0.7f)
                path.close()
                canvas.drawPath(path, iconPaint)
            }
            "oxygen" -> {
                iconPaint.color = Color.parseColor("#AAAAAA")
                val path = android.graphics.Path()
                val s = size * 0.50f
                // Tropfen (O2)
                path.moveTo(cx, cy - s * 0.9f) // Spitze oben
                path.cubicTo(cx - s * 0.8f, cy + s * 0.1f, cx - s * 0.6f, cy + s * 0.8f, cx, cy + s * 0.8f)
                path.cubicTo(cx + s * 0.6f, cy + s * 0.8f, cx + s * 0.8f, cy + s * 0.1f, cx, cy - s * 0.9f)
                path.close()
                canvas.drawPath(path, iconPaint)
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
     * Zeichnet zwei ioBroker-Werte-Slots unterhalb der Uhrzeit, nebeneinander.
     * Format: "LBL 12.3" — Label in Grau, Wert in Neon-Gelb.
     */
    private fun drawCustomSlots(canvas: Canvas, cx: Float, cy: Float, radius: Float, clockBottomY: Float) {
        val config = WatchFaceConfigCache
        val fontSize = radius * 0.11f
        customSlotLabelPaint.textSize = fontSize
        customSlotValuePaint.textSize = fontSize

        val dp5 = 5f * context.resources.displayMetrics.density
        val fm = customSlotValuePaint.fontMetrics
        val slotY = clockBottomY + dp5 - fm.ascent
        val gap = radius * 0.04f
        val slotSpacing = radius * 0.48f

        // Slot 1 (links)
        if (config.customSlot1Label.isNotBlank()) {
            val slotCx = cx - slotSpacing / 2f
            val labelText = config.customSlot1Label.take(3).uppercase()
            customSlotLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labelText, slotCx - gap / 2f, slotY, customSlotLabelPaint)
            customSlotValuePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(config.customSlot1Value, slotCx + gap / 2f, slotY, customSlotValuePaint)
        }

        // Slot 2 (rechts)
        if (config.customSlot2Label.isNotBlank()) {
            val slotCx = cx + slotSpacing / 2f
            val labelText = config.customSlot2Label.take(3).uppercase()
            customSlotLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labelText, slotCx - gap / 2f, slotY, customSlotLabelPaint)
            customSlotValuePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(config.customSlot2Value, slotCx + gap / 2f, slotY, customSlotValuePaint)
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private fun colorFromId(id: String): Int = when (id) {
        "white"       -> Color.WHITE
        "neon_yellow" -> Color.parseColor("#EAFF00")
        "cyan"        -> Color.parseColor("#00BCD4")
        else          -> Color.parseColor("#E8E8E8")
    }

    private fun dimColor(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8)  and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color          and 0xFF) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
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
        complicationSlotsManager.complicationSlots.forEach { (_, slot) ->
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
        if (tapType != TapType.UP) return
        val config = WatchFaceConfigCache
        if (!config.actionPillEnabled) return

        val x = tapEvent.xPos.toFloat()
        val y = tapEvent.yPos.toFloat()
        if (!pillBounds.contains(x, y)) return

        val now = System.currentTimeMillis()
        if (now - lastTapTime <= DOUBLE_TAP_MS) {
            lastTapTime = 0L
            triggerPillAction()
        } else {
            lastTapTime = now
        }
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
