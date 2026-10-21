package com.example.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var intentionalDisconnect = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
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
        _connectionState.value = ConnectionState.CONNECTING

        if (target !is DeviceTarget.BluetoothRfcomm) {
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalArgumentException("Target no es Bluetooth"))
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            val err = "Bluetooth no soportado en este dispositivo"
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalStateException(err))
        }

        if (!adapter.isEnabled) {
            val err = "Bluetooth desactivado. Por favor actívelo en ajustes."
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(IllegalStateException(err))
        }

        try {
            val device = adapter.getRemoteDevice(target.deviceAddress)
            val sppUuid = UUID.fromString(ProtocolConstants.BT_SPP_UUID)
            // Cancelar discovery para mejorar throughput y estabilidad
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
        } catch (e: SecurityException) {
            val err = "Permiso de Bluetooth denegado (BLUETOOTH_CONNECT)"
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            cleanUp()
            Result.failure(e)
        } catch (e: Exception) {
            val err = "Fallo al conectar RFCOMM con ${target.deviceAddress}: ${e.message}"
            _lastError.value = err
            _connectionState.value = ConnectionState.ERROR
            cleanUp()
            Result.failure(e)
        }
    }

    private suspend fun readLoop(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            while (isActive && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.length <= ProtocolConstants.MAX_MESSAGE_SIZE) {
                    _incomingMessages.emit(line)
                }
            }
        } catch (e: Exception) {
            if (!intentionalDisconnect) {
                _lastError.value = "Pérdida de enlace RFCOMM: ${e.message}"
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
            _lastError.value = "Error enviando vía Bluetooth: ${e.message}"
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        intentionalDisconnect = true
        readerJob?.cancel()
        readerJob = null
        cleanUp()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanUp() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
    }
}
