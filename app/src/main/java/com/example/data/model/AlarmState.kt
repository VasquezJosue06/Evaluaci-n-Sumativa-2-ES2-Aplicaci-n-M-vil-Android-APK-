package com.example.data.model

/**
 * Estados del sistema de alarma según los requerimientos de la asignatura TI3042.
 */
enum class AlarmState(val label: String) {
    DESARMADA("Desarmada"),
    ARMADA("Armada"),
    ALERTA("Alerta de Intrusión"),
    FALLO_SENSOR("Fallo en Sensores"),
    SIN_CONEXION("Sin Conexión con Dispositivo");

    val isArmed: Boolean
        get() = this == ARMADA || this == ALERTA
}

enum class ConnectionState(val label: String) {
    DISCONNECTED("Desconectado"),
    CONNECTING("Conectando..."),
    CONNECTED("Conectado"),
    RECONNECTING("Reconectando..."),
    ERROR("Error de Comunicación")
}

enum class SensorStatus(val label: String) {
    NORMAL("Normal / Reposo"),
    MOTION_DETECTED("Movimiento Detectado"),
    FAULT("Fallo")
}

enum class DoorStatus(val label: String) {
    CLOSED("Puerta Cerrada"),
    OPEN("Puerta Abierta"),
    FAULT("Fallo")
}

enum class TransportType(val label: String) {
    TCP("Wi-Fi TCP/IP"),
    BLUETOOTH_RFCOMM("Bluetooth RFCOMM"),
    SIMULATED("Simulador de Demostración")
}
