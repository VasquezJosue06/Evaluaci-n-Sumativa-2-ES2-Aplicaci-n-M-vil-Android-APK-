package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DeviceRole
import com.example.ui.AlarmViewModel
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen

@Composable
fun SetupWizardDialog(
    viewModel: AlarmViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 9

    var deviceName by remember { mutableStateOf(viewModel.myDeviceName.value) }
    var selectedRole by remember { mutableStateOf(viewModel.myRole.value) }
    var useCameraSensor by remember { mutableStateOf(true) }
    var useExternalPir by remember { mutableStateOf(true) }
    var useReedDoor by remember { mutableStateOf(true) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var alarmTestDone by remember { mutableStateOf(false) }

    val capabilities by viewModel.deviceCapabilities.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera del Asistente
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Asistente de Configuración",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Paso $step de $totalSteps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                LinearProgressIndicator(
                    progress = { step / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Contenido dinámico según el paso actual
                when (step) {
                    1 -> {
                        Text("Paso 1: Dar nombre al dispositivo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Asigne un nombre identificador fácil de reconocer por los otros miembros de la red.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Nombre del dispositivo") },
                            placeholder = { Text("Ej: Celular Guardia 1 / Tablet Oficina") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wizard_device_name_input"),
                            singleLine = true
                        )
                    }
                    2 -> {
                        Text("Paso 2: Capacidades Reales Detectadas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Android ha inspeccionado el hardware físico real disponible en este equipo:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        capabilities.take(5).forEach { cap ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cap.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(cap.status.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    3 -> {
                        Text("Paso 3: Elegir el Rol del Dispositivo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Defina qué papel cumple este equipo en la arquitectura perimetral:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        DeviceRole.values().forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.label) },
                                leadingIcon = if (selectedRole == role) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            )
                        }
                    }
                    4 -> {
                        Text("Paso 4: Sensores de Intrusión", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Seleccione qué sensores estarán activos para reportar intrusiones:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        FilterChip(
                            selected = useCameraSensor,
                            onClick = { useCameraSensor = !useCameraSensor },
                            label = { Text("Cámara en tiempo real (CameraX)") },
                            leadingIcon = if (useCameraSensor) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = useExternalPir,
                            onClick = { useExternalPir = !useExternalPir },
                            label = { Text("Sensor PIR Infrarrojo (ESP32)") },
                            leadingIcon = if (useExternalPir) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = useReedDoor,
                            onClick = { useReedDoor = !useReedDoor },
                            label = { Text("Sensor Magnético Puerta (ESP32)") },
                            leadingIcon = if (useReedDoor) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    5 -> {
                        Text("Paso 5: Emparejamiento y Código de Seguridad", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Genere un código temporal de 6 dígitos para autorizar otro celular o ingrese el código del equipo anfitrión:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        val code = viewModel.activePairingCode.collectAsState().value
                        if (code != null) {
                            Text("Código Temporal Activo: $code", fontWeight = FontWeight.Black, fontSize = 20.sp, color = SecureGreen)
                        } else {
                            OutlinedButton(onClick = { viewModel.generatePairingCode() }) {
                                Text("Generar Código de 6 Dígitos")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { pairingCodeInput = it },
                            label = { Text("O ingresar código recibido") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    6 -> {
                        Text("Paso 6: Configurar Eventos a Transmitir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Se activará el canal de alertas críticas (Intrusión por cámara, PIR y puerta). Si la red se corta, se encolarán automáticamente sin perderse.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Text("• Alertas críticas: Activación inmediata de sirena\n• Desconexión: Alerta visual\n• Cola offline: Sincronización automática al reconectar", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    7 -> {
                        Text("Paso 7: Probar Comunicación de Red", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Verifique que el enlace TCP o Bluetooth esté listo para intercambiar paquetes JSON.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(14.dp))
                        val connState = viewModel.alarmEngine.connectionState.collectAsState().value
                        Text("Estado de red: ${connState.label}", fontWeight = FontWeight.Bold, color = if (connState == com.example.data.model.ConnectionState.CONNECTED) SecureGreen else SafetyOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.connectTcp() }) {
                            Text("Probar Conexión TCP")
                        }
                    }
                    8 -> {
                        Text("Paso 8: Prueba de Alarma Local", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Compruebe que este dispositivo emite vibración y advertencias ante intrusión:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.testAlarm()
                                alarmTestDone = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ejecutar Prueba de Alarma (3s)")
                        }
                        if (alarmTestDone) {
                            Text("✓ Prueba ejecutada correctamente", color = SecureGreen, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    9 -> {
                        Text("Paso 9: Resumen y Confirmación", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Configuración lista para operar:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("• Dispositivo: $deviceName", fontWeight = FontWeight.Bold)
                                Text("• Rol: ${selectedRole.label}")
                                Text("• Detección por Cámara: ${if (useCameraSensor) "Habilitada" else "Deshabilitada"}")
                                Text("• Protocolo: TCP/IP 2.0 y Bluetooth SPP")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones de Navegación del Asistente
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        OutlinedButton(onClick = { step-- }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atrás")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (step < totalSteps) {
                        Button(onClick = {
                            if (step == 1) viewModel.setLocalDeviceName(deviceName)
                            if (step == 3) viewModel.setLocalDeviceRole(selectedRole)
                            if (step == 4) viewModel.setCameraMonitoring(useCameraSensor)
                            step++
                        }) {
                            Text("Siguiente")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.setLocalDeviceName(deviceName)
                                viewModel.setLocalDeviceRole(selectedRole)
                                viewModel.setCameraMonitoring(useCameraSensor)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecureGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Finalizar y Guardar")
                        }
                    }
                }
            }
        }
    }
}
