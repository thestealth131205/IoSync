package com.iosync.watchface

import android.content.Context
import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import com.iosync.watchface.health.HealthSensorManager
import com.iosync.watchface.renderer.IoSyncWatchFaceRenderer

/**
 * IoSync WatchFaceService — Wear OS 4 compatible watch face.
 *
 * Uses the Jetpack Watch Face API (androidx.watchface).
 *
 * Complications layout:
 *   ┌─────────────────┐
 *   │       TOP        │  ← Slot 0 (ioBroker data point)
 *   │  LEFT  ●  RIGHT  │  ← Slots 1 & 2
 *   │      BOTTOM      │  ← Slot 3 (ioBroker data point)
 *   └─────────────────┘
 */
class IoSyncWatchFaceService : WatchFaceService() {

    companion object {
        const val COMPLICATION_TOP_ID = 0
        const val COMPLICATION_BOTTOM_ID = 1
        const val COMPLICATION_LEFT_ID = 2
        const val COMPLICATION_RIGHT_ID = 3
        const val COMPLICATION_HEART_RATE_ID = 4
        const val COMPLICATION_CALORIES_ID = 5
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val healthSensorManager = try {
            HealthSensorManager(applicationContext).also { it.start() }
        } catch (e: Exception) {
            android.util.Log.e("IoSyncWatchFace", "HealthSensorManager init fehlgeschlagen", e)
            null
        }

        val renderer = IoSyncWatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.SOFTWARE,
            healthSensorManager = healthSensorManager ?: HealthSensorManager.NOOP
        )

        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer
        ).apply {
            setTapListener(renderer)
        }
    }

    override fun createUserStyleSchema(): UserStyleSchema {
        val colorStyle = UserStyleSetting.ListUserStyleSetting(
            id = UserStyleSetting.Id("color_style"),
            displayName = "Farb-Stil",
            description = "Wähle die Akzentfarbe",
            icon = null,
            options = listOf(
                UserStyleSetting.ListUserStyleSetting.ListOption(
                    id = UserStyleSetting.Option.Id("neon_yellow"),
                    displayName = "Neon Gelb",
                    screenReaderName = "Neon Gelb",
                    icon = null
                ),
                UserStyleSetting.ListUserStyleSetting.ListOption(
                    id = UserStyleSetting.Option.Id("white"),
                    displayName = "Weiß",
                    screenReaderName = "Weiß",
                    icon = null
                ),
                UserStyleSetting.ListUserStyleSetting.ListOption(
                    id = UserStyleSetting.Option.Id("cyan"),
                    displayName = "Cyan",
                    screenReaderName = "Cyan",
                    icon = null
                )
            ),
            affectsWatchFaceLayers = listOf(
                androidx.wear.watchface.style.WatchFaceLayer.BASE,
                androidx.wear.watchface.style.WatchFaceLayer.COMPLICATIONS
            )
        )

        val showSecondsSetting = UserStyleSetting.BooleanUserStyleSetting(
            id = UserStyleSetting.Id("show_seconds"),
            displayName = "Sekunden anzeigen",
            description = "Zeigt oder versteckt den Sekundenzeiger",
            icon = null,
            defaultValue = true,
            affectsWatchFaceLayers = listOf(androidx.wear.watchface.style.WatchFaceLayer.BASE)
        )

        return UserStyleSchema(listOf(colorStyle, showSecondsSetting))
    }

    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager {
        val context: Context = applicationContext

        val topComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_TOP_ID,
            bounds = RectF(0.30f, 0.08f, 0.52f, 0.30f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_WATCH_BATTERY,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.SMALL_IMAGE
            ),
            textSizeSp = 20
        )

        val bottomComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_BOTTOM_ID,
            bounds = RectF(0.34f, 0.60f, 0.66f, 0.72f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_STEP_COUNT,
            supportedTypes = listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.SHORT_TEXT,
                ComplicationType.SMALL_IMAGE
            ),
            textSizeSp = 20,
            defaultType = ComplicationType.RANGED_VALUE
        )

        val leftComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_LEFT_ID,
            bounds = RectF(0.0f, 0.50f, 0.28f, 0.74f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.SMALL_IMAGE,
                ComplicationType.MONOCHROMATIC_IMAGE
            ),
            textSizeSp = 20
        )

        // Native Komplikation: Puls (links unten)
        val heartRateComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_HEART_RATE_ID,
            bounds = RectF(0.05f, 0.68f, 0.40f, 0.88f),
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.SMALL_IMAGE
            ),
            textSizeSp = 18
        )

        // Native Komplikation: Kalorien (rechts unten)
        val caloriesComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_CALORIES_ID,
            bounds = RectF(0.60f, 0.68f, 0.95f, 0.88f),
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.SMALL_IMAGE
            ),
            textSizeSp = 18
        )

        return ComplicationSlotsManager(
            complicationSlotCollection = listOf(
                topComplication,
                bottomComplication,
                leftComplication,
                heartRateComplication,
                caloriesComplication
            ),
            currentUserStyleRepository = currentUserStyleRepository
        )
    }

    private fun buildComplicationSlot(
        context: Context,
        id: Int,
        bounds: RectF,
        defaultDataSource: Int? = null,
        supportedTypes: List<ComplicationType>,
        textSizeSp: Int = 14,
        defaultType: ComplicationType = supportedTypes.first()
    ): ComplicationSlot {
        val drawable = ComplicationDrawable(context).apply {
            activeStyle.apply {
                textColor = android.graphics.Color.parseColor("#EAFF00")
                titleColor = android.graphics.Color.parseColor("#999999")
                iconColor = android.graphics.Color.parseColor("#BBBBBB")
                borderColor = android.graphics.Color.TRANSPARENT
                borderWidth = 0
                backgroundColor = android.graphics.Color.TRANSPARENT
                rangedValuePrimaryColor = android.graphics.Color.parseColor("#EAFF00")
                rangedValueSecondaryColor = android.graphics.Color.parseColor("#2E2E2E")
                textSize = textSizeSp
                titleSize = (textSizeSp * 0.75f).toInt()
            }
            ambientStyle.apply {
                textColor = android.graphics.Color.parseColor("#888888")
                borderColor = android.graphics.Color.parseColor("#333333")
                backgroundColor = android.graphics.Color.TRANSPARENT
                iconColor = android.graphics.Color.parseColor("#888888")
            }
        }

        val factory = CanvasComplicationFactory { watchState, listener ->
            CanvasComplicationDrawable(drawable, watchState, listener)
        }

        val policy = if (defaultDataSource != null) {
            DefaultComplicationDataSourcePolicy(
                systemDataSource = defaultDataSource,
                systemDataSourceDefaultType = defaultType
            )
        } else {
            DefaultComplicationDataSourcePolicy(
                systemDataSource = SystemDataSources.NO_DATA_SOURCE,
                systemDataSourceDefaultType = defaultType
            )
        }

        return ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = id,
            canvasComplicationFactory = factory,
            supportedTypes = supportedTypes,
            defaultDataSourcePolicy = policy,
            bounds = ComplicationSlotBounds(bounds)
        ).build()
    }
}
