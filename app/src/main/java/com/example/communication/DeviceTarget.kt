package com.example.communication

import com.example.data.model.TransportType

sealed class DeviceTarget(val transportType: TransportType) {
    data class TcpClient(
        val ipAddress: String,
        val port: Int,
        val deviceId: String = "ESP32_BODEGA_01"
    ) : DeviceTarget(TransportType.TCP)

    data class TcpServer(
        val listenPort: Int = 8888,
        val deviceId: String = "ANDROID_HUB"
    ) : DeviceTarget(TransportType.TCP)

    data class BluetoothRfcomm(
        val deviceAddress: String,
        val deviceName: String = "ESP32_BT_ALARM"
    ) : DeviceTarget(TransportType.BLUETOOTH_RFCOMM)

    data class Simulated(
        val deviceId: String = "SIMULADOR_VIRTUAL"
    ) : DeviceTarget(TransportType.SIMULATED)
}
