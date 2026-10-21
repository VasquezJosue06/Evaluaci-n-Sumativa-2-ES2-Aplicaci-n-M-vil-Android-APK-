# Alarma Perimetral IoT — Sistema de Seguridad para Bodega

**Asignatura:** Aplicaciones Móviles para IoT (TI3042)  
**Aplicación:** Alarma Perimetral IoT  
**Paquete:** `com.aistudio.alarmaperimetral.iotz`  
**Tecnologías:** Android Nativo, Kotlin, Jetpack Compose, Material Design 3, Room Database, Corrutinas & Flow, Sockets TCP/IP, Bluetooth Classic RFCOMM (SPP), C++ Arduino (ESP32).

---

## 1. Resumen y Arquitectura Multi-Dispositivo 2.0

Esta solución integral resuelve la problemática de protección perimetral de una bodega fuera del horario autorizado mediante una arquitectura distribuida y tolerante a fallos que interconecta **Teléfonos móviles, Tablets, Computadoras (PC) y microcontroladores ESP32**:

- **Roles Dinámicos e Intercambiables:**
  - `EMISOR_DETECTOR`: Dispositivo ubicado en el perímetro con sensores activos (PIR, magnético o cámara). Detecta y transmite alertas.
  - `RECEPTOR_MONITOR`: Dispositivo en caseta de guardia o administración. Recibe alertas y suena sirena local sin capturar video/sensores.
  - `EMISOR_RECEPTOR`: Modo híbrido bidireccional completo (detecta y monitorea simultáneamente).
  - `MONITOR_SOLO_LECTURA`: Visualización pasiva de telemetría e historial sin capacidad de emitir comandos de control.
- **Detección Visual con Cámara (CameraX):**
  - Análisis de luminancia por matriz diferencial de 32x24 celdas.
  - Sensibilidad configurable entre 10% y 90% para evitar falsas alarmas por sombras o polvillo.
  - Procesamiento 100% en el dispositivo (sin subida de video a la nube, garantizando privacidad total).
- **Inspección de Hardware Real (`CapabilityDetector`):**
  - Detección en tiempo real de cámaras frontal/trasera, acelerómetro, giroscopio, sensor de proximidad, Wi-Fi, Bluetooth, vibrador y nivel de batería.
- **Seguridad Criptográfica y Emparejamiento:**
  - Claves criptográficas protegidas por **Android Keystore (AES-256-GCM)**.
  - Código numérico de emparejamiento de **6 dígitos** con caducidad temporal (5 minutos) y bloqueo tras 3 intentos fallidos consecutivos contra fuerza bruta.
- **Cola de Sincronización Fuera de Línea:**
  - Los eventos detectados sin conexión a la red se almacenan en Room marcados como `PENDIENTE_SYNC`.
  - Al restablecer el enlace con el monitor o la tablet, se transmiten automáticamente y se confirman con `sync_ack` deduplicando por `eventId` para evitar registros repetidos.
- **Asistente de Configuración Inicial (Wizard de 9 Pasos):**
  - Guía interactiva paso a paso para el alta de rol, calibración de sensibilidad de cámara, verificación de hardware, código de enlace y confirmación de perímetro.
- **5 Secciones de Navegación:**
  1. **Inicio (Dashboard):** estado del sistema, visualización de los 3 sensores perimetrales (PIR, Reed Switch, Cámara), selector instantáneo de rol y controles de armado.
  2. **Dispositivos:** lista de equipos vinculados, generación y validación de código de enlace de 6 dígitos, nivel de batería, estado de enlace y revocación.
  3. **Sensores:** matriz de sensores reales del equipo, vista previa de cámara con análisis diferencial en vivo y calibrador de sensibilidad.
  4. **Eventos (Historial):** bitácora persistente en Room Database con indicador de cola fuera de línea, nivel de confianza y prioridad de alerta.
  5. **Ajustes:** gestión de permisos de Android, asistente de configuración, reprogramación de horarios de protección y auditoría de seguridad.

---

## 2. Estructura del Repositorio

```text
├── app/
│   ├── build.gradle.kts           # Configuración de compilación Gradle
│   └── src/main/
│       ├── AndroidManifest.xml    # Permisos (INTERNET, BLUETOOTH_CONNECT, VIBRATE)
│       └── java/com/example/
│           ├── MainActivity.kt    # Navegación y ciclo de vida de la aplicación
│           ├── communication/     # TcpTransport, BluetoothRfcommTransport, SimulatedTransport
│           ├── data/
│           │   ├── local/         # Room Database, Entidades, DAO, SecurityPreferences (PBKDF2)
│           │   └── model/         # Máquina de estados, horarios y protocolo JSON
│           ├── service/           # AlarmEngine (evaluador de reglas y coordinador)
│           └── ui/                # Theme, ViewModel, Dashboard, Devices, Schedule, History, Settings
├── esp32-firmware/
│   └── esp32_alarma_perimetral.ino # Firmware C++ autónomo para Arduino IDE
├── protocol/
│   ├── PROTOCOL.md                # Especificación de tramas JSON delimitadas por \n
│   └── test_server.py             # Servidor TCP en Python para pruebas desde PC
├── docs/
│   └── ARQUITECTURA_Y_CABLEADO.md # Esquema de pines, aislamiento y fuentes de poder
└── README.md                      # Documentación maestra del proyecto
```

---

## 3. Credenciales de Acceso

La aplicación implementa inicio de sesión con almacenamiento protegido y hash PBKDF2 (HMAC-SHA256 con 10.000 iteraciones y sal aleatoria de 16 bytes).

- **Usuario por defecto:** `operador`
- **Contraseña por defecto:** `BodegaIoT#2026`

Puede registrar nuevos operadores o cambiar la clave en cualquier momento desde la pantalla de Ajustes.

---

## 4. Instrucciones de Compilación y Generación del APK

### En entorno AI Studio / Servidor:
La aplicación se compila automáticamente mediante el comando de compilación Gradle del contenedor.

### En Android Studio local:
1. Abra Android Studio y seleccione `Open an existing project`.
2. Seleccione la carpeta raíz del proyecto.
3. Deje que Gradle sincronice las dependencias (`compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`).
4. Para generar el APK de depuración:
   - Menú: `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.
   - El archivo se generará en: `app/build/outputs/apk/debug/app-debug.apk`.
5. O por consola:
   ```bash
   gradle assembleDebug
   ```

---

## 5. Instrucciones de Prueba según Matriz de Evaluación (15 Pruebas)

| Nº | Caso de Prueba | Procedimiento de Verificación |
|---|---|---|
| **1** | **Detección PIR** | Armar el sistema. En el ESP32, activar el sensor PIR con la mano (o pulsar "Disparar Movimiento PIR" en el simulador). El sistema transiciona a `ALERTA`, la sirena y estrobo se activan, y se registra `INTRUSION_PIR` en el historial. |
| **2** | **Apertura de Puerta** | Armar el sistema. Separar el imán del reed switch (o pulsar "Alternar Puerta" en simulador). Se dispara `ALERTA` inmediata con activación de actuadores y registro `INTRUSION_DOOR`. |
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

## 6. Seguridad y Enfoque ISO 27400

- **Sin texto plano:** Las credenciales de acceso se derivan mediante PBKDF2 con sal aleatoria de 16 bytes.
- **Minimización de permisos:** Solo se solicitan permisos estrictamente necesarios (`INTERNET`, `VIBRATE`, `BLUETOOTH_CONNECT` condicional).
- **Limitaciones de TCP en OT:** Los sockets TCP locales en texto plano no disponen de cifrado de capa de transporte; para producción se recomienda el uso de VLANs dedicadas, WPA3-Enterprise o un túnel WireGuard/IPsec.
- **Aclaración de certificación:** La app aplica directrices y buenas prácticas recomendadas por la familia ISO 27400 para IoT, sin implicar una certificación de conformidad formal.
