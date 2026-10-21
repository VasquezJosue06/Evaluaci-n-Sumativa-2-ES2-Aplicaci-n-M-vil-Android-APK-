package com.example.communication

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
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class TcpTransport(
    private val scope: CoroutineScope
) : CommunicationTransport {

    override val transportType: TransportType = TransportType.TCP

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var activeSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var currentTarget: DeviceTarget? = null
    private var intentionalDisconnect = false

    override fun isConnected(): Boolean {
        val s = activeSocket
        return s != null && s.isConnected && !s.isClosed && _connectionState.value == ConnectionState.CONNECTED
    }

    override suspend fun connect(target: DeviceTarget): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        currentTarget = target
        intentionalDisconnect = false
        _lastError.value = null
        _connectionState.value = ConnectionState.CONNECTING

        try {
            when (target) {
                is DeviceTarget.TcpClient -> {
                    val socket = Socket()
                    activeSocket = socket
                    socket.tcpNoDelay = true
                    socket.soTimeout = 0 // Bloqueo controlado en corrutina
                    socket.connect(InetSocketAddress(target.ipAddress, target.port), 6000)

                    writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                    _connectionState.value = ConnectionState.CONNECTED
                    startReading(socket)
                    Result.success(Unit)
                }
                is DeviceTarget.TcpServer -> {
                    val server = ServerSocket(target.listenPort)
                    serverSocket = server
                    _connectionState.value = ConnectionState.CONNECTING

                    readerJob = scope.launch(Dispatchers.IO) {
                        try {
                            // Esperar conexión entrante
                            val client = server.accept()
                            activeSocket = client
                            client.tcpNoDelay = true
                            writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))
                            _connectionState.value = ConnectionState.CONNECTED
                            readLoop(client)
                        } catch (e: Exception) {
                            if (!intentionalDisconnect) {
                                _lastError.value = "Error en servidor TCP: ${e.message}"
                                _connectionState.value = ConnectionState.ERROR
                            }
                        }
                    }
                    Result.success(Unit)
                }
                else -> {
                    _connectionState.value = ConnectionState.ERROR
                    Result.failure(IllegalArgumentException("Target no es TCP"))
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "Fallo de conexión TCP: ${e.message}"
            cleanUpSockets()
            Result.failure(e)
        }
    }

    private fun startReading(socket: Socket) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            readLoop(socket)
        }
    }

    private suspend fun readLoop(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            while (isActive && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.length <= ProtocolConstants.MAX_MESSAGE_SIZE) {
                    _incomingMessages.emit(line)
                } else {
                    _lastError.value = "Mensaje excedió el límite de ${ProtocolConstants.MAX_MESSAGE_SIZE} bytes descartado"
                }
            }
        } catch (e: Exception) {
            if (!intentionalDisconnect) {
                _lastError.value = "Pérdida de enlace TCP: ${e.message}"
            }
        } finally {
            if (!intentionalDisconnect) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            cleanUpSockets()
        }
    }

    override suspend fun send(rawJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        val w = writer
        val s = activeSocket
        if (w == null || s == null || !s.isConnected || s.isClosed) {
            val err = "Socket no disponible para envío"
            _lastError.value = err
            return@withContext Result.failure(IllegalStateException(err))
        }

        try {
            val messageToSend = if (rawJson.endsWith("\n")) rawJson else "$rawJson\n"
            w.write(messageToSend)
            w.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            _lastError.value = "Error enviando por TCP: ${e.message}"
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        intentionalDisconnect = true
        readerJob?.cancel()
        readerJob = null
        cleanUpSockets()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanUpSockets() {
        try {
            writer?.close()
        } catch (_: Exception) {}
        writer = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
