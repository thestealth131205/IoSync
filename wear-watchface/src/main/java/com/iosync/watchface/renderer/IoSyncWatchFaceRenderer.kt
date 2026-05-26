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
import com.google.android.gms.wearable.Wearable
import com.iosync.watchface.datalayer.SmartHomeStateCache
import com.iosync.watchface.datalayer.WatchFaceConfigCache
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
    canvasType: Int
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeEachFrame = false
), WatchFace.TapListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val density = context.resources.displayMetrics.density

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
        textAlign = Paint.Align.LEFT
    }
    private val batteryChargingPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    private val overlayBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
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

    companion object {
        private const val DOUBLE_TAP_MS   = 400L
        private const val PATH_ACTION_TRIGGER = "/iosync/watchface/action_trigger"
        private const val TAG_PILL        = "WatchFacePill"
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
        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                userStyle[UserStyleSetting.Id("color_style")]?.let { option ->
                    accentColor = when (option.id.value) {
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
        val timeFontSize = radius * 0.46f

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
                val secFontSize = timeFontSize * 0.5f
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
                    val dateFontSize = secFontSize * 0.80f
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
                    val dateFontSize = timeFontSize * 0.28f
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
            if (config.showPhoneBattery && config.phoneBatteryLevel >= 0) {
                drawPhoneBattery(canvas, cx, cy, radius, config.phoneBatteryLevel, config.phoneBatteryCharging)
            }

            // Aktions-Pille bei 6 Uhr (oberhalb des unteren Komplikations-Slots)
            if (config.actionPillEnabled) {
                drawActionPill(canvas, cx, cy, radius)
            }
        }

        drawComplications(canvas, zonedDateTime, isAmbient)
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
     * Zeichnet den Handy-Akkustand als kleines Label oben links.
     * Grün wenn das Gerät geladen wird, grau sonst.
     */
    private fun drawPhoneBattery(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        level: Int,
        isCharging: Boolean
    ) {
        val textSize = radius * 0.095f
        val paint = if (isCharging) batteryChargingPaint else batteryPaint
        paint.textSize = textSize

        val icon = if (isCharging) "+" else "P"   // einfache ASCII-Symbole (keine Emoji-Abhängigkeit)
        val text = "$level%"
        val full = "$icon$text"

        // Position: oben links, innerhalb des Zifferblatts
        val x = cx - radius * 0.56f
        val y = cy - radius * 0.50f

        // Hintergrund-Pill für bessere Lesbarkeit
        val textBounds = Rect()
        paint.getTextBounds(full, 0, full.length, textBounds)
        val padH = textSize * 0.25f
        val padV = textSize * 0.18f
        canvas.drawRoundRect(
            RectF(x - padH, y - textBounds.height() - padV, x + textBounds.width() + padH, y + padV),
            6f, 6f, overlayBgPaint
        )
        canvas.drawText(full, x, y, paint)
    }

    /**
     * Zeichnet die Aktions-Pille bei 6 Uhr, oberhalb des unteren Komplikations-Slots.
     * Farbe: Cyan (true/aktiv) oder Rot (false/inaktiv), konfigurierbar via Android App.
     * Doppelklick sendet eine Message an die verbundene Android App.
     */
    private fun drawActionPill(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val config   = WatchFaceConfigCache
        val halfW    = radius * 0.38f
        val halfH    = radius * 0.055f
        val centerY  = cy + radius * 0.655f

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

        // Text "TRUE" / "FALSE"
        val label = if (config.actionPillState) "TRUE" else "FALSE"
        pillTextPaint.textSize = halfH * 1.15f
        pillTextPaint.color    = if (config.actionPillState) Color.BLACK else Color.WHITE
        canvas.drawText(label, cx, centerY + halfH * 0.42f, pillTextPaint)
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
            val glowSteps = 10
            val glowDepth = minOf(bounds.width(), bounds.height()) * 0.09f
            val layerWidth = (glowDepth / glowSteps) * 2.2f
            secondsGlowPaint.strokeWidth = layerWidth

            for (step in 0 until glowSteps) {
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
        scope.cancel()
        super.onDestroy()
    }

    fun updateBurnInProtection() {
        burnInFrame   = (burnInFrame + 1) % 4
        burnInOffsetX = when (burnInFrame) { 0 -> -4f; 1 -> 4f; 2 -> -4f; else -> 4f }
        burnInOffsetY = when (burnInFrame) { 0 -> -4f; 1 -> -4f; 2 -> 4f; else -> 4f }
    }
}
