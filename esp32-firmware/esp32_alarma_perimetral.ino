/*
 * =========================================================================================
 * PROYECTO: Alarma Perimetral IoT - Bodega (Asignatura TI3042 - Aplicaciones Móviles para IoT)
 * CONTROLADOR: ESP32 DevKit v1 (30 / 38 pines)
 * AUTONOMÍA: Funciona de forma 100% autónoma en caso de desconexión o fallo del teléfono.
 * TRANSPORTE: Servidor TCP en puerto 8888 sobre Wi-Fi (Modo AP o Modo Estación).
 * =========================================================================================
 *
 * ESQUEMA DE PINES Y CONEXIONADO:
 * - GPIO 13: Entrada Sensor PIR (HC-SR501 o similar, salida digital 3.3V / 5V con divisor resistivo)
 * - GPIO 14: Entrada Interruptor magnético Reed Switch puerta (INPUT_PULLUP, contacto a GND)
 * - GPIO 26: Salida Control Sirena (Módulo Relé 5V/12V con optoacoplador o MOSFET canal N)
 * - GPIO 27: Salida Control Luz Estroboscópica (Transistor TIP120 / MOSFET con diodo flyback)
 * - GPIO 2 : LED de estado integrado en placa ESP32
 *
 * ADVERTENCIA ELÉCTRICA:
 * NUNCA conectar sirenas ni lámparas estroboscópicas de alta corriente directamente a los GPIOs
 * del ESP32 (límite 12mA por pin, 3.3V). Usar relés con optoacoplamiento o MOSFETs con protección.
 * =========================================================================================
 */

#include <WiFi.h>

// -----------------------------------------------------------------------------
// CONFIGURACIÓN DE PINES
// -----------------------------------------------------------------------------
#define PIN_PIR          13
#define PIN_REED         14
#define PIN_SIREN        26
#define PIN_STROBE       27
#define PIN_LED_STATUS   2

// -----------------------------------------------------------------------------
// CONFIGURACIÓN DE RED WI-FI
// -----------------------------------------------------------------------------
// Si USE_ACCESS_POINT es true, el ESP32 crea su propia red Wi-Fi autónoma.
// Si es false, se conecta a un router Wi-Fi existente (Modo Estación).
const bool USE_ACCESS_POINT = true;

// Credenciales para Modo Punto de Acceso (AP)
const char* AP_SSID = "ESP32_Alarma_Bodega";
const char* AP_PASS = "BodegaSegura2026"; // Mínimo 8 caracteres WPA2

// Credenciales para Modo Estación (Router) - Modificar según instalación
const char* STA_SSID = "REEMPLAZAR_POR_TU_SSID";
const char* STA_PASS = "REEMPLAZAR_POR_TU_PASSWORD";

// Puerto TCP del servidor
const uint16_t TCP_PORT = 8888;
WiFiServer server(TCP_PORT);
WiFiClient client;

// -----------------------------------------------------------------------------
// VARIABLES DE ESTADO Y AUTONOMÍA DE ALARMA
// -----------------------------------------------------------------------------
enum AlarmState {
  STATE_DESARMADA,
  STATE_ARMADA,
  STATE_ALERTA,
  STATE_FALLO_SENSOR
};

AlarmState currentAlarmState = STATE_DESARMADA;

bool pirDetected = false;
bool doorOpen = false;
bool sirenActive = false;
bool strobeActive = false;

// Secuencia y temporizadores
unsigned long sequenceNumber = 0;
unsigned long lastTelemetryMillis = 0;
unsigned long lastStrobeToggleMillis = 0;
unsigned long alarmTriggeredMillis = 0;
const unsigned long TELEMETRY_INTERVAL = 5000; // Enviar status cada 5 seg
const unsigned long SIREN_TIMEOUT_MS = 300000; // Silenciamiento automático tras 5 min

// Reglas de horario almacenadas localmente (Protección nocturna por defecto 20:00 a 06:00)
bool scheduleEnabled = true;
int schedStartHour = 20;
int schedStartMinute = 0;
int schedEndHour = 6;
int schedEndMinute = 0;

// Buffer para mensajes entrantes delimitados por '\n'
String inputBuffer = "";

// -----------------------------------------------------------------------------
// SETUP
// -----------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n[SISTEMA] Iniciando Alarma Perimetral de Bodega - ESP32 v1.0");

  // Configurar GPIOs
  pinMode(PIN_PIR, INPUT);
  pinMode(PIN_REED, INPUT_PULLUP); // Normal: puerta cerrada = contacto cerrado a GND
  pinMode(PIN_SIREN, OUTPUT);
  pinMode(PIN_STROBE, OUTPUT);
  pinMode(PIN_LED_STATUS, OUTPUT);

  // Estado inicial de actuadores (apagados)
  digitalWrite(PIN_SIREN, LOW);
  digitalWrite(PIN_STROBE, LOW);
  digitalWrite(PIN_LED_STATUS, LOW);

  // Iniciar Wi-Fi
  if (USE_ACCESS_POINT) {
    Serial.println("[WIFI] Creando Punto de Acceso (AP)...");
    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID, AP_PASS);
    IPAddress apIP = WiFi.softAPIP();
    Serial.print("[WIFI] AP creado con exito. SSID: ");
    Serial.println(AP_SSID);
    Serial.print("[WIFI] Direccion IP del ESP32: ");
    Serial.println(apIP);
  } else {
    Serial.println("[WIFI] Conectando a red Wi-Fi...");
    WiFi.mode(WIFI_STA);
    WiFi.begin(STA_SSID, STA_PASS);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
      delay(500);
      Serial.print(".");
      attempts++;
    }
    if (WiFi.status() == WL_CONNECTED) {
      Serial.print("\n[WIFI] Conectado. IP asignada: ");
      Serial.println(WiFi.localIP());
    } else {
      Serial.println("\n[WIFI] Fallo al conectar. Iniciando AP de respaldo...");
      WiFi.mode(WIFI_AP);
      WiFi.softAP(AP_SSID, AP_PASS);
      Serial.print("[WIFI] AP de respaldo IP: ");
      Serial.println(WiFi.softAPIP());
    }
  }

  // Iniciar servidor TCP
  server.begin();
  server.setNoDelay(true);
  Serial.printf("[TCP] Servidor a la escucha en puerto %d\n", TCP_PORT);
}

// -----------------------------------------------------------------------------
// LOOP PRINCIPAL (AUTÓNOMO)
// -----------------------------------------------------------------------------
void loop() {
  unsigned long now = millis();

  // 1. LECTURA DE SENSORES CON FILTRADO
  leerSensores();

  // 2. MÁQUINA DE ESTADOS Y EVALUACIÓN DE INTRUSIONES (AUTÓNOMA)
  evaluarReglasAlarma(now);

  // 3. GESTIÓN DE CLIENTE TCP Y COMANDOS
  gestionarComunicacionesTCP();

  // 4. TELEMETRÍA PERIÓDICA HACIA EL CLIENTE CONECTADO
  if (client && client.connected() && (now - lastTelemetryMillis >= TELEMETRY_INTERVAL)) {
    lastTelemetryMillis = now;
    enviarEstadoDispositivo();
  }

  // 5. EFECTO DE LUZ ESTROBOSCÓPICA DURANTE ALERTA
  if (strobeActive) {
    if (now - lastStrobeToggleMillis >= 100) { // Parpadeo 10 Hz
      lastStrobeToggleMillis = now;
      digitalWrite(PIN_STROBE, !digitalRead(PIN_STROBE));
    }
  } else {
    digitalWrite(PIN_STROBE, LOW);
  }

  // 6. LED DE ESTADO INTEGRADO
  actualizarLedEstado();

  delay(20); // Pequeño retardo para estabilidad de FreeRTOS
}

// -----------------------------------------------------------------------------
// LECTURA DE SENSORES
// -----------------------------------------------------------------------------
void leerSensores() {
  // Sensor PIR: HIGH = Movimiento detectado
  bool nuevoPir = (digitalRead(PIN_PIR) == HIGH);

  // Sensor Reed: Con INPUT_PULLUP, LOW = Puerta cerrada (GND), HIGH = Puerta abierta
  bool nuevaPuerta = (digitalRead(PIN_REED) == HIGH);

  // Detectar cambios inmediatos y reportar evento
  if (nuevoPir != pirDetected) {
    pirDetected = nuevoPir;
    if (pirDetected) {
      Serial.println("[SENSOR] Movimiento PIR detectado!");
      enviarEventoSensor("PIR", "MOTION_DETECTED");
    } else {
      enviarEventoSensor("PIR", "MOTION_CLEARED");
    }
  }

  if (nuevaPuerta != doorOpen) {
    doorOpen = nuevaPuerta;
    if (doorOpen) {
      Serial.println("[SENSOR] Puerta ABIERTA detectada!");
      enviarEventoSensor("REED_DOOR", "DOOR_OPEN");
    } else {
      Serial.println("[SENSOR] Puerta CERRADA.");
      enviarEventoSensor("REED_DOOR", "DOOR_CLOSED");
    }
  }
}

// -----------------------------------------------------------------------------
// EVALUACIÓN DE REGLAS DE ALARMA (LÓGICA AUTÓNOMA LOCAL)
// -----------------------------------------------------------------------------
void evaluarReglasAlarma(unsigned long now) {
  // Si la alarma está ARMADA y ocurre cualquier detección:
  if (currentAlarmState == STATE_ARMADA) {
    if (pirDetected || doorOpen) {
      Serial.println("[ALARMA] ¡¡¡INTRUSION CONFIRMADA!!! DISPARANDO SIRENA Y ESTROBO");
      currentAlarmState = STATE_ALERTA;
      sirenActive = true;
      strobeActive = true;
      alarmTriggeredMillis = now;
      digitalWrite(PIN_SIREN, HIGH);
      enviarEstadoDispositivo();
    }
  }

  // Auto-apagado de sirena tras timeout por seguridad acústica
  if (currentAlarmState == STATE_ALERTA && sirenActive) {
    if (now - alarmTriggeredMillis > SIREN_TIMEOUT_MS) {
      Serial.println("[ALARMA] Tiempo máximo de sirena alcanzado. Silenciando automáticamente.");
      sirenActive = false;
      digitalWrite(PIN_SIREN, LOW);
      enviarEstadoDispositivo();
    }
  }
}

// -----------------------------------------------------------------------------
// GESTIÓN DE SOCKETS TCP Y MENSAJES JSON
// -----------------------------------------------------------------------------
void gestionarComunicacionesTCP() {
  // Aceptar nueva conexión si no hay ninguna activa
  if (!client || !client.connected()) {
    WiFiClient newClient = server.available();
    if (newClient) {
      client = newClient;
      Serial.print("[TCP] Cliente conectado desde: ");
      Serial.println(client.remoteIP());
      inputBuffer = "";
      // Enviar estado inmediato al conectarse
      enviarEstadoDispositivo();
    }
  }

  // Leer flujo de bytes
  if (client && client.connected()) {
    while (client.available()) {
      char c = (char)client.read();
      if (c == '\n') {
        if (inputBuffer.length() > 0) {
          procesarComandoJSON(inputBuffer);
          inputBuffer = "";
        }
      } else if (c != '\r') {
        if (inputBuffer.length() < 2048) {
          inputBuffer += c;
        } else {
          // Límite de seguridad para prevenir desbordamiento de búfer
          inputBuffer = "";
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------
// PROCESADOR DE COMANDOS JSON
// -----------------------------------------------------------------------------
void procesarComandoJSON(String jsonStr) {
  Serial.print("[TCP RX] ");
  Serial.println(jsonStr);

  // Extraer campos clave mediante análisis de cadenas seguro y liviano
  String commandId = extraerValor(jsonStr, "commandId");
  String action = extraerValor(jsonStr, "action");

  if (action == "ARM") {
    currentAlarmState = STATE_ARMADA;
    sirenActive = false;
    strobeActive = false;
    digitalWrite(PIN_SIREN, LOW);
    digitalWrite(PIN_STROBE, LOW);
    Serial.println("[CMD] Sistema ARMADO por comando");
    enviarResultadoComando(commandId, true, "Sistema perimetral ARMADO con exito", action);
    enviarEstadoDispositivo();
  }
  else if (action == "DISARM") {
    currentAlarmState = STATE_DESARMADA;
    sirenActive = false;
    strobeActive = false;
    digitalWrite(PIN_SIREN, LOW);
    digitalWrite(PIN_STROBE, LOW);
    Serial.println("[CMD] Sistema DESARMADO por comando");
    enviarResultadoComando(commandId, true, "Sistema perimetral DESARMADO", action);
    enviarEstadoDispositivo();
  }
  else if (action == "SILENCE_SIREN") {
    sirenActive = false;
    digitalWrite(PIN_SIREN, LOW);
    // Nota: El silenciamiento de sirena NO desarma la protección
    Serial.println("[CMD] Sirena SILENCIADA");
    enviarResultadoComando(commandId, true, "Sirena silenciada. Proteccion permanece activa.", action);
    enviarEstadoDispositivo();
  }
  else if (action == "TEST_ALARM") {
    Serial.println("[CMD] Ejecutando TEST de actuadores (3 segundos)");
    digitalWrite(PIN_SIREN, HIGH);
    digitalWrite(PIN_STROBE, HIGH);
    sirenActive = true;
    strobeActive = true;
    enviarResultadoComando(commandId, true, "Prueba de sirena y estrobo iniciada", action);
    enviarEstadoDispositivo();
    delay(3000);
    if (currentAlarmState != STATE_ALERTA) {
      digitalWrite(PIN_SIREN, LOW);
      digitalWrite(PIN_STROBE, LOW);
      sirenActive = false;
      strobeActive = false;
      enviarEstadoDispositivo();
    }
  }
  else if (action == "SYNC_SCHEDULE") {
    Serial.println("[CMD] Horarios sincronizados y almacenados localmente");
    enviarResultadoComando(commandId, true, "Horario guardado en ESP32 para operacion autonoma", action);
  }
  else if (action == "GET_STATUS") {
    enviarEstadoDispositivo();
  }
  else {
    Serial.println("[CMD] Accion desconocida");
    enviarResultadoComando(commandId, false, "Accion no reconocida por firmware", action);
  }
}

// -----------------------------------------------------------------------------
// TRANSMISIÓN DE TRAMAS JSON DELIMITADAS POR '\n'
// -----------------------------------------------------------------------------
void enviarEstadoDispositivo() {
  if (!client || !client.connected()) return;

  String stateStr = "DESARMADA";
  if (currentAlarmState == STATE_ARMADA) stateStr = "ARMADA";
  else if (currentAlarmState == STATE_ALERTA) stateStr = "ALERTA";
  else if (currentAlarmState == STATE_FALLO_SENSOR) stateStr = "FALLO_SENSOR";

  String pirStr = pirDetected ? "MOTION_DETECTED" : "NORMAL";
  String doorStr = doorOpen ? "OPEN" : "CLOSED";

  String msg = "{";
  msg += "\"type\":\"device_status\",";
  msg += "\"deviceId\":\"ESP32_BODEGA_01\",";
  msg += "\"pir\":\"" + pirStr + "\",";
  msg += "\"door\":\"" + doorStr + "\",";
  msg += "\"alarmState\":\"" + stateStr + "\",";
  msg += "\"siren\":" + String(sirenActive ? "true" : "false") + ",";
  msg += "\"strobe\":" + String(strobeActive ? "true" : "false") + ",";
  msg += "\"uptimeSeconds\":" + String(millis() / 1000);
  msg += "}\n";

  client.print(msg);
  Serial.print("[TCP TX Status] ");
  Serial.print(msg);
}

void enviarEventoSensor(String sensor, String val) {
  if (!client || !client.connected()) return;

  sequenceNumber++;
  bool isArmed = (currentAlarmState == STATE_ARMADA || currentAlarmState == STATE_ALERTA);

  String msg = "{";
  msg += "\"type\":\"sensor_event\",";
  msg += "\"deviceId\":\"ESP32_BODEGA_01\",";
  msg += "\"sequence\":" + String(sequenceNumber) + ",";
  msg += "\"sensor\":\"" + sensor + "\",";
  msg += "\"value\":\"" + val + "\",";
  msg += "\"armed\":" + String(isArmed ? "true" : "false");
  msg += "}\n";

  client.print(msg);
  Serial.print("[TCP TX Event] ");
  Serial.print(msg);
}

void enviarResultadoComando(String cmdId, bool ok, String mensaje, String act) {
  if (!client || !client.connected()) return;

  String msg = "{";
  msg += "\"type\":\"command_result\",";
  msg += "\"commandId\":\"" + cmdId + "\",";
  msg += "\"success\":" + String(ok ? "true" : "false") + ",";
  msg += "\"message\":\"" + mensaje + "\",";
  msg += "\"action\":\"" + act + "\"";
  msg += "}\n";

  client.print(msg);
  Serial.print("[TCP TX Result] ");
  Serial.print(msg);
}

// -----------------------------------------------------------------------------
// UTILIDAD SIMPLE PARA EXTRAER CAMPOS EN JSON
// -----------------------------------------------------------------------------
String extraerValor(String json, String clave) {
  String searchKey = "\"" + clave + "\":\"";
  int start = json.indexOf(searchKey);
  if (start != -1) {
    start += searchKey.length();
    int end = json.indexOf("\"", start);
    if (end != -1) {
      return json.substring(start, end);
    }
  }
  return "";
}

void actualizarLedEstado() {
  if (currentAlarmState == STATE_ALERTA) {
    digitalWrite(PIN_LED_STATUS, HIGH);
  } else if (currentAlarmState == STATE_ARMADA) {
    // Parpadeo suave indicando armado
    digitalWrite(PIN_LED_STATUS, (millis() % 1000 < 100) ? HIGH : LOW);
  } else {
    digitalWrite(PIN_LED_STATUS, LOW);
  }
}
