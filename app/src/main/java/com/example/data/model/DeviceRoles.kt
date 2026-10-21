package com.example.data.model

enum class DeviceRole(val label: String, val description: String) {
    EMISOR_DETECTOR(
        "Emisor / Detector",
        "Detecta intrusiones con cámara o sensores y transmite alertas a la red."
    ),
    RECEPTOR_MONITOR(
        "Receptor / Monitor",
        "Recibe eventos, muestra alertas, guarda historial y activa sirena local."
    ),
    EMISOR_RECEPTOR(
        "Emisor y Receptor",
        "Detecta intrusiones y a la vez recibe y procesa alertas de otros dispositivos."
    ),
    MONITOR_SOLO_LECTURA(
        "Monitor Solo Lectura",
        "Visualiza estado e historial pero no puede enviar comandos que modifiquen la alarma."
    );

    val canSendCommands: Boolean
        get() = this != MONITOR_SOLO_LECTURA

    val canEmitAlerts: Boolean
        get() = this == EMISOR_DETECTOR || this == EMISOR_RECEPTOR

    val canReceiveAlerts: Boolean
        get() = this == RECEPTOR_MONITOR || this == EMISOR_RECEPTOR || this == MONITOR_SOLO_LECTURA
}

enum class DeviceKind(val label: String) {
    PHONE("Teléfono Android"),
    TABLET("Tablet Android"),
    PC("Computadora / Servidor"),
    ESP32("Microcontrolador ESP32"),
    UNKNOWN("Dispositivo IoT")
}

data class DeviceRecord(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val role: DeviceRole,
    val ipAddress: String? = null,
    val port: Int = 8888,
    val isAuthorized: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lastSeenTimestamp: Long = 0L,
    val batteryPercent: Int? = null,
    val firmwareVersion: String = "2.0",
    val activeSensors: List<String> = emptyList(),
    val isLocalDevice: Boolean = false
)
