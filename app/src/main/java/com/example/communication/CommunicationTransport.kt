package com.example.communication

import com.example.data.model.ConnectionState
import com.example.data.model.TransportType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Capa de abstracción de transporte inalámbrico extensible para IoT.
 * Permite alternar entre Wi-Fi TCP/IP, Bluetooth RFCOMM y Simulación.
 */
interface CommunicationTransport {
    val transportType: TransportType
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: SharedFlow<String>
    val lastError: StateFlow<String?>

    suspend fun connect(target: DeviceTarget): Result<Unit>
    suspend fun disconnect()
    suspend fun send(rawJson: String): Result<Unit>
    fun isConnected(): Boolean
}
