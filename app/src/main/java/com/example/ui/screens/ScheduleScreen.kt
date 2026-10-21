package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlarmSchedule
import com.example.ui.AlarmViewModel
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.WarningAmber
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(viewModel: AlarmViewModel) {
    val currentSchedule by viewModel.alarmEngine.schedule.collectAsState()
    val isConnected = viewModel.alarmEngine.connectionState.collectAsState().value == com.example.data.model.ConnectionState.CONNECTED

    var enabled by remember(currentSchedule) { mutableStateOf(currentSchedule.enabled) }
    var startHour by remember(currentSchedule) { mutableIntStateOf(currentSchedule.startHour) }
    var startMinute by remember(currentSchedule) { mutableIntStateOf(currentSchedule.startMinute) }
    var endHour by remember(currentSchedule) { mutableIntStateOf(currentSchedule.endHour) }
    var endMinute by remember(currentSchedule) { mutableIntStateOf(currentSchedule.endMinute) }
    var activeDays by remember(currentSchedule) { mutableStateOf(currentSchedule.activeDays) }
    var isManualOverride by remember(currentSchedule) { mutableStateOf(currentSchedule.manualOverride) }

    val daysMap = listOf(
        1 to "Lun",
        2 to "Mar",
        3 to "Mié",
        4 to "Jue",
        5 to "Vie",
        6 to "Sáb",
        7 to "Dom"
    )

    val crossesMidnight = startHour > endHour || (startHour == endHour && startMinute > endMinute)
    val isInProtectionNow = currentSchedule.isTimeInProtectionWindow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Horarios de Protección",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Configure la ventana de tiempo fuera del horario de bodega donde el sistema activará automáticamente la detección perimetral.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Estado del Calendario Actual
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isInProtectionNow) SecureGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (isInProtectionNow) SecureGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (crossesMidnight) Icons.Default.NightsStay else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isInProtectionNow) SecureGreen else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (isInProtectionNow) "PROTECCIÓN ACTIVA AHORA" else "FUERA DE HORARIO DE PROTECCIÓN",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isInProtectionNow) SecureGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rango: ${String.format("%02d:%02d a %02d:%02d", startHour, startMinute, endHour, endMinute)} ${if (crossesMidnight) "(Cruza medianoche)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Habilitador maestro
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Activar Horario Automático",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "El ESP32 armará/desarmará el sistema según este horario",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    modifier = Modifier.testTag("schedule_enable_switch")
                )
            }
        }

        // Configuración de Horas
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Horas de Inicio y Término",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = String.format("%02d", startHour),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 0
                            if (v in 0..23) startHour = v
                        },
                        label = { Text("Hora Inicio (0-23)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_hour_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = String.format("%02d", startMinute),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 0
                            if (v in 0..59) startMinute = v
                        },
                        label = { Text("Minuto Inicio (0-59)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_min_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = String.format("%02d", endHour),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 0
                            if (v in 0..23) endHour = v
                        },
                        label = { Text("Hora Fin (0-23)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("end_hour_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = String.format("%02d", endMinute),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 0
                            if (v in 0..59) endMinute = v
                        },
                        label = { Text("Minuto Fin (0-59)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("end_min_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (crossesMidnight) {
                    Text(
                        text = "ℹ El horario cruza la medianoche (ej: noche de viernes a madrugada de sábado). La lógica evaluará correctamente ambos días.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Días de la semana
                Text(
                    text = "Días de Protección:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    daysMap.forEach { (dayId, dayName) ->
                        val isSelected = activeDays.contains(dayId)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                activeDays = if (isSelected) {
                                    activeDays - dayId
                                } else {
                                    activeDays + dayId
                                }
                            },
                            label = { Text(dayName) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                        )
                    }
                }
            }
        }

        // Advertencia si no está sincronizado con el microcontrolador
        if (!isConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Advertencia: Sin conexión con el ESP32. El horario se guardará localmente pero debe sincronizarse cuando el dispositivo esté conectado para que opere autónomamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Botón Guardar y Sincronizar
        Button(
            onClick = {
                val newSchedule = AlarmSchedule(
                    enabled = enabled,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    activeDays = activeDays,
                    timezone = ZoneId.systemDefault().id,
                    manualOverride = isManualOverride,
                    isSyncedWithEsp32 = isConnected
                )
                viewModel.saveSchedule(newSchedule)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_schedule_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CloudSync, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Guardar y Sincronizar con ESP32", fontWeight = FontWeight.Bold)
        }
    }
}
