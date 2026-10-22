package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.communication.DiagnosticItem
import com.example.communication.DiscoveredDevice
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRole
import com.example.data.model.TransportType
import com.example.ui.AlarmViewModel
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.WarningAmber

@Composable
fun ConnectDevicesScreen(
    viewModel: AlarmViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val connectionState by viewModel.alarmEngine.connectionState.collectAsState()
    val activeTransport by viewModel.alarmEngine.activeTransportType.collectAsState()
    val selectedTransportPref by viewModel.selectedTransportPreference.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val isSearching by viewModel.isSearchingNsd.collectAsState()
    val discoveredDevices by viewModel.discoveredNsdDevices.collectAsState()
    val myDeviceName by viewModel.myDeviceName.collectAsState()
    val myRole by viewModel.myRole.collectAsState()
    val activePairingCode by viewModel.activePairingCode.collectAsState()
    val showDiagnosticModal by viewModel.showDiagnosticModal.collectAsState()
    val diagnosticResult by viewModel.diagnosticResult.collectAsState()

    val localIp = viewModel.getLocalIpAddress()
    val tcpPort by viewModel.tcpPort.collectAsState()

    var showManualSection by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf(tcpPort) }
    var manualNameInput by remember { mutableStateOf("") }

    var showPairingInputDialog by remember { mutableStateOf(false) }
    var pendingDeviceToPair by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var inputPairingCode by remember { mutableStateOf("") }

    val btSupported = viewModel.alarmEngine.bluetoothTransport.isBluetoothSupported()
    val btEnabled = viewModel.alarmEngine.bluetoothTransport.isBluetoothEnabled()

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

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBtPermission = results.values.all { it }
    }

    BackHandler {
        if (isHosting) {
            viewModel.stopHosting()
        }
        if (isSearching) {
            viewModel.stopSearching()
        }
        onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ENCABEZADO
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isHosting) viewModel.stopHosting()
                    if (isSearching) viewModel.stopSearching()
                    onNavigateBack()
                },
                modifier = Modifier.testTag("connect_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Conectar dispositivos",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Asistente rápido entre celulares, tablets y detectores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // BARRA DE ESTADO DE CONEXIÓN ACTUAL
        ConnectionStatusBar(
            connectionState = connectionState,
            activeTransport = activeTransport,
            onDisconnect = { viewModel.disconnect() },
            onRetry = {
                if (isHosting) viewModel.startHosting()
                else viewModel.startSearching()
            }
        )

        // SELECCIÓN DE MÉTODO DE TRANSPORTE
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Método de Conexión",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTransportPref == TransportType.TCP,
                        onClick = { viewModel.setTransportPreference(TransportType.TCP) },
                        label = { Text("Wi-Fi local (Recomendado)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transport_wifi_chip")
                    )

                    FilterChip(
                        selected = selectedTransportPref == TransportType.BLUETOOTH_RFCOMM,
                        onClick = {
                            if (!btSupported) {
                                // Explicar no disponibilidad
                                viewModel.runDiagnostic()
                            } else {
                                viewModel.setTransportPreference(TransportType.BLUETOOTH_RFCOMM)
                                if (!hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    btPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_ADVERTISE
                                        )
                                    )
                                }
                            }
                        },
                        label = { Text("Bluetooth RFCOMM", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("transport_bt_chip")
                    )
                }

                if (selectedTransportPref == TransportType.BLUETOOTH_RFCOMM) {
                    if (!btSupported) {
                        Text(
                            text = "⚠ Este dispositivo no cuenta con adaptador Bluetooth. Usa Wi-Fi.",
                            color = AlarmRed,
                            fontSize = 11.sp
                        )
                    } else if (!btEnabled) {
                        Text(
                            text = "ℹ El Bluetooth está apagado en este equipo. Por favor enciéndelo en los ajustes.",
                            color = WarningAmber,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "✓ Bluetooth listo para conectar con dispositivos previamente vinculados en el sistema.",
                            color = SecureGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // DOS BOTONES PRINCIPALES CLARAMENTE VISIBLES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // BOTÓN 1: CREAR CONEXIÓN (Modo Anfitrión)
            Button(
                onClick = {
                    if (isHosting) {
                        viewModel.stopHosting()
                    } else {
                        viewModel.startHosting()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("btn_create_connection"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHosting) WarningAmber else MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isHosting) Icons.Default.Cancel else Icons.Default.WifiTethering,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isHosting) "Cancelar Espera" else "Crear conexión",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isHosting) "Detener servicio" else "Recibir otro equipo",
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // BOTÓN 2: BUSCAR DISPOSITIVOS (Modo Cliente)
            Button(
                onClick = {
                    if (isSearching) {
                        viewModel.stopSearching()
                    } else {
                        viewModel.startSearching()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("btn_search_devices"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSearching) SafetyOrange else MaterialTheme.colorScheme.secondary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Cancel else Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isSearching) "Detener Búsqueda" else "Buscar dispositivos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isSearching) "Escaneando red..." else "Conectar a anfitrión",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // PANEL DE ESTADO: MODO ANFITRIÓN (ESPERANDO OTRO DISPOSITIVO)
        AnimatedVisibility(visible = isHosting) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_waiting_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Esperando otro dispositivo...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Este equipo ($myDeviceName) está anunciado en la red local y listo para conectarse.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Método:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = selectedTransportPref.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        if (localIp != null && selectedTransportPref == TransportType.TCP) {
                            Column {
                                Text(text = "Dirección IP:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "$localIp:$tcpPort", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SecureGreen)
                            }
                        }
                    }

                    // CÓDIGO TEMPORAL DE EMPAREJAMIENTO GENERADO
                    if (activePairingCode != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, SecureGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Código de Autorización Temporal (5 min):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activePairingCode ?: "------",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp,
                                    color = SecureGreen
                                )
                                Text(
                                    text = "Proporcione este código si el otro equipo lo solicita",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // INSTRUCCIONES PASO A PASO
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Pasos para conectar:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "1. En el otro celular o tablet, abre la app y pulsa 'Buscar dispositivos'.", fontSize = 11.sp)
                            Text(text = "2. Selecciona '$myDeviceName' cuando aparezca en la lista.", fontSize = 11.sp)
                            Text(text = "3. La conexión y prueba de eco se realizarán automáticamente.", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // INDICADOR DE BÚSQUEDA
        if (isSearching) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buscando dispositivos compatibles...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { viewModel.stopSearching() }) {
                        Text("Detener", fontSize = 12.sp)
                    }
                }
            }
        }

        // LISTA DE DISPOSITIVOS ENCONTRADOS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dispositivos encontrados",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (discoveredDevices.isNotEmpty() || selectedTransportPref == TransportType.BLUETOOTH_RFCOMM) {
                    IconButton(onClick = { viewModel.startSearching() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (selectedTransportPref == TransportType.TCP) {
                if (discoveredDevices.isEmpty() && !isSearching && !isHosting) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Pulsa 'Buscar dispositivos' para encontrar equipos en tu red Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    discoveredDevices.forEach { dev ->
                        DiscoveredDeviceCard(
                            device = dev,
                            connectionState = connectionState,
                            onConnect = {
                                viewModel.connectToDiscoveredDevice(dev)
                            }
                        )
                    }
                }
            } else {
                // BLUETOOTH RFCOMM: Mostrar dispositivos emparejados en el sistema
                val pairedBtDevices = if (hasBtPermission) viewModel.alarmEngine.bluetoothTransport.getPairedDevices() else emptyList()
                if (pairedBtDevices.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No hay dispositivos Bluetooth vinculados en Android",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Vincula primero el otro celular o tablet desde los Ajustes de Bluetooth del sistema.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    pairedBtDevices.forEach { btd ->
                        @Suppress("MissingPermission")
                        val devName = try { btd.name ?: "Dispositivo Bluetooth" } catch (_: SecurityException) { "Dispositivo Bluetooth" }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = devName, fontWeight = FontWeight.Bold)
                                        Text(text = btd.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.connectBluetooth(btd.address, devName) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Conectar", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTÓN: ¿NO APARECE TU DISPOSITIVO? -> GUÍA DE DIAGNÓSTICO
        OutlinedButton(
            onClick = { viewModel.runDiagnostic() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_diagnostic_guide"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("¿No aparece tu dispositivo? Diagnosticar conexión")
        }

        // OPCIÓN SECUNDARIA: CONECTAR MANUALMENTE (Para routers con aislamiento o IPs fijas)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualSection = !showManualSection },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Conectar manualmente (IP y Puerto)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = if (showManualSection) "Ocultar" else "Mostrar",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showManualSection) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Útil cuando el router tiene aislamiento de clientes o firewall que bloquea el descubrimiento automático.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            label = { Text("Dirección IP del otro equipo") },
                            placeholder = { Text("Ej: 192.168.1.50") },
                            leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_ip_input")
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualPortInput,
                                onValueChange = { manualPortInput = it },
                                label = { Text("Puerto TCP") },
                                placeholder = { Text("8888") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_port_input")
                            )

                            OutlinedTextField(
                                value = manualNameInput,
                                onValueChange = { manualNameInput = it },
                                label = { Text("Nombre (Opcional)") },
                                placeholder = { Text("Ej: Tablet Sala") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.connectManualValidated(manualIpInput, manualPortInput, manualNameInput)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_manual_connect"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Conectar por IP Directa")
                        }
                    }
                }
            }
        }
    }

    // DIÁLOGO MODAL DE DIAGNÓSTICO INTERACTIVO
    if (showDiagnosticModal && diagnosticResult != null) {
        val diag = diagnosticResult!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDiagnostic() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Diagnóstico de Conectividad")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = diag.generalAdvice,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    diag.items.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isOk) SecureGreen.copy(alpha = 0.08f) else AlarmRed.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item.isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (item.isOk) SecureGreen else AlarmRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(text = item.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                item.recommendation?.let { rec ->
                                    Text(text = "💡 $rec", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissDiagnostic() }) {
                    Text("Entendido")
                }
            }
        )
    }

    // DIÁLOGO PARA INGRESAR CÓDIGO DE EMPAREJAMIENTO SI ES REQUERIDO
    if (connectionState == ConnectionState.REQUESTING_AUTH) {
        AlertDialog(
            onDismissRequest = { /* Requiere acción */ },
            title = { Text("Autorización de Seguridad Requerida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "El otro equipo requiere un código temporal de 6 dígitos para autorizar el emparejamiento seguro.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = inputPairingCode,
                        onValueChange = { inputPairingCode = it },
                        label = { Text("Código de 6 dígitos") },
                        placeholder = { Text("Ingresa el código que muestra el otro celular") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPairingCode.isNotBlank()) {
                            viewModel.submitPairingRequest(inputPairingCode, DeviceRole.RECEPTOR_MONITOR)
                        }
                    }
                ) {
                    Text("Autorizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.disconnect() }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    activeTransport: TransportType,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit
) {
    val (bgColor, textColor, icon) = when (connectionState) {
        ConnectionState.CONNECTED -> Triple(SecureGreen.copy(alpha = 0.15f), SecureGreen, Icons.Default.CheckCircle)
        ConnectionState.CONNECTING, ConnectionState.VERIFYING_COMMUNICATION, ConnectionState.REQUESTING_AUTH ->
            Triple(SafetyOrange.copy(alpha = 0.15f), SafetyOrange, Icons.Default.Sync)
        ConnectionState.WAITING_FOR_DEVICE, ConnectionState.PREPARING_HOST, ConnectionState.SEARCHING ->
            Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, Icons.Default.HourglassBottom)
        ConnectionState.RECONNECTING -> Triple(WarningAmber.copy(alpha = 0.15f), WarningAmber, Icons.Default.Refresh)
        ConnectionState.ERROR -> Triple(AlarmRed.copy(alpha = 0.15f), AlarmRed, Icons.Default.ErrorOutline)
        ConnectionState.DISCONNECTED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Lan)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connection_status_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = connectionState.label,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Vía: ${activeTransport.label}",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }

            if (connectionState == ConnectionState.CONNECTED) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlarmRed)
                ) {
                    Text("Desconectar", fontSize = 11.sp)
                }
            } else if (connectionState == ConnectionState.ERROR) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reintentar", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    connectionState: ConnectionState,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovered_device_${device.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (device.deviceKind) {
                    DeviceKind.TABLET -> Icons.Default.Tablet
                    DeviceKind.ESP32 -> Icons.Default.Sensors
                    else -> Icons.Default.PhoneAndroid
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${device.deviceKind.label} • ${device.hostAddress}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Protocolo v${device.protocolVersion} • Listo para enlazar",
                        fontSize = 10.sp,
                        color = SecureGreen
                    )
                }
            }

            val isConnectingThis = connectionState == ConnectionState.CONNECTING ||
                    connectionState == ConnectionState.VERIFYING_COMMUNICATION

            Button(
                onClick = onConnect,
                enabled = !isConnectingThis,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("connect_discovered_${device.id}")
            ) {
                if (isConnectingThis) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Conectar", fontSize = 12.sp)
                }
            }
        }
    }
}
