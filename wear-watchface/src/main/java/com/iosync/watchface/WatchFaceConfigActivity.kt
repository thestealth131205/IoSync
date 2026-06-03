package com.iosync.watchface

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
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

private val HEALTH_CONNECT_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class)
)

@Composable
fun WatchFaceConfigScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Status: wurden Health Connect Berechtigungen erteilt?
    var healthPermissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        healthPermissionsGranted = granted.containsAll(HEALTH_CONNECT_PERMISSIONS)
    }

    // Beim Start Berechtigungs-Status laden
    LaunchedEffect(Unit) {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            healthPermissionsGranted = granted.containsAll(HEALTH_CONNECT_PERMISSIONS)
        } catch (_: Exception) {}
    }

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

        // Health Connect Berechtigungsanfrage (für Kalorien, SpO2, Schlaf)
        val hcButtonColor = if (healthPermissionsGranted) Color(0xFF2E7D32) else Color(0xFFEAFF00)
        val hcButtonText  = if (healthPermissionsGranted) "Health ✓" else "Health Connect"
        Button(
            onClick = {
                if (!healthPermissionsGranted) {
                    permissionLauncher.launch(HEALTH_CONNECT_PERMISSIONS)
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(backgroundColor = hcButtonColor),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = hcButtonText,
                color = if (healthPermissionsGranted) Color.White else Color(0xFF1A1A00),
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF333333)
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Schliessen",
                color = Color(0xFFCCCCCC),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
