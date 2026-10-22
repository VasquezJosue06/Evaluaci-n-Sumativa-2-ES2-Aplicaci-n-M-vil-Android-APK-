package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.ConnectionDiagnosticResult
import com.example.communication.DeviceTarget
import com.example.communication.DiscoveredDevice
import com.example.data.local.AlarmDatabase
import com.example.data.local.AlarmEventEntity
import com.example.data.local.SecurityPreferences
import com.example.data.model.AlarmSchedule
import com.example.data.model.AlarmState
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRecord
import com.example.data.model.DeviceRole
import com.example.data.model.DoorStatus
import com.example.data.model.ProtocolConstants
import com.example.data.model.SensorStatus
import com.example.data.model.TransportType
import com.example.service.AlarmEngine
import com.example.service.DeviceCapability
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EventFilter(val label: String) {
    ALL("Todos"),
    REAL("Reales"),
    SIMULATED("Simulados"),
    INTRUSIONS("Intrusiones"),
    COMMANDS("Comandos"),
    PENDING_SYNC("Cola Offline")
}

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getInstance(application)
    val securityPrefs = SecurityPreferences(application)
    val alarmEngine = AlarmEngine(application, viewModelScope, database)

    // Autenticación
    private val _isAuthenticated = MutableStateFlow(securityPrefs.isSessionActive())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUsername = MutableStateFlow(securityPrefs.getStoredUsername())
    val currentUsername: StateFlow<String> = _currentUsername.asStateFlow()

    // Dispositivo y roles
    private val _myRole = MutableStateFlow(alarmEngine.securityRegistry.getMyDeviceRole())
    val myRole: StateFlow<DeviceRole> = _myRole.asStateFlow()

    private val _myDeviceName = MutableStateFlow(alarmEngine.securityRegistry.getMyDeviceName())
    val myDeviceName: StateFlow<String> = _myDeviceName.asStateFlow()

    private val _authorizedDevices = MutableStateFlow(alarmEngine.securityRegistry.getAuthorizedDevices())
    val authorizedDevices: StateFlow<List<DeviceRecord>> = _authorizedDevices.asStateFlow()

    private val _activePairingCode = MutableStateFlow(alarmEngine.securityRegistry.getActivePairingCode())
    val activePairingCode: StateFlow<String?> = _activePairingCode.asStateFlow()

    // Capacidades de hardware
    private val _deviceCapabilities = MutableStateFlow(alarmEngine.capabilityDetector.detectAllCapabilities())
    val deviceCapabilities: StateFlow<List<DeviceCapability>> = _deviceCapabilities.asStateFlow()

    // Configuración de conexión TCP
    private val _tcpIp = MutableStateFlow(ProtocolConstants.DEFAULT_ESP32_IP)
    val tcpIp: StateFlow<String> = _tcpIp.asStateFlow()

    private val _tcpPort = MutableStateFlow(ProtocolConstants.DEFAULT_TCP_PORT.toString())
    val tcpPort: StateFlow<String> = _tcpPort.asStateFlow()

    private val _isTcpServerMode = MutableStateFlow(false)
    val isTcpServerMode: StateFlow<Boolean> = _isTcpServerMode.asStateFlow()

    // Conexión Guiada y Automática (Wi-Fi y Bluetooth)
    private val _selectedTransportPreference = MutableStateFlow(TransportType.TCP)
    val selectedTransportPreference: StateFlow<TransportType> = _selectedTransportPreference.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    val discoveredNsdDevices: StateFlow<List<DiscoveredDevice>> = alarmEngine.nsdDiscoveryManager.discoveredDevices
    val isSearchingNsd: StateFlow<Boolean> = alarmEngine.nsdDiscoveryManager.isSearching
    val isNsdRegistered: StateFlow<Boolean> = alarmEngine.nsdDiscoveryManager.isRegistered

    private val _diagnosticResult = MutableStateFlow<ConnectionDiagnosticResult?>(null)
    val diagnosticResult: StateFlow<ConnectionDiagnosticResult?> = _diagnosticResult.asStateFlow()

    private val _showDiagnosticModal = MutableStateFlow(false)
    val showDiagnosticModal: StateFlow<Boolean> = _showDiagnosticModal.asStateFlow()

    // Feedback visual y alertas
    private val _userFeedback = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val userFeedback: SharedFlow<String> = _userFeedback.asSharedFlow()

    // Filtro de historial
    private val _eventFilter = MutableStateFlow(EventFilter.ALL)
    val eventFilter: StateFlow<EventFilter> = _eventFilter.asStateFlow()

    // Asistente de configuración guiada
    private val _showSetupWizard = MutableStateFlow(false)
    val showSetupWizard: StateFlow<Boolean> = _showSetupWizard.asStateFlow()

    // Historial reactivo filtrado
    val filteredEvents: StateFlow<List<AlarmEventEntity>> = combine(
        database.alarmEventDao().getAllEvents().catch { emit(emptyList()) },
        _eventFilter
    ) { all, filter ->
        when (filter) {
            EventFilter.ALL -> all
            EventFilter.REAL -> all.filter { !it.isSimulated }
            EventFilter.SIMULATED -> all.filter { it.isSimulated }
            EventFilter.INTRUSIONS -> all.filter { it.eventType.contains("INTRUSION") || it.eventType.contains("ALARM") }
            EventFilter.COMMANDS -> all.filter { it.eventType.contains("COMMAND") }
            EventFilter.PENDING_SYNC -> all.filter { it.deliveryState == "PENDIENTE_SYNC" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            alarmEngine.lastCommandFeedback.collect { msg ->
                _userFeedback.emit(msg)
            }
        }
    }

    // Autenticación
    fun login(user: String, pass: String): Boolean {
        val success = securityPrefs.authenticate(user, pass)
        if (success) {
            _isAuthenticated.value = true
            _currentUsername.value = user
        }
        return success
    }

    fun logout() {
        securityPrefs.logout()
        _isAuthenticated.value = false
    }

    fun changePassword(oldPass: String, newPass: String): Boolean {
        val user = _currentUsername.value
        if (securityPrefs.authenticate(user, oldPass)) {
            val updated = securityPrefs.registerUser(user, newPass)
            if (updated) {
                viewModelScope.launch { _userFeedback.emit("Contraseña actualizada exitosamente") }
            }
            return updated
        }
        return false
    }

    // Gestión Multidispositivo y Roles
    fun setLocalDeviceRole(role: DeviceRole) {
        alarmEngine.securityRegistry.setMyDeviceRole(role)
        _myRole.value = role
        viewModelScope.launch {
            _userFeedback.emit("Rol actualizado a: ${role.label}")
        }
    }

    fun setLocalDeviceName(name: String) {
        alarmEngine.securityRegistry.setMyDeviceName(name)
        _myDeviceName.value = name
    }

    fun generatePairingCode(): String {
        val code = alarmEngine.securityRegistry.createNewPairingCode()
        _activePairingCode.value = code
        viewModelScope.launch {
            _userFeedback.emit("Código de emparejamiento generado: $code (válido por 5 minutos)")
        }
        return code
    }

    fun submitPairingRequest(pairingCode: String, proposedRole: DeviceRole) {
        viewModelScope.launch {
            val sent = alarmEngine.sendPairingRequest(pairingCode, proposedRole)
            if (sent) {
                _userFeedback.emit("Solicitud de emparejamiento enviada...")
            } else {
                _userFeedback.emit("Error transmitiendo solicitud de emparejamiento")
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        alarmEngine.securityRegistry.revokeDevice(deviceId)
        _authorizedDevices.value = alarmEngine.securityRegistry.getAuthorizedDevices()
        viewModelScope.launch {
            _userFeedback.emit("Acceso revocado para dispositivo $deviceId")
        }
    }

    fun deleteDevice(deviceId: String) {
        alarmEngine.securityRegistry.deleteDevice(deviceId)
        _authorizedDevices.value = alarmEngine.securityRegistry.getAuthorizedDevices()
        viewModelScope.launch {
            _userFeedback.emit("Dispositivo eliminado")
        }
    }

    fun swapRoleBetweenPhoneAndTablet() {
        val current = _myRole.value
        val newRole = if (current == DeviceRole.EMISOR_DETECTOR) {
            DeviceRole.RECEPTOR_MONITOR
        } else {
            DeviceRole.EMISOR_DETECTOR
        }
        setLocalDeviceRole(newRole)
    }

    fun refreshCapabilities() {
        _deviceCapabilities.value = alarmEngine.capabilityDetector.detectAllCapabilities()
    }

    // Cámara y Análisis
    fun setCameraMonitoring(enabled: Boolean) {
        alarmEngine.cameraDetector.isEnabled = enabled
        viewModelScope.launch {
            if (enabled) {
                _userFeedback.emit("Detección visual por cámara armada")
            } else {
                _userFeedback.emit("Detección visual detenida")
            }
        }
    }

    fun setCameraSensitivity(sensitivity: Int) {
        alarmEngine.cameraDetector.sensitivityPercent = sensitivity
    }

    // Configuración de red
    fun setTcpIp(ip: String) { _tcpIp.value = ip }
    fun setTcpPort(port: String) { _tcpPort.value = port }
    fun setTcpServerMode(isServer: Boolean) { _isTcpServerMode.value = isServer }
    fun setEventFilter(filter: EventFilter) { _eventFilter.value = filter }
    fun setShowSetupWizard(show: Boolean) { _showSetupWizard.value = show }

    // Conexión
    fun connectTcp() {
        val port = _tcpPort.value.toIntOrNull() ?: ProtocolConstants.DEFAULT_TCP_PORT
        val target = if (_isTcpServerMode.value) {
            DeviceTarget.TcpServer(listenPort = port)
        } else {
            DeviceTarget.TcpClient(ipAddress = _tcpIp.value.trim(), port = port)
        }
        alarmEngine.selectTransport(TransportType.TCP)
        viewModelScope.launch {
            val res = alarmEngine.connect(target)
            if (res.isFailure) {
                _userFeedback.emit("Fallo al conectar TCP: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun connectBluetooth(macAddress: String, name: String) {
        val target = DeviceTarget.BluetoothRfcomm(deviceAddress = macAddress, deviceName = name)
        alarmEngine.selectTransport(TransportType.BLUETOOTH_RFCOMM)
        viewModelScope.launch {
            val res = alarmEngine.connect(target)
            if (res.isFailure) {
                _userFeedback.emit("Fallo al conectar Bluetooth: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun connectSimulated() {
        val target = DeviceTarget.Simulated()
        alarmEngine.selectTransport(TransportType.SIMULATED)
        viewModelScope.launch {
            val res = alarmEngine.connect(target)
            if (res.isSuccess) {
                _userFeedback.emit("ESP32 Simulado conectado correctamente")
            }
        }
    }

    fun toggleEsp32Simulation() {
        val currentTransport = alarmEngine.activeTransportType.value
        val isConnected = alarmEngine.connectionState.value == ConnectionState.CONNECTED
        if (currentTransport == TransportType.SIMULATED && isConnected) {
            disconnect()
        } else {
            connectSimulated()
        }
    }

    fun disconnect() {
        stopHosting()
        stopSearching()
        viewModelScope.launch {
            alarmEngine.disconnect()
            _userFeedback.emit("Desconectado")
        }
    }

    // Métodos del Asistente Rápido y Guiado de Conexión
    fun setTransportPreference(type: TransportType) {
        _selectedTransportPreference.value = type
    }

    fun startHosting(transportType: TransportType = _selectedTransportPreference.value) {
        _isHosting.value = true
        // Generar código de emparejamiento de seguridad para validación mutua
        generatePairingCode()

        viewModelScope.launch {
            if (transportType == TransportType.TCP) {
                val port = _tcpPort.value.toIntOrNull() ?: ProtocolConstants.DEFAULT_TCP_PORT
                alarmEngine.nsdDiscoveryManager.startRegistration(
                    deviceName = _myDeviceName.value,
                    port = port,
                    role = _myRole.value
                )
                val target = DeviceTarget.TcpServer(listenPort = port, deviceName = _myDeviceName.value)
                alarmEngine.connect(target)
            } else if (transportType == TransportType.BLUETOOTH_RFCOMM) {
                val target = DeviceTarget.BluetoothRfcommServer(deviceName = _myDeviceName.value)
                alarmEngine.connect(target)
            }
        }
    }

    fun stopHosting() {
        _isHosting.value = false
        alarmEngine.nsdDiscoveryManager.stopRegistration()
        viewModelScope.launch {
            alarmEngine.disconnect()
            _userFeedback.emit("Servicio anfitrión detenido")
        }
    }

    fun startSearching(transportType: TransportType = _selectedTransportPreference.value) {
        if (transportType == TransportType.TCP) {
            alarmEngine.nsdDiscoveryManager.startDiscovery()
        }
        viewModelScope.launch {
            _userFeedback.emit("Buscando dispositivos compatibles en la red...")
        }
    }

    fun stopSearching() {
        alarmEngine.nsdDiscoveryManager.stopDiscovery()
    }

    fun connectToDiscoveredDevice(device: DiscoveredDevice) {
        stopSearching()
        val target = DeviceTarget.TcpClient(
            ipAddress = device.hostAddress,
            port = device.port,
            deviceId = device.id,
            deviceName = device.name
        )
        viewModelScope.launch {
            _userFeedback.emit("Conectando con ${device.name} (${device.hostAddress})...")
            val res = alarmEngine.connect(target)
            if (res.isFailure) {
                _userFeedback.emit("Error de conexión: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun connectManualValidated(ip: String, portStr: String, name: String = "Dispositivo Manual") {
        val trimmedIp = ip.trim()
        val port = portStr.trim().toIntOrNull()

        if (trimmedIp.isEmpty()) {
            viewModelScope.launch { _userFeedback.emit("Por favor ingresa una dirección IP o nombre de host") }
            return
        }

        // Validador de formato IPv4
        val ipRegex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipRegex.matches(trimmedIp)) {
            val parts = trimmedIp.split(".")
            val valid = parts.all { it.toIntOrNull() in 0..255 }
            if (!valid) {
                viewModelScope.launch { _userFeedback.emit("IP inválida. Cada segmento debe estar entre 0 y 255 (ej: 192.168.1.50)") }
                return
            }
        }

        if (port == null || port !in 1..65535) {
            viewModelScope.launch { _userFeedback.emit("Puerto inválido. Debe ser un número entre 1 y 65535 (ej: 8888)") }
            return
        }

        _tcpIp.value = trimmedIp
        _tcpPort.value = port.toString()

        val target = DeviceTarget.TcpClient(
            ipAddress = trimmedIp,
            port = port,
            deviceName = if (name.isNotBlank()) name.trim() else "Dispositivo Manual"
        )
        viewModelScope.launch {
            _userFeedback.emit("Iniciando conexión con $trimmedIp:$port...")
            val res = alarmEngine.connect(target)
            if (res.isFailure) {
                _userFeedback.emit("Fallo al conectar: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun runDiagnostic() {
        val result = alarmEngine.connectionDiagnosticHelper.runFullDiagnostic()
        _diagnosticResult.value = result
        _showDiagnosticModal.value = true
    }

    fun dismissDiagnostic() {
        _showDiagnosticModal.value = false
    }

    fun getLocalIpAddress(): String? {
        return alarmEngine.nsdDiscoveryManager.getLocalIpAddress()
    }

    // Acciones de Alarma
    fun armAlarm() {
        viewModelScope.launch {
            val sent = alarmEngine.sendArmCommand()
            if (!sent) _userFeedback.emit("No se pudo transmitir comando de armado")
        }
    }

    fun disarmAlarm() {
        alarmEngine.notificationManager.stopAlarmAudio()
        viewModelScope.launch {
            val sent = alarmEngine.sendDisarmCommand()
            if (!sent) _userFeedback.emit("No se pudo transmitir comando de desarmado")
        }
    }

    fun silenceSiren() {
        alarmEngine.notificationManager.stopAlarmAudio()
        viewModelScope.launch {
            val sent = alarmEngine.sendSilenceSirenCommand()
            if (!sent) _userFeedback.emit("No se pudo transmitir comando de silenciamiento")
        }
    }

    fun testAlarm() {
        viewModelScope.launch {
            val sent = alarmEngine.sendTestAlarmCommand()
            if (!sent) _userFeedback.emit("No se pudo transmitir comando de prueba")
        }
    }

    fun saveSchedule(schedule: AlarmSchedule) {
        viewModelScope.launch {
            alarmEngine.syncScheduleWithController(schedule)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.alarmEventDao().clearAllEvents()
            _userFeedback.emit("Historial de eventos vaciado")
        }
    }

    // Simulación
    fun simTriggerPir() {
        viewModelScope.launch {
            alarmEngine.simulatedTransport.triggerSimulatedPirMotion()
        }
    }

    fun simToggleDoor() {
        viewModelScope.launch {
            alarmEngine.simulatedTransport.toggleSimulatedDoor()
        }
    }

    fun simTriggerFault() {
        viewModelScope.launch {
            alarmEngine.simulatedTransport.simulateSensorFault()
        }
    }

    fun simDisconnect() {
        viewModelScope.launch {
            alarmEngine.simulatedTransport.simulateConnectionLoss()
        }
    }

    fun simTriggerSecurityAlert() {
        viewModelScope.launch {
            alarmEngine.triggerSecurityAlert(
                title = "¡ALERTA DE SEGURIDAD: INTRUSIÓN SIMULADA!",
                details = "Intrusión detectada en bodega: Sensor PIR y switch magnético activados simultáneamente."
            )
            _userFeedback.emit("Alerta enviada con notificación, sonido y vibración")
        }
    }
}
