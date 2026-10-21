package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRecord
import com.example.data.model.DeviceRole
import com.example.data.model.TransportType
import com.example.ui.AlarmViewModel
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.WarningAmber

@Composable
fun DevicesScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val myRole by viewModel.myRole.collectAsState()
    val myDeviceName by viewModel.myDeviceName.collectAsState()
    val authorizedDevices by viewModel.authorizedDevices.collectAsState()
    val activePairingCode by viewModel.activePairingCode.collectAsState()

    val connectionState by viewModel.alarmEngine.connectionState.collectAsState()
    val activeTransport by viewModel.alarmEngine.activeTransportType.collectAsState()
    val tcpIp by viewModel.tcpIp.collectAsState()
    val tcpPort by viewModel.tcpPort.collectAsState()
    val isServerMode by viewModel.isTcpServerMode.collectAsState()

    var showPairingModal by remember { mutableStateOf(false) }
    var inputCodeToPair by remember { mutableStateOf("") }
    var selectedRoleToRequest by remember { mutableStateOf(DeviceRole.RECEPTOR_MONITOR) }

    val isConnected = connectionState == ConnectionState.CONNECTED

    // Permisos Bluetooth para Android 12+
    var hasBtPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBtPermission = results.values.all { it }
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
                    text = "Dispositivos y Red",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Sistema Multidispositivo • Celulares, Tablets y ESP32",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showPairingModal = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("pair_device_btn")
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Emparejar")
            }
        }

        // TARJETA DE ESTE DISPOSITIVO (ROL LOCAL & CAMBIO RÁPIDO CELULAR/TABLET)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (myRole == DeviceRole.EMISOR_DETECTOR) Icons.Default.PhoneAndroid else Icons.Default.Tablet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Este Equipo: $myDeviceName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Rol actual: ${myRole.label}", color = SecureGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Botón para intercambiar roles (Caso obligatorio Celular <-> Tablet)
                    OutlinedButton(
                        onClick = { viewModel.swapRoleBetweenPhoneAndTablet() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("swap_role_btn")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cambiar Rol", fontSize = 12.sp)
                    }
                }

                Text(
                    text = myRole.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Selector de roles expandido
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeviceRole.values().forEach { r ->
                        FilterChip(
                            selected = myRole == r,
                            onClick = { viewModel.setLocalDeviceRole(r) },
                            label = { Text(r.label.split("/")[0].trim(), fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Pestañas de Transporte de Conexión
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Wi-Fi TCP") }, icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dispositivos") }, icon = { Icon(Icons.Default.DeviceHub, contentDescription = null, modifier = Modifier.size(16.dp)) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Bluetooth") }, icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }

        when (selectedTab) {
            0 -> {
                // Tab Wi-Fi TCP/IP
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sockets TCP en Red Local",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isServerMode,
                                onClick = { viewModel.setTcpServerMode(false) },
                                label = { Text("Cliente (Conectar a ESP32/Tablet)") },
                                leadingIcon = if (!isServerMode) { { Icon(Icons.Default.Check, contentDescription = null) } } else null
                            )
                            FilterChip(
                                selected = isServerMode,
                                onClick = { viewModel.setTcpServerMode(true) },
                                label = { Text("Servidor (Escuchar)") },
                                leadingIcon = if (isServerMode) { { Icon(Icons.Default.Check, contentDescription = null) } } else null
                            )
                        }

                        if (!isServerMode) {
                            OutlinedTextField(
                                value = tcpIp,
                                onValueChange = { viewModel.setTcpIp(it) },
                                label = { Text("Dirección IP Remota") },
                                placeholder = { Text("192.168.4.1 o IP del otro celular") },
                                leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tcp_ip_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        OutlinedTextField(
                            value = tcpPort,
                            onValueChange = { viewModel.setTcpPort(it) },
                            label = { Text(if (isServerMode) "Puerto de Escucha" else "Puerto Remoto") },
                            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tcp_port_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isConnected && activeTransport == TransportType.TCP) {
                                Button(
                                    onClick = { viewModel.disconnect() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("tcp_disconnect_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = AlarmRed)
                                ) {
                                    Text("Desconectar Enlace TCP")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.connectTcp() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("tcp_connect_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (isServerMode) "Iniciar Servidor TCP" else "Conectar a Dispositivo TCP")
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Tab Dispositivos Autorizados
                Text(
                    text = "Dispositivos Registrados y Autorizados",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                authorizedDevices.forEach { dev ->
                    DeviceCardItem(
                        device = dev,
                        onRevoke = { viewModel.revokeDevice(dev.id) },
                        onDelete = { viewModel.deleteDevice(dev.id) }
                    )
                }
            }
            2 -> {
                // Tab Bluetooth RFCOMM
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Bluetooth Classic RFCOMM (SPP)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        if (!hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }) {
                                Text("Conceder Permisos Bluetooth")
                            }
                        }

                        val pairedDevices = if (hasBtPermission) viewModel.alarmEngine.bluetoothTransport.getPairedDevices() else emptyList()

                        if (pairedDevices.isEmpty()) {
                            Text("No se encontraron dispositivos Bluetooth emparejados.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            pairedDevices.forEach { dev ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            @Suppress("MissingPermission")
                                            val name = try { dev.name ?: "Dispositivo" } catch (_: SecurityException) { "Dispositivo" }
                                            Text(text = name, fontWeight = FontWeight.Bold)
                                            Text(text = dev.address, style = MaterialTheme.typography.bodySmall)
                                        }

                                        Button(onClick = {
                                            @Suppress("MissingPermission")
                                            val n = try { dev.name ?: "BT_DEV" } catch (_: SecurityException) { "BT_DEV" }
                                            viewModel.connectBluetooth(dev.address, n)
                                        }) {
                                            Text("Conectar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Emparejamiento Seguro con Código de 6 dígitos
    if (showPairingModal) {
        AlertDialog(
            onDismissRequest = { showPairingModal = false },
            title = { Text("Emparejamiento Seguro Multidispositivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Para autorizar la comunicación con otro celular o tablet, use uno de los dos métodos:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Opción A: Generar código para que el otro equipo lo ingrese
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Opción A: Generar código de autorización", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (activePairingCode != null) {
                                Text("Código Temporal: $activePairingCode", fontSize = 22.sp, fontWeight = FontWeight.Black, color = SecureGreen)
                                Text("Expira en 5 minutos • Máximo 3 intentos", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                            } else {
                                Button(onClick = { viewModel.generatePairingCode() }) {
                                    Text("Generar Código de 6 Dígitos")
                                }
                            }
                        }
                    }

                    // Opción B: Ingresar código que generó el otro equipo
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Opción B: Conectarse con código ajeno", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = inputCodeToPair,
                                onValueChange = { inputCodeToPair = it },
                                label = { Text("Código de 6 dígitos") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputCodeToPair.isNotBlank()) {
                            viewModel.submitPairingRequest(inputCodeToPair, selectedRoleToRequest)
                        }
                        showPairingModal = false
                    }
                ) {
                    Text("Enviar Solicitud")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairingModal = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun DeviceCardItem(
    device: DeviceRecord,
    onRevoke: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (device.kind) {
                        DeviceKind.PHONE -> Icons.Default.PhoneAndroid
                        DeviceKind.TABLET -> Icons.Default.Tablet
                        DeviceKind.ESP32 -> Icons.Default.Sensors
                        else -> Icons.Default.DeviceHub
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(text = "${device.kind.label} • ${device.role.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (device.isAuthorized) SecureGreen.copy(alpha = 0.15f) else AlarmRed.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (device.isAuthorized) "AUTORIZADO" else "REVOCADO",
                        color = if (device.isAuthorized) SecureGreen else AlarmRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "ID: ${device.id}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (device.batteryPercent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryFull, contentDescription = null, modifier = Modifier.size(14.dp), tint = SecureGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${device.batteryPercent}%", fontSize = 11.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.isAuthorized) {
                    TextButton(onClick = onRevoke) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = WarningAmber)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Revocar Acceso", color = WarningAmber, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
