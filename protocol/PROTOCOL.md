# Protocolo de Comunicación JSON — Alarma Perimetral IoT

**Asignatura:** Aplicaciones Móviles para IoT (TI3042)  
**Versión de Protocolo:** 2.0 (Compatible hacia atrás con 1.0)  
**Transporte:** TCP/IP Sockets (Wi-Fi 802.11) y Bluetooth Classic RFCOMM (SPP)  
**Delimitador de trama:** Salto de línea (`\n`)  
**Codificación:** UTF-8  
**Tamaño máximo por mensaje:** 8192 bytes  

---

## 1. Estructura General

Todos los paquetes transmitidos entre teléfonos Android, tablets, computadoras y el microcontrolador ESP32 son objetos JSON delimitados estrictamente por el caracter `\n`.

### Tipos de Mensajes:

| Tipo (`type`) | Origen | Destino | Propósito |
|---|---|---|---|
| `sensor_event` | Teléfono / ESP32 / PC | Android Monitor / Tablet | Reporta un cambio o detección de sensor físico (PIR, magnético o cámara diferencial). Incluye `eventId` y `confidence`. |
| `device_status` | Cualquier nodo | Todos / Central | Telemetría periódica de estado de sensores, actuadores, rol activo y porcentaje de batería. |
| `command` | Emisor / Monitor | Nodos / ESP32 | Envío de orden de armado, desarmado, silencio de sirena, test o sincronización con verificación de rol. |
| `command_result` | Nodo ejecutante | Emisor de comando | Confirmación (ACK/NACK) y reporte de ejecución real del comando. |
| `pairing_request` | Dispositivo nuevo | Dispositivo Master/Monitor | Solicitud de emparejamiento con código OTP de 6 dígitos para autorización criptográfica. |
| `pairing_response` | Dispositivo Master | Dispositivo solicitante | Aprobación o rechazo del emparejamiento con rol asignado y token de autorización. |
| `sync_pending_events`| Emisor fuera de línea | Monitor / Central | Lote de eventos acumulados en la cola local mientras no había conexión de red. |
| `sync_ack` | Monitor / Central | Emisor fuera de línea | Confirmación de recepción deduplicada por `eventId` para vaciar la cola fuera de línea. |
| `heartbeat` | Ambos | Ambos | Verificación de enlace activo, secuencia y telemetría de latencia. |

---

## 2. Definición de Esquemas

### A. Evento de Sensor (`sensor_event`)

Transmitido de inmediato cuando un sensor físico detecta un cambio.

```json
{
  "type": "sensor_event",
  "deviceId": "ESP32_BODEGA_01",
  "sequence": 42,
  "sensor": "PIR",
  "value": "MOTION_DETECTED",
  "armed": true,
  "timestamp": "2026-10-21T21:15:30Z"
}
```

**Valores permitidos:**
- `sensor`: `"PIR"`, `"REED_DOOR"`
- `value`:
  - Para PIR: `"MOTION_DETECTED"`, `"MOTION_CLEARED"`, `"FAULT"`
  - Para REED_DOOR: `"DOOR_OPEN"`, `"DOOR_CLOSED"`, `"FAULT"`
- `armed`: `true` (sistema armado), `false` (sistema desarmado).

---

### B. Telemetría de Estado (`device_status`)

Transmitido periódicamente (cada 5 segundos) o como respuesta inmediata a una conexión entrante o a un comando `GET_STATUS`.

```json
{
  "type": "device_status",
  "deviceId": "ESP32_BODEGA_01",
  "pir": "NORMAL",
  "door": "CLOSED",
  "alarmState": "ARMADA",
  "siren": false,
  "strobe": false,
  "scheduleActive": true,
  "uptimeSeconds": 14500,
  "timestamp": "2026-10-21T21:15:35Z"
}
```

**Valores de `alarmState`:**
- `"DESARMADA"`: Monitoreo pasivo, sensores informan pero no activan sirena.
- `"ARMADA"`: Protección perimetral activa; cualquier detección activa sirena y estrobo.
- `"ALERTA"`: Intrusión confirmada en curso; sirena sonando y estrobo destellando.
- `"FALLO_SENSOR"`: Anomalía de hardware detectada.

---

### C. Comando de Control (`command`)

Transmitido desde la aplicación Android hacia el microcontrolador.

```json
{
  "type": "command",
  "commandId": "cmd_arm_1772605000000",
  "action": "ARM",
  "targetDevice": "ESP32_BODEGA_01",
  "params": {}
}
```

**Acciones (`action`) permitidas:**
- `"ARM"`: Arma el perímetro.
- `"DISARM"`: Desarma el sistema.
- `"SILENCE_SIREN"`: Apaga la sirena sonora sin desarmar la protección ni borrar el evento.
- `"TEST_ALARM"`: Activa sirena y estrobo durante 3 segundos para comprobación de hardware.
- `"SYNC_SCHEDULE"`: Envía el horario de protección para autonomía local en el ESP32:
  ```json
  {
    "type": "command",
    "commandId": "cmd_sched_123",
    "action": "SYNC_SCHEDULE",
    "params": {
      "startHour": 20,
      "startMinute": 0,
      "endHour": 6,
      "endMinute": 0,
      "enabled": true,
      "timezone": "America/Santiago"
    }
  }
  ```
- `"GET_STATUS"`: Solicita envío inmediato del estado de sensores y actuadores.

---

### D. Resultado de Comando (`command_result`)

El microcontrolador responde de forma obligatoria informando si el comando fue ejecutado. La aplicación Android **nunca** asume éxito si solo se transmitió el paquete por el socket.

```json
{
  "type": "command_result",
  "commandId": "cmd_arm_1772605000000",
  "success": true,
  "message": "Sistema perimetral ARMADO con exito",
  "action": "ARM",
  "timestamp": "2026-10-21T21:15:31Z"
}
```

---

### E. Solicitud de Emparejamiento (`pairing_request`)

Enviada por un nuevo nodo (teléfono, tablet, PC) hacia el nodo maestro o monitor para solicitar ingreso a la red con código numérico de 6 dígitos.

```json
{
  "type": "pairing_request",
  "version": "2.0",
  "deviceId": "TABLET_CASETA_01",
  "deviceName": "Tablet Caseta Guardia",
  "proposedRole": "RECEPTOR_MONITOR",
  "pairingCode": "482910",
  "timestamp": "2026-10-21T21:16:00Z"
}
```

---

### F. Respuesta de Emparejamiento (`pairing_response`)

El nodo maestro valida el código de 6 dígitos (contra reloj de 5 minutos y contador anti-bruteforce máx 3 fallos).

```json
{
  "type": "pairing_response",
  "version": "2.0",
  "deviceId": "PHONE_MASTER_01",
  "success": true,
  "message": "Dispositivo autorizado exitosamente",
  "assignedRole": "RECEPTOR_MONITOR",
  "authToken": "sec_tok_f7a8b9c0d1e2",
  "timestamp": "2026-10-21T21:16:02Z"
}
```

---

### G. Sincronización de Eventos Fuera de Línea (`sync_pending_events`)

Transmitido automáticamente al reconectar cuando un dispositivo emisor acumuló detecciones mientras no había enlace de red.

```json
{
  "type": "sync_pending_events",
  "version": "2.0",
  "deviceId": "PHONE_DETECTOR_SUR",
  "events": [
    {
      "eventId": "evt_c1a2-3b4c-5d6e",
      "sensor": "CAMERA_MOTION",
      "value": "MOTION_DETECTED",
      "confidence": 0.88,
      "timestamp": "2026-10-21T21:05:00Z"
    }
  ],
  "timestamp": "2026-10-21T21:16:10Z"
}
```

---

### H. Confirmación de Sincronización (`sync_ack`)

El receptor responde con los identificadores `eventId` procesados e insertados en Room, permitiendo que el emisor limpie su cola pendiente sin duplicación.

```json
{
  "type": "sync_ack",
  "version": "2.0",
  "deviceId": "TABLET_CASETA_01",
  "acknowledgedEventIds": ["evt_c1a2-3b4c-5d6e"],
  "timestamp": "2026-10-21T21:16:11Z"
}
```

---

## 3. Manejo de Errores y Robustez

1. **Límite de longitud de paquete:** Paquetes mayores a 8192 bytes son descartados inmediatamente para evitar saturación de memoria.
2. **Mensajes incompletos:** El receptor acumula bytes en un buffer hasta encontrar el byte `\n`. Si el buffer supera el límite sin encontrar `\n`, se limpia el buffer.
3. **Pérdida de enlace:** Si no se reciben tramas en 15 segundos o se produce una excepción de socket (`SocketException`, `IOException`), Android transiciona el estado de visualización a `SIN_CONEXION` preservando el último estado de alarma conocido y mostrando la hora exacta de la última telemetría válida.
4. **Protección contra Fuerza Bruta:** Tras 3 intentos fallidos consecutivos de código de emparejamiento, el sistema bloquea solicitudes durante 5 minutos.
