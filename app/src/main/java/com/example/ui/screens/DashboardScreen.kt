package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlarmState
import com.example.data.model.ConnectionState
import com.example.data.model.DoorStatus
import com.example.data.model.SensorStatus
import com.example.data.model.TransportType
import com.example.ui.AlarmViewModel
import com.example.ui.components.SetupWizardDialog
import com.example.ui.components.SimulatorDialog
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.AlarmRedDark
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.SecurityNavyDark
import com.example.ui.theme.SecurityNavyMedium
import com.example.ui.theme.WarningAmber

@Composable
fun DashboardScreen(viewModel: AlarmViewModel) {
    val alarmState by viewModel.alarmEngine.alarmState.collectAsState()
    val connectionState by viewModel.alarmEngine.connectionState.collectAsState()
    val pirStatus by viewModel.alarmEngine.pirStatus.collectAsState()
    val doorStatus by viewModel.alarmEngine.doorStatus.collectAsState()
    val cameraStatus by viewModel.alarmEngine.cameraStatus.collectAsState()
    val myRole by viewModel.myRole.collectAsState()
    val sirenActive by viewModel.alarmEngine.sirenActive.collectAsState()
    val strobeActive by viewModel.alarmEngine.strobeActive.collectAsState()
    val lastComm by viewModel.alarmEngine.lastCommunicationTime.collectAsState()
    val activeTransport by viewModel.alarmEngine.activeTransportType.collectAsState()
    val schedule by viewModel.alarmEngine.schedule.collectAsState()
    val lastArmedBeforeDisconnect by viewModel.alarmEngine.lastArmedStateBeforeDisconnect.collectAsState()
    val showSetupWizard by viewModel.showSetupWizard.collectAsState()

    var showConfirmArmDialog by remember { mutableStateOf(false) }
    var showConfirmDisarmDialog by remember { mutableStateOf(false) }
    var showSimulatorDialog by remember { mutableStateOf(false) }

    // Animación de pulso para estado de ALERTA
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val alertPulseColor by infiniteTransition.animateColor(
        initialValue = AlarmRed,
        targetValue = AlarmRed.copy(alpha = 0.3f),
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner de Conexión & Transporte
        ConnectionStatusBar(
            connectionState = connectionState,
            transportType = activeTransport,
            lastComm = lastComm,
            onOpenSimulator = { showSimulatorDialog = true },
            onOpenWizard = { viewModel.setShowSetupWizard(true) }
        )

        // Advertencia crítica si se perdió conexión mientras estaba armada
        if (alarmState == AlarmState.SIN_CONEXION && lastArmedBeforeDisconnect != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "¡Atención: Enlace Perdido!",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarningAmber
                        )
                        Text(
                            text = "El perímetro estaba ARMADO antes de perder conexión. El ESP32 continúa operando de forma autónoma con la sirena física. No asuma que la bodega está desarmada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Hero Card de Estado de Alarma
        AlarmStateHeroCard(
            alarmState = alarmState,
            sirenActive = sirenActive,
            alertPulseColor = alertPulseColor
        )

        // Botones de Acción Primaria
        AlarmActionControls(
            alarmState = alarmState,
            sirenActive = sirenActive,
            onArmClick = { showConfirmArmDialog = true },
            onDisarmClick = { showConfirmDisarmDialog = true },
            onSilenceClick = { viewModel.silenceSiren() },
            onTestClick = { viewModel.testAlarm() }
        )

        // Tarjetas de Sensores
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sensores de Intrusión (3 Activos)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Rol: ${myRole.label.split("/")[0]}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = SecureGreen
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorCard(
                title = "Sensor PIR",
                subtitle = "Infrarrojo Bodega",
                statusText = pirStatus.label,
                isActive = pirStatus == SensorStatus.MOTION_DETECTED,
                isFault = pirStatus == SensorStatus.FAULT,
                icon = Icons.Default.DirectionsWalk,
                activeColor = SafetyOrange,
                modifier = Modifier.weight(1f)
            )

            SensorCard(
                title = "Reed Switch",
                subtitle = "Portón de Acceso",
                statusText = doorStatus.label,
                isActive = doorStatus == DoorStatus.OPEN,
                isFault = doorStatus == DoorStatus.FAULT,
                icon = Icons.Default.MeetingRoom,
                activeColor = AlarmRed,
                modifier = Modifier.weight(1f)
            )

            SensorCard(
                title = "Cámara",
                subtitle = "Visión CameraX",
                statusText = cameraStatus.label,
                isActive = cameraStatus == SensorStatus.MOTION_DETECTED,
                isFault = false,
                icon = Icons.Default.Videocam,
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Tarjetas de Actuadores Físicos
        Text(
            text = "Actuadores Físicos",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActuatorCard(
                title = "Sirena Sonora",
                subtitle = if (sirenActive) "EMITIENDO ALARMA" else "Inactiva / Silenciada",
                isActive = sirenActive,
                activeIcon = Icons.Default.NotificationsActive,
                inactiveIcon = Icons.Default.NotificationsOff,
                activeColor = AlarmRed,
                modifier = Modifier.weight(1f)
            )

            ActuatorCard(
                title = "Luz Estroboscópica",
                subtitle = if (strobeActive) "DESTELLANDO" else "Apagada",
                isActive = strobeActive,
                activeIcon = Icons.Default.FlashOn,
                inactiveIcon = Icons.Default.FlashOn,
                activeColor = SafetyOrange,
                modifier = Modifier.weight(1f)
            )
        }

        // Resumen de Horario de Protección
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Horario de Protección Automática",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${schedule.formattedTimeRange()} (${schedule.timezone})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (schedule.enabled) {
                            if (schedule.isTimeInProtectionWindow()) "• Ventana de protección activa ahora" else "• Fuera de horario de protección"
                        } else {
                            "• Programación automática deshabilitada"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (schedule.enabled && schedule.isTimeInProtectionWindow()) SecureGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Diálogos de Confirmación
    if (showConfirmArmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmArmDialog = false },
            title = { Text("Confirmar Armado de Perímetro") },
            text = { Text("¿Desea armar la alarma de la bodega? Cualquier detección de movimiento o apertura de puerta activará de inmediato la sirena y luz estroboscópica.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmArmDialog = false
                        viewModel.armAlarm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                    modifier = Modifier.testTag("confirm_arm_btn")
                ) {
                    Text("Sí, Armar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmArmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showConfirmDisarmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDisarmDialog = false },
            title = { Text("Confirmar Desarmado") },
            text = { Text("¿Desea desarmar la alarma perimetral? Los sensores dejarán de disparar la sirena.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDisarmDialog = false
                        viewModel.disarmAlarm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    modifier = Modifier.testTag("confirm_disarm_btn")
                ) {
                    Text("Sí, Desarmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDisarmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showSimulatorDialog) {
        SimulatorDialog(
            viewModel = viewModel,
            onDismiss = { showSimulatorDialog = false }
        )
    }

    if (showSetupWizard) {
        SetupWizardDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowSetupWizard(false) }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    transportType: TransportType,
    lastComm: String?,
    onOpenSimulator: () -> Unit,
    onOpenWizard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (connectionState) {
                    ConnectionState.CONNECTED -> Icons.Default.CheckCircle
                    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Icons.Default.Wifi
                    else -> Icons.Default.SignalCellularConnectedNoInternet0Bar
                }
                val iconColor = when (connectionState) {
                    ConnectionState.CONNECTED -> SecureGreen
                    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> SafetyOrange
                    else -> AlarmRed
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${transportType.label}: ${connectionState.label}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (lastComm != null) "Última telemetría: $lastComm" else "Sin datos recibidos aún",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onOpenWizard,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("open_wizard_btn")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guía", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = onOpenSimulator,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("open_simulator_btn")
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sim", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AlarmStateHeroCard(
    alarmState: AlarmState,
    sirenActive: Boolean,
    alertPulseColor: Color
) {
    val (bgGradientColor, icon, statusTitle, statusSubtitle) = when (alarmState) {
        AlarmState.DESARMADA -> Quadruple(
            Color(0xFF37474F),
            Icons.Default.LockOpen,
            "SISTEMA DESARMADO",
            "Monitoreo pasivo activo. Los sensores registran eventos sin activar sirena."
        )
        AlarmState.ARMADA -> Quadruple(
            SecureGreen,
            Icons.Default.Lock,
            "SISTEMA ARMADO",
            "Protección activa. Cualquier intrusión disparará la sirena y luz estroboscópica."
        )
        AlarmState.ALERTA -> Quadruple(
            alertPulseColor,
            Icons.Default.Warning,
            "¡ALERTA DE INTRUSIÓN!",
            if (sirenActive) "Sirena sonando y luz destellando. Verifique la bodega de inmediato." else "Intrusión detectada. Sirena silenciada."
        )
        AlarmState.FALLO_SENSOR -> Quadruple(
            WarningAmber,
            Icons.Default.Warning,
            "FALLO EN SENSORES",
            "Anomalía en lectura de hardware o sensores desconectados."
        )
        AlarmState.SIN_CONEXION -> Quadruple(
            AlarmRedDark,
            Icons.Default.SignalCellularConnectedNoInternet0Bar,
            "SIN COMUNICACIÓN",
            "Sin enlace con controlador. El ESP32 protege la bodega con sus reglas locales."
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_state_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgGradientColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = statusTitle,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun AlarmActionControls(
    alarmState: AlarmState,
    sirenActive: Boolean,
    onArmClick: () -> Unit,
    onDisarmClick: () -> Unit,
    onSilenceClick: () -> Unit,
    onTestClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón Armar
            Button(
                onClick = onArmClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("arm_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecureGreen),
                enabled = alarmState != AlarmState.ARMADA && alarmState != AlarmState.ALERTA
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Armar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Botón Desarmar
            Button(
                onClick = onDisarmClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("disarm_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                enabled = alarmState == AlarmState.ARMADA || alarmState == AlarmState.ALERTA
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Desarmar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón Silenciar Sirena
            Button(
                onClick = onSilenceClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("silence_siren_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmRed,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                enabled = sirenActive
            ) {
                Icon(Icons.Default.VolumeOff, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Silenciar Sirena", fontWeight = FontWeight.SemiBold)
            }

            // Botón Test
            OutlinedButton(
                onClick = onTestClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_alarm_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Probar Alarma", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SensorCard(
    title: String,
    subtitle: String,
    statusText: String,
    isActive: Boolean,
    isFault: Boolean,
    icon: ImageVector,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isFault) WarningAmber else if (isActive) activeColor else Color.Transparent
    val statusColor = if (isFault) WarningAmber else if (isActive) activeColor else SecureGreen

    Card(
        modifier = modifier
            .border(width = if (isActive || isFault) 2.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isFault) "FALLO" else if (isActive) "ACTIVO" else "OK",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActuatorCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val stateColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .border(width = if (isActive) 2.dp else 0.dp, color = if (isActive) activeColor else Color.Transparent, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isActive) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) activeIcon else inactiveIcon,
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                ),
                color = stateColor
            )
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
