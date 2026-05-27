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
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val healthSensorManager = HealthSensorManager(applicationContext)
        healthSensorManager.start()

        val renderer = IoSyncWatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE,
            healthSensorManager = healthSensorManager
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
            bounds = RectF(0.3f, 0.1f, 0.7f, 0.3f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_WATCH_BATTERY,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.SMALL_IMAGE
            )
        )

        val bottomComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_BOTTOM_ID,
            bounds = RectF(0.3f, 0.7f, 0.7f, 0.9f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_STEP_COUNT,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.SMALL_IMAGE
            )
        )

        val leftComplication = buildComplicationSlot(
            context = context,
            id = COMPLICATION_LEFT_ID,
            bounds = RectF(0.10f, 0.50f, 0.35f, 0.75f),
            defaultDataSource = SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.SMALL_IMAGE,
                ComplicationType.MONOCHROMATIC_IMAGE
            )
        )

        return ComplicationSlotsManager(
            complicationSlotCollection = listOf(
                topComplication,
                bottomComplication,
                leftComplication
            ),
            currentUserStyleRepository = currentUserStyleRepository
        )
    }

    private fun buildComplicationSlot(
        context: Context,
        id: Int,
        bounds: RectF,
        defaultDataSource: Int,
        supportedTypes: List<ComplicationType>
    ): ComplicationSlot {
        val drawable = ComplicationDrawable(context).apply {
            activeStyle.apply {
                textColor = android.graphics.Color.parseColor("#EAFF00")
                titleColor = android.graphics.Color.parseColor("#999999")
                iconColor = android.graphics.Color.parseColor("#EAFF00")
                borderColor = android.graphics.Color.TRANSPARENT
                borderWidth = 0
                backgroundColor = android.graphics.Color.TRANSPARENT
                rangedValuePrimaryColor = android.graphics.Color.parseColor("#EAFF00")
                rangedValueSecondaryColor = android.graphics.Color.parseColor("#2E2E2E")
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

        return ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = id,
            canvasComplicationFactory = factory,
            supportedTypes = supportedTypes,
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                systemDataSource = defaultDataSource,
                systemDataSourceDefaultType = supportedTypes.first()
            ),
            bounds = ComplicationSlotBounds(bounds)
        ).build()
    }
}
