package com.iosync.watchface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            editorSession = EditorSession.createOnWatchEditorSession(this@WatchFaceConfigActivity)
        }

        setContent {
            MaterialTheme {
                WatchFaceConfigScreen(
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

@Composable
fun WatchFaceConfigScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "IoSync\nWatchface",
            style = MaterialTheme.typography.title2.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEAFF00)
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Konfiguration\nComplication-Slots\nkoennen angepasst werden",
            style = MaterialTheme.typography.body2,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFEAFF00)
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Schliessen",
                color = Color(0xFF1A1A00),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
