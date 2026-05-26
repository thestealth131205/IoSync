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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Configuration / Editor activity for the IoSync Watch Face.
 * Launched when the user taps "Customize" in the watch face picker.
 *
 * For a full implementation, integrate with the Jetpack WatchFaceEditorSession
 * to allow live preview of style changes.
 */
class WatchFaceConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WatchFaceConfigScreen(
                    onClose = { finish() }
                )
            }
        }
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
            text = "Konfiguration\nüber Wear OS\nZiffernblatt-Editor",
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
                text = "Schließen",
                color = Color(0xFF1A1A00),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
