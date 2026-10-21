package com.example.communication

import com.example.data.model.ConnectionState
import com.example.data.model.ProtocolMessage
import com.example.data.model.ProtocolParser
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
import kotlinx.coroutines.withContext

/**
 * Transporte de demostración y simulación para evaluación en ausencia de hardware ESP32.
 * Emula el comportamiento del firmware autónomo del ESP32 con fidelidad total.
 */
class SimulatedTransport(
    private val scope: CoroutineScope
) : CommunicationTransport {

    override val transportType: TransportType = TransportType.SIMULATED

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Estado interno del controlador ESP32 virtual simulado
    private var simAlarmState: String = "DESARMADA"
    private var simPir: String = "NORMAL"
    private var simDoor: String = "CLOSED"
    private var simSiren: Boolean = false
    private var simStrobe: Boolean = false
    private var simSequence: Long = 100L
    private var simUptime: Long = 3600L

    private var telemetryJob: Job? = null

    override fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    override suspend fun connect(target: DeviceTarget): Result<Unit> = withContext(Dispatchers.Default) {
        _connectionState.value = ConnectionState.CONNECTING
        delay(600) // Simular handshake de red
        _connectionState.value = ConnectionState.CONNECTED
        _lastError.value = null

        startTelemetryLoop()

        // Enviar estado inicial inmediato
        emitStatus()
        Result.success(Unit)
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.Default) {
            while (isActive && isConnected()) {
                delay(5000) // Telemetría periódica cada 5s
                simUptime += 5
                emitStatus()
            }
        }
    }

    private suspend fun emitStatus() {
        val statusMsg = ProtocolMessage.DeviceStatus(
            deviceId = "ESP32_SIMULADO_01",
            pir = simPir,
            door = simDoor,
            alarmState = simAlarmState,
            siren = simSiren,
            strobe = simStrobe,
            uptimeSeconds = simUptime
        )
        _incomingMessages.emit(statusMsg.toJsonString())
    }

    override suspend fun send(rawJson: String): Result<Unit> = withContext(Dispatchers.Default) {
        if (!isConnected()) {
            return@withContext Result.failure(IllegalStateException("Simulador no conectado"))
        }

        val parsed = ProtocolParser.parse(rawJson)
        if (parsed is ProtocolMessage.Command) {
            handleSimulatedCommand(parsed)
            Result.success(Unit)
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun handleSimulatedCommand(cmd: ProtocolMessage.Command) {
        delay(200) // Simular latencia de microcontrolador
        when (cmd.action) {
            "ARM" -> {
                simAlarmState = "ARMADA"
                simSiren = false
                simStrobe = false
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = true,
                    message = "Simulador: Perímetro ARMADO con éxito",
                    action = "ARM"
                )
                _incomingMessages.emit(res.toJsonString())
                emitStatus()
            }
            "DISARM" -> {
                simAlarmState = "DESARMADA"
                simSiren = false
                simStrobe = false
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = true,
                    message = "Simulador: Perímetro DESARMADO",
                    action = "DISARM"
                )
                _incomingMessages.emit(res.toJsonString())
                emitStatus()
            }
            "SILENCE_SIREN" -> {
                simSiren = false
                // No cambia estado de alarma a desarmada, solo apaga sirena
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = true,
                    message = "Simulador: Sirena silenciada. Protección continúa activa.",
                    action = "SILENCE_SIREN"
                )
                _incomingMessages.emit(res.toJsonString())
                emitStatus()
            }
            "TEST_ALARM" -> {
                simSiren = true
                simStrobe = true
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = true,
                    message = "Simulador: Test de sirena y estrobo ejecutado (5s)",
                    action = "TEST_ALARM"
                )
                _incomingMessages.emit(res.toJsonString())
                emitStatus()
            }
            "SYNC_SCHEDULE" -> {
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = true,
                    message = "Simulador: Horarios sincronizados y grabados en memoria no volátil",
                    action = "SYNC_SCHEDULE"
                )
                _incomingMessages.emit(res.toJsonString())
            }
            "GET_STATUS" -> {
                emitStatus()
            }
            else -> {
                val res = ProtocolMessage.CommandResult(
                    commandId = cmd.commandId,
                    success = false,
                    message = "Simulador: Comando desconocido '${cmd.action}'",
                    action = cmd.action
                )
                _incomingMessages.emit(res.toJsonString())
            }
        }
    }

    // Métodos para inyectar eventos de simulación desde la UI
    suspend fun triggerSimulatedPirMotion() {
        if (!isConnected()) return
        simPir = "MOTION_DETECTED"
        simSequence++

        val event = ProtocolMessage.SensorEvent(
            deviceId = "ESP32_SIMULADO_01",
            sequence = simSequence,
            sensor = "PIR",
            value = "MOTION_DETECTED",
            armed = simAlarmState == "ARMADA" || simAlarmState == "ALERTA"
        )
        _incomingMessages.emit(event.toJsonString())

        // Si estaba armada, el firmware simulado pasa a ALERTA y enciende sirena/estrobo
        if (simAlarmState == "ARMADA") {
            simAlarmState = "ALERTA"
            simSiren = true
            simStrobe = true
        }
        emitStatus()

        // Restablecer sensor PIR a normal después de 4 segundos
        scope.launch(Dispatchers.Default) {
            delay(4000)
            if (simPir == "MOTION_DETECTED") {
                simPir = "NORMAL"
                emitStatus()
            }
        }
    }

    suspend fun toggleSimulatedDoor() {
        if (!isConnected()) return
        simDoor = if (simDoor == "CLOSED") "OPEN" else "CLOSED"
        simSequence++

        val event = ProtocolMessage.SensorEvent(
            deviceId = "ESP32_SIMULADO_01",
            sequence = simSequence,
            sensor = "REED_DOOR",
            value = if (simDoor == "OPEN") "DOOR_OPEN" else "DOOR_CLOSED",
            armed = simAlarmState == "ARMADA" || simAlarmState == "ALERTA"
        )
        _incomingMessages.emit(event.toJsonString())

        if (simDoor == "OPEN" && simAlarmState == "ARMADA") {
            simAlarmState = "ALERTA"
            simSiren = true
            simStrobe = true
        }
        emitStatus()
    }

    suspend fun simulateSensorFault() {
        if (!isConnected()) return
        simAlarmState = "FALLO_SENSOR"
        simPir = "FAULT"
        emitStatus()
    }

    suspend fun simulateConnectionLoss() {
        _connectionState.value = ConnectionState.DISCONNECTED
        _lastError.value = "Simulación: Pérdida intencional de enlace RF/Wi-Fi"
        telemetryJob?.cancel()
    }

    override suspend fun disconnect() = withContext(Dispatchers.Default) {
        telemetryJob?.cancel()
        telemetryJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
