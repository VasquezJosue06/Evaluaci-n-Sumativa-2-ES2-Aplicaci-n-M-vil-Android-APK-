package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.communication.BluetoothRfcommTransport
import com.example.communication.CommunicationTransport
import com.example.communication.DeviceTarget
import com.example.communication.SimulatedTransport
import com.example.communication.TcpTransport
import com.example.data.local.AlarmDatabase
import com.example.data.local.AlarmEventEntity
import com.example.data.local.DeviceSecurityRegistry
import com.example.data.local.KeyStoreHelper
import com.example.data.model.AlarmSchedule
import com.example.data.model.AlarmState
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceRecord
import com.example.data.model.DeviceRole
import com.example.data.model.DoorStatus
import com.example.data.model.ProtocolMessage
import com.example.data.model.ProtocolParser
import com.example.data.model.SensorStatus
import com.example.data.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class AlarmEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val database: AlarmDatabase
) {
    // Seguridad y Criptografía
    val keyStoreHelper = KeyStoreHelper(context)
    val securityRegistry = DeviceSecurityRegistry(context, keyStoreHelper)
    val capabilityDetector = CapabilityDetector(context)
    val cameraDetector = CameraMotionDetector(context)

    // Estados del sistema de alarma
    private val _alarmState = MutableStateFlow(AlarmState.DESARMADA)
    val alarmState: StateFlow<AlarmState> = _alarmState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Sensores y actuadores
    private val _pirStatus = MutableStateFlow(SensorStatus.NORMAL)
    val pirStatus: StateFlow<SensorStatus> = _pirStatus.asStateFlow()

    private val _doorStatus = MutableStateFlow(DoorStatus.CLOSED)
    val doorStatus: StateFlow<DoorStatus> = _doorStatus.asStateFlow()

    private val _cameraStatus = MutableStateFlow(SensorStatus.NORMAL)
    val cameraStatus: StateFlow<SensorStatus> = _cameraStatus.asStateFlow()

    private val _sirenActive = MutableStateFlow(false)
    val sirenActive: StateFlow<Boolean> = _sirenActive.asStateFlow()

    private val _strobeActive = MutableStateFlow(false)
    val strobeActive: StateFlow<Boolean> = _strobeActive.asStateFlow()

    // Dispositivo local
    val myDeviceRole: StateFlow<DeviceRole> = MutableStateFlow(securityRegistry.getMyDeviceRole()).asStateFlow()

    // Horarios
    private val _schedule = MutableStateFlow(AlarmSchedule())
    val schedule: StateFlow<AlarmSchedule> = _schedule.asStateFlow()

    // Telemetría adicional
    private val _lastCommunicationTime = MutableStateFlow<String?>(null)
    val lastCommunicationTime: StateFlow<String?> = _lastCommunicationTime.asStateFlow()

    private val _lastArmedStateBeforeDisconnect = MutableStateFlow<AlarmState?>(null)
    val lastArmedStateBeforeDisconnect: StateFlow<AlarmState?> = _lastArmedStateBeforeDisconnect.asStateFlow()

    private val _activeTransportType = MutableStateFlow(TransportType.TCP)
    val activeTransportType: StateFlow<TransportType> = _activeTransportType.asStateFlow()

    private val _lastCommandFeedback = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val lastCommandFeedback: SharedFlow<String> = _lastCommandFeedback.asSharedFlow()

    private val _pendingCommandId = MutableStateFlow<String?>(null)
    val pendingCommandId: StateFlow<String?> = _pendingCommandId.asStateFlow()

    // Transportes
    val tcpTransport = TcpTransport(scope)
    val bluetoothTransport = BluetoothRfcommTransport(context, scope)
    val simulatedTransport = SimulatedTransport(scope)

    private var activeTransport: CommunicationTransport = tcpTransport
    private var transportCollectorJob: Job? = null
    private var scheduleEvaluatorJob: Job? = null
    private var soundPool: SoundPool? = null
    private var sirenSoundId: Int = 0

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        bindTransport(tcpTransport)
        startScheduleEvaluator()
        initCameraCallback()
        initAudioAlerts()
    }

    private fun initAudioAlerts() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        } catch (_: Throwable) {
            soundPool = null
        }
    }

    private fun initCameraCallback() {
        cameraDetector.setOnMotionDetectedListener { confidence ->
            scope.launch(Dispatchers.Default) {
                handleCameraMotionDetected(confidence)
            }
        }
    }

    private suspend fun handleCameraMotionDetected(confidence: Float) {
        _cameraStatus.value = SensorStatus.MOTION_DETECTED
        val isArmed = _alarmState.value == AlarmState.ARMADA || _alarmState.value == AlarmState.ALERTA

        if (isArmed) {
            _alarmState.value = AlarmState.ALERTA
            _sirenActive.value = true
            _strobeActive.value = true
            triggerAlertHaptics()
        }

        // Registrar evento
        val eventId = UUID.randomUUID().toString()
        val isConnected = activeTransport.isConnected()

        recordEvent(
            eventType = if (isArmed) "INTRUSION_CAMERA" else "SENSOR_CAMERA_ACTIVITY",
            details = if (isArmed) "¡INTRUSIÓN! Cámara detectó movimiento visual significativo (confianza: ${(confidence * 100).toInt()}%)"
            else "Movimiento detectado por cámara (alarma desarmada)",
            sensor = "CAMERA_MOTION",
            eventId = eventId,
            confidence = confidence,
            deliveryState = if (isConnected) "ENTREGADO" else "PENDIENTE_SYNC"
        )

        // Transmitir evento a receptores autorizados (ej: tablet) si está conectado
        if (isConnected) {
            val eventMsg = ProtocolMessage.SensorEvent(
                deviceId = securityRegistry.getMyDeviceId(),
                sequence = System.currentTimeMillis() % 100000,
                sensor = "CAMERA_MOTION",
                value = "MOTION_DETECTED",
                armed = isArmed,
                eventId = eventId,
                confidence = confidence
            )
            activeTransport.send(eventMsg.toJsonString())
        }

        // Restablecer indicador tras 3.5 segundos
        delay(3500)
        _cameraStatus.value = SensorStatus.NORMAL
    }

    fun selectTransport(type: TransportType) {
        scope.launch {
            activeTransport.disconnect()
            _activeTransportType.value = type
            val newTransport = when (type) {
                TransportType.TCP -> tcpTransport
                TransportType.BLUETOOTH_RFCOMM -> bluetoothTransport
                TransportType.SIMULATED -> simulatedTransport
            }
            bindTransport(newTransport)
        }
    }

    private fun bindTransport(transport: CommunicationTransport) {
        transportCollectorJob?.cancel()
        activeTransport = transport

        transportCollectorJob = scope.launch(Dispatchers.IO) {
            launch {
                transport.connectionState.collect { cState ->
                    _connectionState.value = cState
                    if (cState == ConnectionState.DISCONNECTED || cState == ConnectionState.ERROR) {
                        if (_alarmState.value == AlarmState.ARMADA || _alarmState.value == AlarmState.ALERTA) {
                            _lastArmedStateBeforeDisconnect.value = _alarmState.value
                        }
                        _alarmState.value = AlarmState.SIN_CONEXION

                        recordEvent(
                            eventType = "CONNECTION_LOST",
                            details = "Enlace interrumpido (${transport.transportType.label}). El detector continuará encolando eventos localmente.",
                            sensor = null
                        )
                    } else if (cState == ConnectionState.CONNECTED) {
                        recordEvent(
                            eventType = "CONNECTION_ESTABLISHED",
                            details = "Enlace activo con ${transport.transportType.label}. Iniciando sincronización de eventos pendientes.",
                            sensor = null
                        )
                        // Sincronizar cola offline de eventos pendientes
                        synchronizePendingEvents()
                        // Solicitar estado inmediato
                        requestStatus()
                    }
                }
            }

            launch {
                transport.incomingMessages.collect { rawMsg ->
                    processIncomingMessage(rawMsg)
                }
            }
        }
    }

    private suspend fun processIncomingMessage(rawMsg: String) {
        val parsed = ProtocolParser.parse(rawMsg) ?: return
        val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        _lastCommunicationTime.value = nowFormatted

        when (parsed) {
            is ProtocolMessage.DeviceStatus -> {
                _pirStatus.value = when (parsed.pir) {
                    "MOTION_DETECTED" -> SensorStatus.MOTION_DETECTED
                    "FAULT" -> SensorStatus.FAULT
                    else -> SensorStatus.NORMAL
                }
                _doorStatus.value = when (parsed.door) {
                    "OPEN" -> DoorStatus.OPEN
                    "FAULT" -> DoorStatus.FAULT
                    else -> DoorStatus.CLOSED
                }
                _sirenActive.value = parsed.siren
                _strobeActive.value = parsed.strobe

                val parsedState = when (parsed.alarmState) {
                    "ARMADA" -> AlarmState.ARMADA
                    "ALERTA" -> AlarmState.ALERTA
                    "FALLO_SENSOR" -> AlarmState.FALLO_SENSOR
                    else -> AlarmState.DESARMADA
                }
                _alarmState.value = parsedState

                if (parsedState == AlarmState.ALERTA) {
                    triggerAlertHaptics()
                }
            }
            is ProtocolMessage.SensorEvent -> {
                val isSim = activeTransport.transportType == TransportType.SIMULATED
                when (parsed.sensor) {
                    "PIR" -> {
                        if (parsed.value == "MOTION_DETECTED") {
                            _pirStatus.value = SensorStatus.MOTION_DETECTED
                            if (parsed.armed) {
                                _alarmState.value = AlarmState.ALERTA
                                _sirenActive.value = true
                                _strobeActive.value = true
                                triggerAlertHaptics()
                            }
                            recordEvent(
                                eventType = if (parsed.armed) "INTRUSION_PIR" else "SENSOR_PIR_ACTIVITY",
                                details = if (parsed.armed) "¡INTRUSIÓN! Sensor PIR detectó movimiento en área protegida" else "Movimiento detectado (alarma desarmada)",
                                sensor = "PIR",
                                isSim = isSim,
                                eventId = parsed.eventId,
                                confidence = parsed.confidence
                            )
                        } else {
                            _pirStatus.value = SensorStatus.NORMAL
                        }
                    }
                    "REED_DOOR" -> {
                        val isOpen = parsed.value == "DOOR_OPEN"
                        _doorStatus.value = if (isOpen) DoorStatus.OPEN else DoorStatus.CLOSED
                        if (isOpen && parsed.armed) {
                            _alarmState.value = AlarmState.ALERTA
                            _sirenActive.value = true
                            _strobeActive.value = true
                            triggerAlertHaptics()
                        }
                        recordEvent(
                            eventType = if (isOpen && parsed.armed) "INTRUSION_DOOR" else "SENSOR_DOOR_ACTIVITY",
                            details = if (isOpen) "Puerta abierta detectada por switch magnético" else "Puerta cerrada",
                            sensor = "REED_DOOR",
                            isSim = isSim,
                            eventId = parsed.eventId,
                            confidence = parsed.confidence
                        )
                    }
                    "CAMERA_MOTION" -> {
                        _cameraStatus.value = SensorStatus.MOTION_DETECTED
                        if (parsed.armed) {
                            _alarmState.value = AlarmState.ALERTA
                            _sirenActive.value = true
                            _strobeActive.value = true
                            triggerAlertHaptics()
                        }
                        recordEvent(
                            eventType = if (parsed.armed) "INTRUSION_CAMERA" else "SENSOR_CAMERA_ACTIVITY",
                            details = "Alerta remota recibida de cámara en dispositivo [${parsed.deviceId}] (confianza: ${(parsed.confidence * 100).toInt()}%)",
                            sensor = "CAMERA_MOTION",
                            isSim = isSim,
                            eventId = parsed.eventId,
                            confidence = parsed.confidence
                        )
                    }
                }
            }
            is ProtocolMessage.Command -> {
                // Control de acceso y roles: Validar autorización
                handleReceivedCommand(parsed)
            }
            is ProtocolMessage.CommandResult -> {
                _pendingCommandId.value = null
                val resultText = if (parsed.success) "Confirmado: ${parsed.message}" else "Rechazado: ${parsed.message}"
                _lastCommandFeedback.emit(resultText)

                recordEvent(
                    eventType = if (parsed.success) "COMMAND_ACK" else "COMMAND_REJECTED",
                    details = "Respuesta del controlador [${parsed.action}]: ${parsed.message}",
                    sensor = null
                )
            }
            is ProtocolMessage.PairingRequest -> {
                handlePairingRequest(parsed)
            }
            is ProtocolMessage.PairingResponse -> {
                _lastCommandFeedback.emit("Respuesta de emparejamiento: ${parsed.message}")
            }
            is ProtocolMessage.SyncPendingEvents -> {
                handleSyncPendingEvents(parsed)
            }
            is ProtocolMessage.SyncAck -> {
                parsed.acknowledgedEventIds.forEach { eid ->
                    database.alarmEventDao().markEventAsSynced(eid)
                }
                _lastCommandFeedback.emit("Sincronización confirmada: ${parsed.acknowledgedEventIds.size} eventos actualizados.")
            }
            else -> {}
        }
    }

    private suspend fun handleReceivedCommand(cmd: ProtocolMessage.Command) {
        // Regla de seguridad: Si el rol es monitor de solo lectura, rechazar
        if (cmd.senderRole == DeviceRole.MONITOR_SOLO_LECTURA.name) {
            val res = ProtocolMessage.CommandResult(
                commandId = cmd.commandId,
                success = false,
                message = "Rechazado: Dispositivos en rol Monitor de Solo Lectura no pueden modificar el estado de alarma",
                action = cmd.action
            )
            activeTransport.send(res.toJsonString())
            return
        }

        when (cmd.action) {
            "ARM" -> {
                _alarmState.value = AlarmState.ARMADA
                _sirenActive.value = false
                _strobeActive.value = false
                val res = ProtocolMessage.CommandResult(cmd.commandId, true, "Sistema ARMADO por comando remoto", cmd.action)
                activeTransport.send(res.toJsonString())
            }
            "DISARM" -> {
                _alarmState.value = AlarmState.DESARMADA
                _sirenActive.value = false
                _strobeActive.value = false
                val res = ProtocolMessage.CommandResult(cmd.commandId, true, "Sistema DESARMADO por comando remoto", cmd.action)
                activeTransport.send(res.toJsonString())
            }
            "SILENCE_SIREN" -> {
                _sirenActive.value = false
                val res = ProtocolMessage.CommandResult(cmd.commandId, true, "Sirena silenciada. Protección activa.", cmd.action)
                activeTransport.send(res.toJsonString())
            }
            "TEST_ALARM" -> {
                _sirenActive.value = true
                _strobeActive.value = true
                triggerAlertHaptics()
                val res = ProtocolMessage.CommandResult(cmd.commandId, true, "Prueba de alarma activada", cmd.action)
                activeTransport.send(res.toJsonString())
                scope.launch {
                    delay(3000)
                    if (_alarmState.value != AlarmState.ALERTA) {
                        _sirenActive.value = false
                        _strobeActive.value = false
                    }
                }
            }
            "GET_STATUS" -> {
                val status = ProtocolMessage.DeviceStatus(
                    deviceId = securityRegistry.getMyDeviceId(),
                    pir = _pirStatus.value.name,
                    door = _doorStatus.value.name,
                    alarmState = _alarmState.value.name,
                    siren = _sirenActive.value,
                    strobe = _strobeActive.value,
                    role = securityRegistry.getMyDeviceRole().name,
                    batteryPercent = capabilityDetector.getBatteryLevel()
                )
                activeTransport.send(status.toJsonString())
            }
        }
    }

    private suspend fun handlePairingRequest(req: ProtocolMessage.PairingRequest) {
        val isValidCode = securityRegistry.validatePairingCode(req.pairingCode)
        if (isValidCode) {
            val newDevice = DeviceRecord(
                id = req.deviceId,
                name = req.deviceName,
                kind = com.example.data.model.DeviceKind.PHONE,
                role = try { DeviceRole.valueOf(req.proposedRole) } catch (_: Exception) { DeviceRole.RECEPTOR_MONITOR },
                isAuthorized = true
            )
            securityRegistry.saveAuthorizedDevice(newDevice)

            val token = keyStoreHelper.encrypt("auth_token_${req.deviceId}_${System.currentTimeMillis()}")
            val res = ProtocolMessage.PairingResponse(
                deviceId = securityRegistry.getMyDeviceId(),
                success = true,
                message = "Emparejamiento autorizado exitosamente",
                assignedRole = newDevice.role.name,
                authToken = token
            )
            activeTransport.send(res.toJsonString())
            _lastCommandFeedback.emit("Nuevo dispositivo emparejado y autorizado: ${req.deviceName}")
        } else {
            val res = ProtocolMessage.PairingResponse(
                deviceId = securityRegistry.getMyDeviceId(),
                success = false,
                message = "Código de emparejamiento inválido o expirado",
                assignedRole = DeviceRole.MONITOR_SOLO_LECTURA.name
            )
            activeTransport.send(res.toJsonString())
            _lastCommandFeedback.emit("Intento de emparejamiento rechazado para: ${req.deviceName}")
        }
    }

    private suspend fun handleSyncPendingEvents(msg: ProtocolMessage.SyncPendingEvents) {
        val ackIds = mutableListOf<String>()
        try {
            val arr = JSONArray(msg.eventsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val eid = obj.getString("eventId")
                val exists = database.alarmEventDao().findEventByEventId(eid)
                if (exists == null) {
                    val entity = AlarmEventEntity(
                        eventId = eid,
                        timestamp = obj.getLong("timestamp"),
                        deviceId = obj.getString("deviceId"),
                        eventType = obj.getString("eventType"),
                        sensor = if (obj.has("sensor") && !obj.isNull("sensor")) obj.getString("sensor") else null,
                        details = obj.getString("details"),
                        alarmState = obj.getString("alarmState"),
                        sirenActive = obj.getBoolean("sirenActive"),
                        strobeActive = obj.getBoolean("strobeActive"),
                        isSimulated = obj.optBoolean("isSimulated", false),
                        priority = obj.optString("priority", "MEDIA"),
                        deliveryState = "ENTREGADO",
                        confidence = obj.optDouble("confidence", 1.0).toFloat(),
                        isAcknowledged = true
                    )
                    database.alarmEventDao().insertEvent(entity)
                }
                ackIds.add(eid)
            }
            // Enviar confirmación ACK de eventos sincronizados
            val ackMsg = ProtocolMessage.SyncAck(
                deviceId = securityRegistry.getMyDeviceId(),
                acknowledgedEventIds = ackIds
            )
            activeTransport.send(ackMsg.toJsonString())
        } catch (_: Exception) {}
    }

    private suspend fun synchronizePendingEvents() {
        val pendingList = database.alarmEventDao().getPendingSyncEvents()
        if (pendingList.isNotEmpty()) {
            val arr = JSONArray()
            pendingList.forEach { ev ->
                val obj = JSONObject()
                obj.put("eventId", ev.eventId)
                obj.put("timestamp", ev.timestamp)
                obj.put("deviceId", ev.deviceId)
                obj.put("eventType", ev.eventType)
                ev.sensor?.let { obj.put("sensor", it) }
                obj.put("details", ev.details)
                obj.put("alarmState", ev.alarmState)
                obj.put("sirenActive", ev.sirenActive)
                obj.put("strobeActive", ev.strobeActive)
                obj.put("isSimulated", ev.isSimulated)
                obj.put("priority", ev.priority)
                obj.put("confidence", ev.confidence.toDouble())
                arr.put(obj)
            }
            val syncMsg = ProtocolMessage.SyncPendingEvents(
                deviceId = securityRegistry.getMyDeviceId(),
                eventsJson = arr.toString()
            )
            activeTransport.send(syncMsg.toJsonString())
        }
    }

    private fun triggerAlertHaptics() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (_: Exception) {}
    }

    suspend fun connect(target: DeviceTarget): Result<Unit> = activeTransport.connect(target)

    suspend fun disconnect() = activeTransport.disconnect()

    suspend fun sendArmCommand(): Boolean {
        if (!securityRegistry.getMyDeviceRole().canSendCommands) {
            _lastCommandFeedback.emit("Acción denegada: Su rol es Monitor de Solo Lectura")
            return false
        }
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Error: Dispositivo no conectado")
            return false
        }
        val cmdId = "cmd_arm_${System.currentTimeMillis()}"
        _pendingCommandId.value = cmdId
        val cmd = ProtocolMessage.Command(
            commandId = cmdId,
            action = "ARM",
            senderRole = securityRegistry.getMyDeviceRole().name
        )
        recordEvent("COMMAND_SENT", "Enviando comando de ARMADO al sistema", sensor = null)
        val res = activeTransport.send(cmd.toJsonString())
        return res.isSuccess
    }

    suspend fun sendDisarmCommand(): Boolean {
        if (!securityRegistry.getMyDeviceRole().canSendCommands) {
            _lastCommandFeedback.emit("Acción denegada: Su rol es Monitor de Solo Lectura")
            return false
        }
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Error: Dispositivo no conectado")
            return false
        }
        val cmdId = "cmd_disarm_${System.currentTimeMillis()}"
        _pendingCommandId.value = cmdId
        val cmd = ProtocolMessage.Command(
            commandId = cmdId,
            action = "DISARM",
            senderRole = securityRegistry.getMyDeviceRole().name
        )
        recordEvent("COMMAND_SENT", "Enviando comando de DESARMADO al sistema", sensor = null)
        val res = activeTransport.send(cmd.toJsonString())
        return res.isSuccess
    }

    suspend fun sendSilenceSirenCommand(): Boolean {
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Error: Dispositivo no conectado")
            return false
        }
        val cmdId = "cmd_silence_${System.currentTimeMillis()}"
        _pendingCommandId.value = cmdId
        val cmd = ProtocolMessage.Command(
            commandId = cmdId,
            action = "SILENCE_SIREN",
            senderRole = securityRegistry.getMyDeviceRole().name
        )
        recordEvent("COMMAND_SENT", "Enviando comando para silenciar sirena", sensor = null)
        val res = activeTransport.send(cmd.toJsonString())
        return res.isSuccess
    }

    suspend fun sendTestAlarmCommand(): Boolean {
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Error: Dispositivo no conectado")
            return false
        }
        val cmdId = "cmd_test_${System.currentTimeMillis()}"
        _pendingCommandId.value = cmdId
        val cmd = ProtocolMessage.Command(
            commandId = cmdId,
            action = "TEST_ALARM",
            senderRole = securityRegistry.getMyDeviceRole().name
        )
        val res = activeTransport.send(cmd.toJsonString())
        return res.isSuccess
    }

    suspend fun sendPairingRequest(pairingCode: String, proposedRole: DeviceRole): Boolean {
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Error: Conecte primero el enlace TCP con el dispositivo anfitrión")
            return false
        }
        val req = ProtocolMessage.PairingRequest(
            deviceId = securityRegistry.getMyDeviceId(),
            deviceName = securityRegistry.getMyDeviceName(),
            proposedRole = proposedRole.name,
            pairingCode = pairingCode
        )
        val res = activeTransport.send(req.toJsonString())
        return res.isSuccess
    }

    suspend fun syncScheduleWithController(schedule: AlarmSchedule): Boolean {
        _schedule.value = schedule
        if (!activeTransport.isConnected()) {
            _lastCommandFeedback.emit("Horario guardado localmente (sin conexión con ESP32)")
            return false
        }
        val cmdId = "cmd_sync_sched_${System.currentTimeMillis()}"
        _pendingCommandId.value = cmdId
        val params = mapOf(
            "startHour" to schedule.startHour,
            "startMinute" to schedule.startMinute,
            "endHour" to schedule.endHour,
            "endMinute" to schedule.endMinute,
            "enabled" to schedule.enabled,
            "timezone" to schedule.timezone
        )
        val cmd = ProtocolMessage.Command(
            commandId = cmdId,
            action = "SYNC_SCHEDULE",
            params = params
        )
        recordEvent("COMMAND_SENT", "Sincronizando horario ${schedule.formattedTimeRange()} con ESP32", sensor = null)
        val res = activeTransport.send(cmd.toJsonString())
        return res.isSuccess
    }

    private suspend fun requestStatus() {
        val cmd = ProtocolMessage.Command(
            commandId = "cmd_status_${System.currentTimeMillis()}",
            action = "GET_STATUS"
        )
        activeTransport.send(cmd.toJsonString())
    }

    private fun startScheduleEvaluator() {
        scheduleEvaluatorJob?.cancel()
        scheduleEvaluatorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30000)
                val sched = _schedule.value
                if (sched.enabled && !sched.manualOverride) {
                    val shouldBeArmed = sched.isTimeInProtectionWindow()
                    if (shouldBeArmed && _alarmState.value == AlarmState.DESARMADA && activeTransport.isConnected()) {
                        sendArmCommand()
                    } else if (!shouldBeArmed && _alarmState.value == AlarmState.ARMADA && activeTransport.isConnected()) {
                        sendDisarmCommand()
                    }
                }
            }
        }
    }

    suspend fun recordEvent(
        eventType: String,
        details: String,
        sensor: String?,
        isSim: Boolean = activeTransport.transportType == TransportType.SIMULATED,
        eventId: String = UUID.randomUUID().toString(),
        confidence: Float = 1.0f,
        deliveryState: String = "ENTREGADO"
    ) {
        val priority = when {
            eventType.contains("INTRUSION") || eventType.contains("ALARM") -> "CRITICA"
            eventType.contains("LOST") || eventType.contains("FAULT") -> "ALTA"
            eventType.contains("COMMAND") -> "MEDIA"
            else -> "INFO"
        }

        val entity = AlarmEventEntity(
            eventId = eventId,
            deviceId = if (isSim) "ESP32_SIMULADOR" else securityRegistry.getMyDeviceId(),
            eventType = eventType,
            sensor = sensor,
            details = details,
            alarmState = _alarmState.value.name,
            sirenActive = _sirenActive.value,
            strobeActive = _strobeActive.value,
            isSimulated = isSim,
            priority = priority,
            deliveryState = deliveryState,
            confidence = confidence
        )
        database.alarmEventDao().insertEvent(entity)
    }
}
