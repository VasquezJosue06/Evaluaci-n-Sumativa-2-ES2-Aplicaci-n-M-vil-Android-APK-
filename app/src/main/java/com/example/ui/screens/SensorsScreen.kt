package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.service.CapabilityStatus
import com.example.ui.AlarmViewModel
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.WarningAmber

@Composable
fun SensorsScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val capabilities by viewModel.deviceCapabilities.collectAsState()
    val isCameraActive by viewModel.alarmEngine.cameraDetector.isMonitoring.collectAsState()
    val motionScore by viewModel.alarmEngine.cameraDetector.motionScore.collectAsState()

    var cameraSensitivity by remember { mutableFloatStateOf(30f) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.refreshCapabilities()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.alarmEngine.cameraDetector.stopCamera()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sensores y Capacidades",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Inspección de hardware real del dispositivo y detector CameraX",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { viewModel.refreshCapabilities() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // SECCIÓN 1: DETECTOR POR CÁMARA (CameraX)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (isCameraActive) SafetyOrange.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCameraActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = if (isCameraActive) SafetyOrange else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Detección Visual por Cámara",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isCameraActive) "CameraX analizando luminancia en vivo" else "Detector de cámara inactivo",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCameraActive) SecureGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isCameraActive,
                        onCheckedChange = { activate ->
                            if (activate) {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    viewModel.setCameraMonitoring(true)
                                    viewModel.alarmEngine.cameraDetector.startCamera(lifecycleOwner, previewViewRef)
                                }
                            } else {
                                viewModel.setCameraMonitoring(false)
                                viewModel.alarmEngine.cameraDetector.stopCamera()
                            }
                        },
                        modifier = Modifier.testTag("camera_switch")
                    )
                }

                if (!hasCameraPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Se requiere permiso de cámara para el análisis visual.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Conceder")
                            }
                        }
                    }
                }

                // Vista previa de CameraX cuando está activa
                if (isCameraActive && hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).also { pv ->
                                    previewViewRef = pv
                                    viewModel.alarmEngine.cameraDetector.startCamera(lifecycleOwner, pv)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Indicador de análisis activo
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AlarmRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "EN VIVO • ANÁLISIS ACTIVO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Barra de nivel de movimiento detectado en tiempo real
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cambio de Píxeles / Movimiento:", style = MaterialTheme.typography.bodySmall)
                            Text("${motionScore.toInt()}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = if (motionScore > 8f) AlarmRed else SecureGreen)
                        }
                        LinearProgressIndicator(
                            progress = { (motionScore / 50f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (motionScore > 8f) AlarmRed else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Control de Sensibilidad
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sensibilidad de Detección:", style = MaterialTheme.typography.bodySmall)
                            Text("${cameraSensitivity.toInt()}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = cameraSensitivity,
                            onValueChange = {
                                cameraSensitivity = it
                                viewModel.setCameraSensitivity(it.toInt())
                            },
                            valueRange = 10f..90f,
                            steps = 8
                        )
                    }
                }

                // Reglas de Privacidad y Limitaciones de Cámara
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Privacidad y Limitaciones Técnicas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "• Privacidad: El procesamiento se realiza 100% en la memoria RAM del equipo. No se graban videos ni se suben imágenes a la nube.\n" +
                                    "• Limitaciones: La cámara puede arrojar falsos positivos por sombras bruscas, encendido de luces o movimiento del soporte, y puede no detectar intrusos en oscuridad total. Es un sensor complementario y no sustituye un sensor PIR físico.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // SECCIÓN 2: MATRIZ DE CAPACIDADES REALES DE ANDROID
        Text(
            text = "Capacidades de Hardware Detectadas",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        capabilities.forEach { cap ->
            CapabilityItemCard(cap = cap)
        }
    }
}

@Composable
fun CapabilityItemCard(cap: com.example.service.DeviceCapability) {
    val (statusColor, statusBg) = when (cap.status) {
        CapabilityStatus.DISPONIBLE -> SecureGreen to SecureGreen.copy(alpha = 0.15f)
        CapabilityStatus.NO_DISPONIBLE -> Color.Gray to Color.Gray.copy(alpha = 0.15f)
        CapabilityStatus.PERMISO_PENDIENTE -> WarningAmber to WarningAmber.copy(alpha = 0.15f)
        CapabilityStatus.PERMISO_DENEGADO -> AlarmRed to AlarmRed.copy(alpha = 0.15f)
        CapabilityStatus.EXTERNO_REQUERIDO -> SafetyOrange to SafetyOrange.copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(statusBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cap.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cap.status.label,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = cap.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (cap.detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• ${cap.detail}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
