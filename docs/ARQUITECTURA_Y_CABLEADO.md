# Arquitectura Eléctrica, Cableado y Hardware — Alarma Perimetral de Bodega

**Asignatura:** Aplicaciones Móviles para IoT (TI3042)  
**Microcontrolador recomendado:** ESP32 DevKit v1 (30 o 38 pines)  

---

## 1. Diagrama de Pines y Asignación de GPIO

| Componente | Tipo | Pin ESP32 | Modo Pin | Especificación Eléctrica |
|---|---|---|---|---|
| **Sensor PIR (HC-SR501)** | Entrada Digital | **GPIO 13** | `INPUT` | Nivel lógico 3.3V / 5V (divisor resistivo si entrega 5V) |
| **Reed Switch Magnético** | Entrada Digital | **GPIO 14** | `INPUT_PULLUP` | Contacto seco N.C. a GND cuando puerta está cerrada |
| **Módulo Relé Sirena** | Salida Digital | **GPIO 26** | `OUTPUT` | Nivel lógico 3.3V hacia optoacoplador del módulo de relé |
| **Control Luz Estroboscópica** | Salida Digital | **GPIO 27** | `OUTPUT` | Nivel lógico 3.3V hacia gate de MOSFET o transistor |
| **LED Indicador Integrado** | Salida Digital | **GPIO 2** | `OUTPUT` | LED azul en placa (parpadeo armado, fijo en alerta) |

---

## 2. Consideraciones Eléctricas y Protección de Circuitos

### A. Aislamiento y Corriente de Sirena
- Las sirenas industriales de 12V DC consumen típicamente entre **300 mA y 1.5 A**.
- **REGLA CRÍTICA:** Un pin GPIO del ESP32 solo puede suministrar un máximo de **12 mA a 3.3V**. Conectar una sirena directamente destruirá el microcontrolador.
- **Solución implementada:** Se debe interconectar un **Módulo de Relé de 5V/12V con aislamiento por optoacoplador** (ej. relé Songle SRD-05VDC-SL-C o SRD-12VDC) o un **MOSFET de potencia canal N de nivel lógico** (ej. IRLZ44N) con diodo de rueda libre (flyback diode 1N4007) para suprimir picos inductivos.

### B. Luz Estroboscópica
- Las luces estroboscópicas operan habitualmente a 12V DC con picos de corriente pulsada.
- Se controlan mediante el GPIO 27 a través de un transistor Darlington (TIP120) o MOSFET canal N, compartiendo la tierra (GND) con la fuente de 12V.

### C. Sensor PIR (HC-SR501)
- Requiere alimentación de 5V en su pin VCC.
- Su salida de señal OUT entrega habitualmente 3.3V (compatible directo con GPIO 13). Si el modelo entrega 5V, usar un divisor de voltaje resistivo (1kΩ y 2kΩ) para proteger el GPIO.

### D. Interruptor Magnético de Puerta (Reed Switch)
- Conectado entre **GPIO 14** y **GND**.
- El firmware habilita la resistencia de pull-up interna del ESP32 (`pinMode(PIN_REED, INPUT_PULLUP)`).
- Con la puerta cerrada, el imán cierra el interruptor llevando el pin a nivel LOW (0V).
- Al abrirse la puerta, el circuito se abre y el pull-up eleva el pin a nivel HIGH (3.3V), disparando la intrusión.

---

## 3. Fuentes de Alimentación Recomendadas

1. **Fuente de Poder 12V DC / 2A:** Alimenta la sirena y la luz estroboscópica.
2. **Convertidor Reductor (Buck Converter) LM2596 o regulador 5V:** Convierte los 12V a 5V para alimentar el pin `VIN` del ESP32 y el VCC del sensor PIR.
3. **Punto Común de Tierra (GND):** La tierra de 12V y la tierra del ESP32 deben estar unidas para garantizar una referencia de voltaje compartida en los transistores/MOSFETs.
