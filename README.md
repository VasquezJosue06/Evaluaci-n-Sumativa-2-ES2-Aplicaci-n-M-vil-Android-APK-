# Alarma Perimetral IoT — Sistema de Seguridad para Bodega

**Asignatura:** Aplicaciones Móviles para IoT (TI3042)  
**Aplicación:** Alarma Perimetral IoT  
**Paquete:** `com.aistudio.alarmaperimetral.iotz`  
**Tecnologías:** Android Nativo, Kotlin, Jetpack Compose, Material Design 3, Room Database, Corrutinas & Flow, Sockets TCP/IP, Bluetooth Classic RFCOMM (SPP), C++ Arduino (ESP32).

---

## 1. Problemática Resuelta y Justificación de Arquitectura

### 1.1 Problemática de Seguridad Perimetral en Bodegas
La protección de perímetros en bodegas e instalaciones industriales fuera del horario operativo presenta desafíos críticos que las soluciones de seguridad convencionales no logran resolver de manera eficiente:
- **Puntos ciegos y dependencia de Internet:** Muchas alarmas tradicionales dependen exclusivamente de conexiones a servidores en la nube. Un corte accidental o intencional del servicio de Internet o energía deja la instalación vulnerable o sin capacidad de reporte local.
- **Falsas alarmas ambientales:** Los sensores de movimiento infrarrojos (PIR) aislados suelen generar falsas alarmas por variaciones térmicas, pequeños animales o movimiento de polvillo.
- **Costos de infraestructura dedicada:** Desplegar sistemas propietarios de videovigilancia y sensores cableados requiere altas inversiones en hardware especializado.
- **Falta de flexibilidad en la caseta de monitoreo:** La imposibilidad de reutilizar dispositivos móviles (smartphones o tablets) como sensores perimetrales activos o consolas de monitoreo en la caseta de guardia.

### 1.2 Justificación de la Arquitectura Multi-Dispositivo e IoT
Para resolver esta problemática de manera robusta y accesible, se diseñó una arquitectura distribuida basada en **Edge Computing (procesamiento en el borde)** y comunicación híbrida fuera de la nube:

```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │                           RED LOCAL (Wi-Fi / BT)                       │
  └───────┬───────────────────────────┬───────────────────────────┬────────┘
          │                           │                           │
  ┌───────▼─────────┐         ┌───────▼─────────┐         ┌───────▼─────────┐
  │   NODO ESP32    │         │ SENSOR CAMARA   │         │ CONSOLA MONITOR │
  │  (PIR / REED /  │ <=====> │  (Smartphone    │ <=====> │ (Tablet/Phone/  │
  │ Sirena / Luz)   │   TCP   │  Emisor Edge)   │   TCP   │   PC Monitor)   │
  └─────────────────┘   BT    └─────────────────┘   BT    └─────────────────┘
```

1. **Procesamiento Local en el Borde (Edge Computing):**
   - El análisis de visión por computadora para detección de movimiento se realiza localmente mediante el framework **CameraX** de Android, utilizando una matriz de análisis diferencial de luminancia ($32 \times 24$ celdas) procesada directamente en el dispositivo. No se transmite video en bruto a la red, garantizando un ancho de banda mínimo, nula latencia de procesamiento y privacidad total.
   - El microcontrolador **ESP32** gestiona los sensores físicos locales (PIR infrarrojo y Reed Switch magnético en puertas) y los actuadores de potencia (sirena de 120dB y estrobo luminoso).

2. **Abstracción de Transporte Redundante (TCP/IP y Bluetooth SPP):**
   - **Wi-Fi / Sockets TCP/IP:** Proporciona comunicación de alta velocidad en redes locales LAN creadas por un router o por el punto de acceso (Access Point) generado directamente por el ESP32 (`ESP32_Alarma_Bodega`).
   - **Bluetooth Classic RFCOMM (SPP):** Canal alternativo de punto a punto para entornos donde no hay cobertura de red Wi-Fi o como respaldo inmediato ante caída de la red inalámbrica.

3. **Roles Dinámicos e Intercambiables:**
   Cualquier dispositivo Android puede asumir uno de los siguientes 4 roles según las necesidades del perímetro:
   - **`EMISOR_DETECTOR`:** Instalado en el perímetro de la bodega. Activa sensores físicos o la cámara para detectar intrusiones y transmitir alertas.
   - **`RECEPTOR_MONITOR`:** Ubicado en la caseta de guardia o administración. Recibe telemetría, alertas en tiempo real y activa la sirena de la consola.
   - **`EMISOR_RECEPTOR`:** Modo híbrido completo (detecta y monitorea de forma simultánea).
   - **`MONITOR_SOLO_LECTURA`:** Visualización pasiva de telemetría e historial para auditoría.

4. **Tolerancia a Fallos y Autonomía Fail-Safe:**
   - **Cola de Sincronización Fuera de Línea:** Si un nodo emisor pierde la conexión con la consola de monitoreo, los eventos de intrusión se almacenan en la base de datos local **Room Database** etiquetados como `PENDIENTE_SYNC`. Al restablecer el enlace, se sincronizan en lote con deduplicación por UUID (`eventId`).
   - **Autonomía del ESP32:** Si el ESP32 queda incomunicado mientras el sistema está en estado `ARMADA`, su firmware en C++ continúa evaluando los sensores locales y activando la sirena de forma autónoma.

---

## 2. Especificación del Protocolo de Comunicación Inalámbrica

El protocolo de comunicación **Alarma Perimetral IoT v2.0** es un protocolo de nivel de aplicación orientado a texto, ligero, estructurado en **JSON** y delimitado estrictamente por el carácter de salto de línea (`\n`).

### 2.1 Capas de Transporte y Parámetros
- **Transporte TCP/IP:** Sockets TCP bidireccionales en el puerto `8888` (configurable).
- **Transporte Bluetooth:** Perfil Serial Port Profile (SPP) sobre RFCOMM utilizando el UUID estándar `00001101-0000-1000-8000-00805F9B34FB`.
- **Codificación:** UTF-8.
- **Delimitador de trama:** `\n` (ASCII 0x0A).
- **Tamaño máximo de trama:** 8192 bytes por mensaje.

---

### 2.2 Resumen de Tipos de Mensajes (`type`)

| Mensaje (`type`) | Origen | Destino | Propósito |
|---|---|---|---|
| `sensor_event` | Emisor / ESP32 / PC | Monitor / Central | Reporte instantáneo de detección en sensor (PIR, Reed Switch o Cámara). |
| `device_status` | Todos los nodos | Todos los nodos | Telemetría periódica de sensores, estado del sistema, batería y tiempo activo. |
| `command` | Monitor / Emisor | ESP32 / Nodos | Envío de orden (`ARM`, `DISARM`, `SILENCE_SIREN`, `TEST_ALARM`, `SYNC_SCHEDULE`). |
| `command_result` | Nodo ejecutor | Emisor de orden | Confirmación explícita (ACK/NACK) del resultado del comando recibido. |
| `pairing_request` | Dispositivo nuevo | Nodo Maestro | Solicitud de incorporación a la red con código OTP de 6 dígitos. |
| `pairing_response` | Nodo Maestro | Dispositivo nuevo | Respuesta de autorización, asignación de rol y token de sesión. |
| `sync_pending_events` | Emisor reconectado | Monitor / Central | Envío en lote de eventos acumulados fuera de línea. |
| `sync_ack` | Monitor / Central | Emisor reconectado | Confirmación de recepción deduplicada por `eventId` para limpiar la cola local. |
| `heartbeat` | Ambos extremos | Ambos extremos | Verificación de enlace activo, secuencia y estimación de latencia. |

---

### 2.3 Esquemas JSON del Protocolo

#### A. Evento de Sensor (`sensor_event`)
Enviado inmediatamente ante una activación perimetral.

```json
{
  "type": "sensor_event",
  "eventId": "evt_c1a2-3b4c-5d6e",
  "deviceId": "ESP32_BODEGA_01",
  "sequence": 42,
  "sensor": "PIR",
  "value": "MOTION_DETECTED",
  "confidence": 0.95,
  "armed": true,
  "timestamp": "2026-10-22T21:15:30Z"
}
```

#### B. Telemetría de Estado (`device_status`)
Transmitido cada 5 segundos para mantener la visibilidad del estado de salud del sistema.

```json
{
  "type": "device_status",
  "deviceId": "ESP32_BODEGA_01",
  "pir": "NORMAL",
  "door": "CLOSED",
  "alarmState": "ARMADA",
  "siren": false,
  "strobe": false,
  "batteryLevel": 98,
  "scheduleActive": true,
  "uptimeSeconds": 14500,
  "timestamp": "2026-10-22T21:15:35Z"
}
```

*Estados de `alarmState`:* `"DESARMADA"`, `"ARMADA"`, `"ALERTA"`, `"FALLO_SENSOR"`.

#### C. Comando de Control (`command`)
Enviado desde la aplicación móvil hacia el microcontrolador o nodo remoto.

```json
{
  "type": "command",
  "commandId": "cmd_arm_1772605000000",
  "action": "ARM",
  "targetDevice": "ESP32_BODEGA_01",
  "params": {}
}
```

*Acciones soportadas en `action`:*
- `"ARM"`: Activa la protección perimetral.
- `"DISARM"`: Desactiva la protección y apaga alertas.
- `"SILENCE_SIREN"`: Silencia la sirena sin desarmar la supervisión de sensores.
- `"TEST_ALARM"`: Ejecuta una prueba de actuadores (3 segundos).
- `"SYNC_SCHEDULE"`: Programa el rango de horario de armado automático en el ESP32:
  ```json
  {
    "type": "command",
    "commandId": "cmd_sched_101",
    "action": "SYNC_SCHEDULE",
    "params": {
      "startHour": 20,
      "startMinute": 0,
      "endHour": 6,
      "endMinute": 0,
      "enabled": true
    }
  }
  ```

#### D. Resultado de Comando (`command_result`)
Confirmación de ejecución enviada por el receptor. La aplicación no actualiza la interfaz a estado confirmado hasta recibir este mensaje.

```json
{
  "type": "command_result",
  "commandId": "cmd_arm_1772605000000",
  "success": true,
  "message": "Sistema perimetral ARMADO con exito",
  "action": "ARM",
  "timestamp": "2026-10-22T21:15:31Z"
}
```

#### E. Solicitud y Respuesta de Emparejamiento (`pairing_request` / `pairing_response`)
Permite vincular de forma segura nuevos dispositivos mediante un código de 6 dígitos con expiración de 5 minutos y bloqueo tras 3 intentos fallidos consecutivos.

```json
{
  "type": "pairing_request",
  "version": "2.0",
  "deviceId": "TABLET_CASETA_01",
  "deviceName": "Tablet Caseta Guardia",
  "proposedRole": "RECEPTOR_MONITOR",
  "pairingCode": "482910",
  "timestamp": "2026-10-22T21:16:00Z"
}
```

```json
{
  "type": "pairing_response",
  "version": "2.0",
  "deviceId": "PHONE_MASTER_01",
  "success": true,
  "message": "Dispositivo autorizado exitosamente",
  "assignedRole": "RECEPTOR_MONITOR",
  "authToken": "sec_tok_f7a8b9c0d1e2",
  "timestamp": "2026-10-22T21:16:02Z"
}
```

#### F. Sincronización Fuera de Línea (`sync_pending_events` / `sync_ack`)
Mecanismo para transmitir en lote eventos ocurridos sin conexión a la red. El receptor procesa el arreglo e inserta únicamente los `eventId` no existentes en Room, respondiendo con `sync_ack` para que el emisor vacíe su cola local.

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
      "timestamp": "2026-10-22T21:05:00Z"
    }
  ],
  "timestamp": "2026-10-22T21:16:10Z"
}
```

```json
{
  "type": "sync_ack",
  "version": "2.0",
  "deviceId": "TABLET_CASETA_01",
  "acknowledgedEventIds": ["evt_c1a2-3b4c-5d6e"],
  "timestamp": "2026-10-22T21:16:11Z"
}
```

---

## 3. Estructura del Repositorio

```text
├── app/
│   ├── build.gradle.kts           # Configuración de compilación Gradle y dependencias
│   └── src/main/
│       ├── AndroidManifest.xml    # Permisos (INTERNET, BLUETOOTH_CONNECT, VIBRATE, CAMERA)
│       └── java/com/example/
│           ├── MainActivity.kt    # Punto de entrada y navegación principal
│           ├── communication/     # Transporte TCP, Bluetooth SPP y Simulador
│           ├── data/
│           │   ├── local/         # Room Database, Entidades, DAO, SecurityPreferences (PBKDF2)
│           │   └── model/         # Maquina de estados, esquemas de protocolo JSON
│           ├── service/           # AlarmEngine, CameraMotionDetector, SecurityNotificationManager
│           └── ui/                # Componentes Jetpack Compose, ViewModels y Pantallas
├── esp32-firmware/
│   └── esp32_alarma_perimetral.ino # Firmware C++ para ESP32 (Arduino IDE)
├── protocol/
│   ├── PROTOCOL.md                # Especificación detallada del protocolo v2.0
│   └── test_server.py             # Servidor TCP interactivo en Python
├── docs/
│   └── ARQUITECTURA_Y_CABLEADO.md # Diagrama de conexiones, rele, optoacopladores y pines ESP32
└── README.md                      # Documentación maestra del proyecto
```

---

## 4. Credenciales de Acceso y Seguridad

El sistema utiliza cifrado derivado por contraseña mediante **PBKDF2 con HMAC-SHA256** (10.000 iteraciones y sal aleatoria de 16 bytes).

- **Usuario por defecto:** `operador`
- **Contraseña por defecto:** `BodegaIoT#2026`

*Nota:* Las credenciales se pueden actualizar en cualquier momento desde la pantalla de Ajustes. Las claves criptográficas de la sesión se gestionan mediante **Android Keystore** (AES-256-GCM).

---

## 5. Instrucciones de Compilación y Ejecución

### 5.1 Requisitos Previos
- **Java Development Kit (JDK):** Versión 17 o superior.
- **Android SDK:** `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
- **Gradle:** 9.6.0 (incluido vía Gradle Wrapper).
- **Entorno de desarrollo:** Android Studio Ladybug / ME o consola de comandos.
- **Hardware Opcional:** Microcontrolador ESP32 DevKit v1, Sensor PIR HC-SR501, Sensor magnético Reed Switch, Módulo relé / Buzzer de 12V.

---

### 5.2 Compilación de la Aplicación Android

#### Opción A: Desde la línea de comandos (Terminal)
1. Abre una terminal en la raíz del proyecto.
2. Ejecute el comando de compilación de depuración:
   - **En Linux/macOS:**
     ```bash
     ./gradlew assembleDebug
     ```
   - **En Windows:**
     ```cmd
     gradlew.bat assembleDebug
     ```
3. El proceso compilará el código Kotlin, procesará los esquemas de Room con KSP y generará el paquete final.

#### Opción B: Desde Android Studio
1. Abre Android Studio y selecciona **Open an existing project**.
2. Selecciona el directorio raíz `alarma-perimetral-iot`.
3. Aguarda a que Gradle sincronice las dependencias del archivo `build.gradle.kts`.
4. Ve al menú superior: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

### 5.3 Ubicación del APK Generado

Una vez finalizada la compilación con éxito, el archivo APK de depuración estará disponible en:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

### 5.4 Instalación en Dispositivo Físico mediante ADB

1. Conecta el teléfono Android a la computadora mediante un cable USB.
2. Asegúrate de tener habilitada la **Depuración por USB** en el teléfono (*Ajustes > Opciones de desarrollador > Depuración por USB*).
3. Abre la terminal y verifica la detección del dispositivo:
   ```bash
   adb devices
   ```
4. Instala el APK en el dispositivo:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

### 5.5 Carga del Firmware en el Microcontrolador ESP32

1. Abre el software **Arduino IDE** (v2.0 o superior).
2. Ve a **Archivo > Preferencias** e incluye la URL de tarjetas ESP32:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Instala la placa **esp32** desde el Gestor de Tarjetas (*Tools > Board Manager*).
4. Instala la biblioteca **ArduinoJson** (v6.x o v7.x) desde el Gestor de Bibliotecas.
5. Abre el archivo `esp32-firmware/esp32_alarma_perimetral.ino`.
6. Selecciona la placa **ESP32 Dev Module**, el puerto serie COM correspondiente y presiona **Subir**.
7. El ESP32 creará un punto de acceso Wi-Fi llamado `ESP32_Alarma_Bodega` y abrirá el servidor TCP en el puerto `8888`.

---

### 5.6 Ejecución del Servidor de Pruebas Python (Simulación en PC)

Para realizar pruebas sin disponer del microcontrolador físico:
1. Asegúrate de tener instalado Python 3.8+ en tu computadora.
2. Ejecute el servidor simulador desde la raíz del proyecto:
   ```bash
   python3 protocol/test_server.py 8888
   ```
3. En la aplicación Android (pantalla Dispositivos), ingresa la dirección IP de tu computadora y el puerto `8888`, luego presiona **Conectar**.
4. En la consola interactiva de Python podrás simular eventos presionando:
   - `p`: Disparar evento PIR (`MOTION_DETECTED`).
   - `d`: Alternar estado de Puerta (`DOOR_OPEN` / `DOOR_CLOSED`).
   - `s`: Ver estado del servidor y clientes conectados.

---

## 6. Matriz de Evaluación y Casos de Prueba (15 Pruebas)

| Nº | Caso de Prueba | Procedimiento de Verificación |
|---|---|---|
| **1** | **Detección PIR** | Armar el sistema. En el ESP32, activar el sensor PIR con la mano (o pulsar "p" en `test_server.py`). El sistema transiciona a `ALERTA`, la sirena y estrobo se activan, y se registra `INTRUSION_PIR` en el historial. |
| **2** | **Apertura de Puerta** | Armar el sistema. Separar el imán del reed switch (o pulsar "d" en simulador). Se dispara `ALERTA` inmediata con activación de actuadores y registro `INTRUSION_DOOR`. |
| **3** | **Activación Sirena y Luz** | En pantalla Dashboard, pulsar "Probar Alarma" o disparar un sensor. Se comprueba la salida HIGH en GPIO 26 y GPIO 27 del ESP32 y el estado visual en pantalla. |
| **4** | **Horario de Alarma** | En pantalla Horarios, configurar inicio a la hora actual y activar el horario. Al entrar en el periodo, el sistema evalúa y transiciona automáticamente a `ARMADA`. Se comprueba soporte que cruza medianoche (ej: 20:00 a 06:00). |
| **5** | **Armado y Desarmado** | Pulsar "Armar" en Dashboard: se muestra diálogo de confirmación, se transmite comando `ARM` por TCP y el ESP32 confirma con `command_result`. Pulsar "Desarmar": se confirma y apaga sirena. |
| **6** | **Conexión TCP con ESP32** | Conectar teléfono a la red Wi-Fi `ESP32_Alarma_Bodega` (o router local). En pantalla Dispositivos, ingresar IP `192.168.4.1` puerto `8888`. Pulsar "Conectar". El banner cambia a `Conectado` y se recibe telemetría. |
| **7** | **Mensajes con PC** | En un computador en la misma red Wi-Fi, ejecutar `python3 protocol/test_server.py 8888`. En la app Android, ingresar la IP de la computadora y conectar. Presionar `p` o `d` en la consola de la PC para ver eventos en Android. |
| **8** | **Intercambio entre Teléfonos** | En un teléfono Android, en pantalla Dispositivos activar "Rol Servidor (Escuchar)" puerto `8888`. En el segundo teléfono, ingresar la IP del primer teléfono y pulsar conectar. |
| **9** | **Bluetooth RFCOMM** | Emparejar el ESP32 o PC por Bluetooth en Ajustes de Android. En pantalla Dispositivos pestaña Bluetooth, seleccionar el dispositivo de la lista y pulsar "Conectar" (utiliza perfil SPP). |
| **10** | **Denegación de Permisos** | Si se niega el permiso de Bluetooth en Android 12+, la aplicación no se cierra, muestra una tarjeta explicativa y mantiene el transporte Wi-Fi TCP/IP totalmente operativo. |
| **11** | **Desconexión y Reconexión** | Desconectar Wi-Fi en el teléfono o apagar el ESP32. La app marca `SIN_CONEXION`. Al restablecer la red y reconectar, se sincroniza el estado sin bloquear la interfaz. |
| **12** | **Rotación de Pantalla** | Girar el dispositivo entre horizontal y vertical. El estado observable en `AlarmViewModel` conserva las conexiones activas, telemetría y contadores sin reiniciar los sockets. |
| **13** | **Mensajes JSON Malformados** | Enviar texto corrupto o tramas incompletas al puerto 8888. `ProtocolParser` captura la excepción, descarta el mensaje corrupto y no interrumpe el ciclo de lectura. |
| **14** | **Persistencia del Historial** | Disparar eventos y cerrar completamente la aplicación (incluso forzar detención). Al reabrir, el historial en Room conserva todos los registros con su fecha, origen y etiqueta. |
| **15** | **Pérdida de Enlace Armada** | Armar la alarma y forzar desconexión de red. La app muestra advertencia destacada indicando que el perímetro estaba armado y que el ESP32 continúa protegiendo de forma autónoma. |

---

## 7. Cumplimiento de Directrices de Seguridad e ISO 27400

- **Cifrado de Credenciales:** Las contraseñas de operador no se almacenan en texto plano; se utiliza derivación de claves con algoritmo PBKDF2 con sal de 16 bytes y HMAC-SHA256.
- **Protección de Almacenamiento Local:** Las claves de sesión y preferencias sensibles se gestionan en el almacenamiento seguro de **Android Keystore (AES-256-GCM)**.
- **Principio de Mínimo Privilegio:** La aplicación solicita únicamente los permisos estrictamente requeridos para su operación (`INTERNET`, `VIBRATE`, `CAMERA`, `BLUETOOTH_CONNECT` condicional).
- **Seguridad en Redes OT/IoT:** Para despliegues en entornos de producción, se recomienda operar la red local TCP/IP sobre una VLAN aislada, redes Wi-Fi con cifrado WPA3-Enterprise o mediante túneles VPN dedicados.
