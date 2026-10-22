package com.example.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.data.model.ConnectionState
import com.example.data.model.ProtocolConstants
import com.example.data.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

class BluetoothRfcommTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : CommunicationTransport {

    override val transportType: TransportType = TransportType.BLUETOOTH_RFCOMM

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var serverAcceptJob: Job? = null
    private var intentionalDisconnect = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        @Suppress("DEPRECATION")
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    override fun isConnected(): Boolean {
        val s = activeSocket
        return s != null && s.isConnected && _connectionState.value == ConnectionState.CONNECTED
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (_: Throwable) {
        false
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(target: DeviceTarget): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        intentionalDisconnect = false
        _lastError.value = null

        if (target !is DeviceTarget.BluetoothRfcomm && target !is DeviceTarget.BluetoothRfcommServer) {
            val err = "BluetoothRfcommTransport sólo admite destinos Bluetooth (recibido: ${target::class.simpleName})"
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            val err = "Bluetooth no está soportado en este dispositivo"
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalStateException(err))
        }

        if (!adapter.isEnabled) {
            val err = "Bluetooth desactivado. Por favor actívelo en ajustes del sistema."
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalStateException(err))
        }

        val sppUuid = UUID.fromString(ProtocolConstants.BT_SPP_UUID)

        try {
            when (target) {
                is DeviceTarget.BluetoothRfcommServer -> {
                    _connectionState.value = ConnectionState.PREPARING_HOST
                    val server = adapter.listenUsingRfcommWithServiceRecord(target.serviceName, sppUuid)
                    serverSocket = server
                    _connectionState.value = ConnectionState.WAITING_FOR_DEVICE

                    serverAcceptJob = scope.launch(Dispatchers.IO) {
                        try {
                            val clientSocket = server.accept()
                            if (!isActive || intentionalDisconnect) {
                                clientSocket.close()
                                return@launch
                            }
                            activeSocket = clientSocket
                            writer = BufferedWriter(OutputStreamWriter(clientSocket.outputStream, StandardCharsets.UTF_8))
                            _connectionState.value = ConnectionState.CONNECTED
                            readLoop(clientSocket)
                        } catch (e: Exception) {
                            if (!intentionalDisconnect) {
                                val isNormalClose = e.message?.contains("closed", ignoreCase = true) == true
                                if (!isNormalClose) {
                                    _lastError.value = "Error en servidor Bluetooth: ${e.message}"
                                    _connectionState.value = ConnectionState.ERROR
                                }
                            }
                        }
                    }
                    Result.success(Unit)
                }

                is DeviceTarget.BluetoothRfcomm -> {
                    _connectionState.value = ConnectionState.CONNECTING
                    val device = adapter.getRemoteDevice(target.deviceAddress)
                    // Cancelar discovery para optimizar throughput y estabilidad del canal RFCOMM
                    try { adapter.cancelDiscovery() } catch (_: SecurityException) {}

                    val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                    activeSocket = socket
                    socket.connect()

                    writer = BufferedWriter(OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8))
                    _connectionState.value = ConnectionState.CONNECTED

                    readerJob = scope.launch(Dispatchers.IO) {
                        readLoop(socket)
                    }
                    Result.success(Unit)
                }
                else -> Result.failure(IllegalArgumentException("Destino no soportado"))
            }
        } catch (e: SecurityException) {
            val err = "Permiso de Bluetooth denegado (BLUETOOTH_CONNECT). Conceda permisos en Ajustes."
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            cleanUp()
            Result.failure(e)
        } catch (e: Exception) {
            val rawMsg = e.message ?: ""
            val userFriendlyMsg = when {
                rawMsg.contains("read ret: -1", ignoreCase = true) || rawMsg.contains("socket might closed", ignoreCase = true) ->
                    "El canal RFCOMM fue cerrado o expiró el tiempo de espera."
                rawMsg.contains("connection refused", ignoreCase = true) ->
                    "Conexión rechazada por el dispositivo Bluetooth remoto."
                else ->
                    "Fallo al enlazar Bluetooth: ${e.message}"
            }
            _lastError.value = userFriendlyMsg
            _connectionState.value = ConnectionState.ERROR
            cleanUp()
            Result.failure(Exception(userFriendlyMsg, e))
        }
    }

    private suspend fun readLoop(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            while (isActive && socket.isConnected) {
                val line = try {
                    reader.readLine()
                } catch (e: IOException) {
                    val msg = e.message ?: ""
                    if (msg.contains("read ret: -1") || msg.contains("socket might closed") || msg.contains("closed") || msg.contains("broken pipe")) {
                        // EOF / socket remoto cerrado ordenadamente
                        null
                    } else {
                        throw e
                    }
                }

                if (line == null) {
                    // EOF alcanzado: desconexión ordenada del socket remoto
                    break
                }

                if (line.length <= ProtocolConstants.MAX_MESSAGE_SIZE) {
                    _incomingMessages.emit(line)
                } else {
                    _lastError.value = "Mensaje Bluetooth descartado: excedió el límite de ${ProtocolConstants.MAX_MESSAGE_SIZE} bytes"
                }
            }
        } catch (e: Exception) {
            if (!intentionalDisconnect) {
                val msg = e.message ?: ""
                val isNormalDisconnect = msg.contains("read ret: -1") || msg.contains("socket might closed") || msg.contains("closed")
                if (!isNormalDisconnect) {
                    _lastError.value = "Pérdida de enlace Bluetooth: ${e.message}"
                }
            }
        } finally {
            if (!intentionalDisconnect) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            cleanUp()
        }
    }

    override suspend fun send(rawJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        val w = writer
        val s = activeSocket
        if (w == null || s == null || !s.isConnected) {
            val err = "Socket Bluetooth no conectado"
            _lastError.value = err
            return@withContext Result.failure(IllegalStateException(err))
        }

        try {
            val msg = if (rawJson.endsWith("\n")) rawJson else "$rawJson\n"
            w.write(msg)
            w.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            _lastError.value = "Error transmitiendo por Bluetooth: ${e.message}"
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        intentionalDisconnect = true
        readerJob?.cancel()
        readerJob = null
        serverAcceptJob?.cancel()
        serverAcceptJob = null
        cleanUp()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanUp() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
