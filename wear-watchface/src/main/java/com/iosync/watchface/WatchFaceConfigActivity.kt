package com.iosync.watchface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.watchface.editor.EditorSession
import kotlinx.coroutines.launch

/**
 * Editor-Activity fuer das IoSync Watch Face.
 * Wird angezeigt, wenn der Nutzer beim langen Druecken auf "Anpassen" tippt.
 * Nutzt EditorSession fuer die Integration mit dem Wear OS Watch Face Editor.
 */
class WatchFaceConfigActivity : ComponentActivity() {

    private var editorSession: EditorSession? = null
    private var editorReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                editorSession = EditorSession.createOnWatchEditorSession(this@WatchFaceConfigActivity)
                editorReady = true
            } catch (e: Exception) {
                android.util.Log.e("WatchFaceConfig", "EditorSession fehlgeschlagen", e)
            }
        }

        setContent {
            MaterialTheme {
                WatchFaceConfigScreen(
                    editorReady = editorReady,
                    onEditComplication = { slotId ->
                        lifecycleScope.launch {
                            try {
                                editorSession?.openComplicationDataSourceChooser(slotId)
                            } catch (e: Exception) {
                                android.util.Log.e("WatchFaceConfig", "Complication-Chooser Fehler", e)
                            }
                        }
                    },
                    onClose = {
                        editorSession?.close()
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editorSession?.close()
    }
}

private val NEON_YELLOW = Color(0xFFEAFF00)
private val DARK_BG = Color(0xFF080808)
private val BUTTON_TEXT = Color(0xFF1A1A00)
private val SUBTITLE_COLOR = Color(0xFF999999)

@Composable
fun WatchFaceConfigScreen(
    editorReady: Boolean,
    onEditComplication: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DARK_BG)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "IoSync",
            style = MaterialTheme.typography.title2.copy(
                fontWeight = FontWeight.Bold,
                color = NEON_YELLOW
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Complications anpassen",
            style = MaterialTheme.typography.body2,
            color = SUBTITLE_COLOR,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (!editorReady) {
            Text(
                text = "Lade Editor...",
                color = SUBTITLE_COLOR,
                style = MaterialTheme.typography.body2
            )
        } else {
            // Complication-Slot Buttons
            ComplicationButton("Oben (Batterie)", IoSyncWatchFaceService.COMPLICATION_TOP_ID, onEditComplication)
            ComplicationButton("Links", IoSyncWatchFaceService.COMPLICATION_LEFT_ID, onEditComplication)
            ComplicationButton("Puls", IoSyncWatchFaceService.COMPLICATION_HEART_RATE_ID, onEditComplication)
            ComplicationButton("Schritte", IoSyncWatchFaceService.COMPLICATION_STEPS_ID, onEditComplication)
            ComplicationButton("Kalorien", IoSyncWatchFaceService.COMPLICATION_CALORIES_ID, onEditComplication)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(backgroundColor = NEON_YELLOW),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Fertig",
                color = BUTTON_TEXT,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ComplicationButton(
    label: String,
    slotId: Int,
    onEdit: (Int) -> Unit
) {
    Button(
        onClick = { onEdit(slotId) },
        modifier = Modifier.fillMaxWidth(0.85f),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = NEON_YELLOW,
            style = MaterialTheme.typography.body2
        )
    }
}
