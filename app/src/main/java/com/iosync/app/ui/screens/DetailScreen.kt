package com.iosync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.iosync.app.data.model.StateType
import com.iosync.app.ui.theme.NeonYellow
import com.iosync.app.ui.theme.SurfaceMid
import com.iosync.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    stateId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.getStateById(stateId)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state?.name ?: stateId,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (state == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Datenpunkt nicht gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ID Card
            DetailCard(label = "Datenpunkt-ID") {
                Text(
                    text = state.id,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Current value card
            DetailCard(label = "Aktueller Wert") {
                Text(
                    text = state.displayValue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NeonYellow
                )
            }

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailCard(label = "Typ", modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.type.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                DetailCard(label = "Status", modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isOnline) "Online" else "Fehler (Q=${state.quality})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isOnline) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.room != null) {
                DetailCard(label = "Raum") {
                    Text(text = state.room, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Timestamp
            DetailCard(label = "Letztes Update") {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
                Text(
                    text = sdf.format(Date(state.timestamp)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Control section
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Steuern",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (state.type) {
                StateType.BOOLEAN -> BooleanControl(
                    currentValue = state.value?.lowercase() == "true",
                    onToggle = { newValue ->
                        viewModel.setStateValue(state.id, newValue.toString())
                    }
                )
                StateType.NUMBER, StateType.STRING, StateType.MIXED -> TextControl(
                    currentValue = state.value ?: "",
                    unit = state.unit,
                    onSend = { newValue ->
                        viewModel.setStateValue(state.id, newValue)
                    }
                )
            }
        }
    }
}

@Composable
fun DetailCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceMid)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun BooleanControl(
    currentValue: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceMid)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (currentValue) "AN" else "AUS",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (currentValue) NeonYellow else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = currentValue,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonYellow,
                checkedTrackColor = NeonYellow.copy(alpha = 0.3f),
                checkedIconColor = androidx.compose.ui.graphics.Color(0xFF1A1A00)
            )
        )
    }
}

@Composable
fun TextControl(
    currentValue: String,
    unit: String?,
    onSend: (String) -> Unit
) {
    var inputValue by remember(currentValue) { mutableStateOf(currentValue) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            modifier = Modifier.weight(1f),
            label = { Text(if (unit != null) "Wert ($unit)" else "Wert") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend(inputValue) })
        )
        Button(
            onClick = { onSend(inputValue) },
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonYellow,
                contentColor = androidx.compose.ui.graphics.Color(0xFF1A1A00)
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = "Senden")
        }
    }
}
