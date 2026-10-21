package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AlarmViewModel
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val currentUsername by viewModel.currentUsername.collectAsState()

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordChangeStatus by remember { mutableStateOf<String?>(null) }

    // Estado de Permisos
    val hasWifiPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    val hasVibratePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
    val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Actualización reactiva por recomposición
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ajustes del Sistema y Seguridad",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Acceso al Asistente Guiado y Keystore
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Asistente de Configuración", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Vuelva a ejecutar el asistente de 9 pasos para renombrar, emparejar o redefinir roles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { viewModel.setShowSetupWizard(true) }) {
                    Text("Iniciar Guía")
                }
            }
        }

        // Sesión de Usuario
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Operador Activo: $currentUsername",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Sesión autenticada con clave hash PBKDF2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPasswordChangeDialog = !showPasswordChangeDialog },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar Clave")
                    }

                    Button(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AlarmRed)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cerrar Sesión")
                    }
                }

                if (showPasswordChangeDialog) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text("Contraseña Actual") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nueva Contraseña (mín 6 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newPassword.length < 6) {
                                passwordChangeStatus = "La nueva contraseña debe tener al menos 6 caracteres"
                                return@Button
                            }
                            if (viewModel.changePassword(oldPassword, newPassword)) {
                                passwordChangeStatus = "Contraseña cambiada exitosamente"
                                oldPassword = ""
                                newPassword = ""
                            } else {
                                passwordChangeStatus = "Contraseña actual incorrecta"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar Nueva Clave")
                    }
                    if (passwordChangeStatus != null) {
                        Text(
                            text = passwordChangeStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (passwordChangeStatus?.contains("éxito") == true) SecureGreen else AlarmRed
                        )
                    }
                }
            }
        }

        // Gestión de Permisos Android
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Permisos del Sistema Android",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                PermissionStatusItem(
                    title = "Acceso a Red e Internet (TCP/IP)",
                    description = "Necesario para abrir sockets locales con el ESP32 o PC",
                    isGranted = hasWifiPermission
                )

                PermissionStatusItem(
                    title = "Vibración y Alertas Hápticas",
                    description = "Retroalimentación táctil durante alertas de intrusión",
                    isGranted = hasVibratePermission
                )

                PermissionStatusItem(
                    title = "Bluetooth y Dispositivos Cercanos",
                    description = "Necesario para el transporte opcional RFCOMM (SPP)",
                    isGranted = hasBluetoothPermission,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Abrir Ajustes de la Aplicación en Android")
                }
            }
        }

        // Auditoría de Seguridad IoT & ISO 27400
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Policy, contentDescription = null, tint = SafetyOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Análisis de Seguridad IoT (ISO 27400)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "• Limitación de TCP Plano: La conexión Wi-Fi local mediante sockets TCP sin cifrar carece de cifrado de capa de transporte (TLS). En redes abiertas o no confiables, el tráfico puede ser interceptado o manipulado mediante ataques Man-in-the-Middle (MitM) o spoofing.\n\n" +
                            "• Recomendaciones para Instalación OT Real:\n" +
                            "  1. Aislar los dispositivos en una VLAN dedicada de automatización/seguridad.\n" +
                            "  2. Proteger la red inalámbrica con WPA3-Enterprise / 802.1X.\n" +
                            "  3. Utilizar un túnel cifrado como WireGuard o IPsec entre el dispositivo móvil y la red perimetral.\n" +
                            "  4. Implementar encapsulación TLS/mTLS en futuras iteraciones del firmware ESP32.\n\n" +
                            "• Principio de Autonomía de Borde: El ESP32 conserva las reglas esenciales de disparo físico en hardware, de modo que una degradación o pérdida de conectividad no inhabilita la sirena.\n\n" +
                            "• Descargo Académico: Esta aplicación se diseñó siguiendo directrices metodológicas de seguridad IoT, pero NO cuenta con certificación formal por parte de un organismo acreditador ISO 27400.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Información de la Asignatura
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Alarma Perimetral IoT v1.0",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Asignatura: Aplicaciones Móviles para IoT (TI3042)\n" +
                            "Arquitectura: Android MVVM + Jetpack Compose + Room + Sockets TCP/IP + Bluetooth Classic RFCOMM + ESP32 C++\n" +
                            "Paquete: com.iotmonitor.alarma",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionStatusItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Concedido", tint = SecureGreen)
        } else if (onRequest != null) {
            TextButton(onClick = onRequest) {
                Text("Solicitar")
            }
        } else {
            Icon(Icons.Default.Warning, contentDescription = "No concedido", tint = WarningAmber)
        }
    }
}
