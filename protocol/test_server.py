#!/usr/bin/env python3
"""
=============================================================================
SERVIDOR DE PRUEBA TCP PARA PC - ALARMA PERIMETRAL IOT (TI3042)
=============================================================================
Permite probar la comunicación TCP/IP entre la aplicación Android y un PC
en la misma red local Wi-Fi, simulando las respuestas y eventos del ESP32.

Uso:
    python3 test_server.py [puerto]

Comandos por consola durante la ejecución:
    [p] -> Disparar detección de movimiento PIR
    [d] -> Abrir / Cerrar interruptor magnético de puerta
    [s] -> Enviar telemetría de estado actual
    [q] -> Salir del servidor
=============================================================================
"""

import socket
import threading
import json
import time
import sys

DEFAULT_PORT = 8888

# Estado del dispositivo emulado
device_state = {
    "deviceId": "PC_TEST_HUB_01",
    "pir": "NORMAL",
    "door": "CLOSED",
    "alarmState": "DESARMADA",
    "siren": False,
    "strobe": False,
    "uptimeSeconds": 0
}

sequence = 1
active_client = None
client_lock = threading.Lock()
running = True

def send_message(client_socket, data_dict):
    try:
        raw = json.dumps(data_dict) + "\n"
        client_socket.sendall(raw.encode('utf-8'))
        print(f"[TX] {raw.strip()}")
    except Exception as e:
        print(f"[ERROR TX] {e}")

def handle_client(client_socket, client_address):
    global active_client, sequence
    print(f"\n[CONEXIÓN] Cliente Android conectado desde: {client_address}")
    with client_lock:
        active_client = client_socket

    # Enviar estado inicial
    send_status(client_socket)

    buffer = ""
    try:
        while running:
            chunk = client_socket.recv(1024)
            if not chunk:
                break
            buffer += chunk.decode('utf-8')
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if line:
                    process_command(client_socket, line)
    except Exception as e:
        print(f"[DESCONEXIÓN] Error con cliente {client_address}: {e}")
    finally:
        with client_lock:
            active_client = None
        client_socket.close()
        print(f"[CONEXIÓN] Cliente {client_address} desconectado.")

def process_command(client_socket, raw_line):
    global sequence
    print(f"[RX] {raw_line}")
    try:
        payload = json.loads(raw_line)
    except Exception:
        print("[ERROR] JSON inválido recibido")
        return

    cmd_id = payload.get("commandId", f"cmd_{int(time.time())}")
    action = payload.get("action", "")

    if action == "ARM":
        device_state["alarmState"] = "ARMADA"
        device_state["siren"] = False
        device_state["strobe"] = False
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": True,
            "message": "PC-Hub: Perímetro ARMADO con éxito",
            "action": "ARM"
        }
        send_message(client_socket, res)
        send_status(client_socket)

    elif action == "DISARM":
        device_state["alarmState"] = "DESARMADA"
        device_state["siren"] = False
        device_state["strobe"] = False
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": True,
            "message": "PC-Hub: Perímetro DESARMADO",
            "action": "DISARM"
        }
        send_message(client_socket, res)
        send_status(client_socket)

    elif action == "SILENCE_SIREN":
        device_state["siren"] = False
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": True,
            "message": "PC-Hub: Sirena silenciada. La protección continúa activa.",
            "action": "SILENCE_SIREN"
        }
        send_message(client_socket, res)
        send_status(client_socket)

    elif action == "TEST_ALARM":
        device_state["siren"] = True
        device_state["strobe"] = True
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": True,
            "message": "PC-Hub: Test de actuadores activado (sirena + estrobo)",
            "action": "TEST_ALARM"
        }
        send_message(client_socket, res)
        send_status(client_socket)

    elif action == "SYNC_SCHEDULE":
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": True,
            "message": "PC-Hub: Horarios recibidos y sincronizados en base de datos",
            "action": "SYNC_SCHEDULE"
        }
        send_message(client_socket, res)

    elif action == "GET_STATUS":
        send_status(client_socket)

    else:
        res = {
            "type": "command_result",
            "commandId": cmd_id,
            "success": False,
            "message": f"Acción desconocida: {action}",
            "action": action
        }
        send_message(client_socket, res)

def send_status(client_socket):
    status_msg = {
        "type": "device_status",
        "deviceId": device_state["deviceId"],
        "pir": device_state["pir"],
        "door": device_state["door"],
        "alarmState": device_state["alarmState"],
        "siren": device_state["siren"],
        "strobe": device_state["strobe"],
        "uptimeSeconds": device_state["uptimeSeconds"]
    }
    send_message(client_socket, status_msg)

def trigger_pir():
    global sequence
    sequence += 1
    device_state["pir"] = "MOTION_DETECTED"
    is_armed = device_state["alarmState"] in ["ARMADA", "ALERTA"]
    if is_armed:
        device_state["alarmState"] = "ALERTA"
        device_state["siren"] = True
        device_state["strobe"] = True

    event = {
        "type": "sensor_event",
        "deviceId": device_state["deviceId"],
        "sequence": sequence,
        "sensor": "PIR",
        "value": "MOTION_DETECTED",
        "armed": is_armed
    }

    with client_lock:
        if active_client:
            send_message(active_client, event)
            send_status(active_client)
            print("[SIMULACIÓN] Evento PIR transmitido al teléfono!")
        else:
            print("[INFO] No hay cliente conectado para recibir el evento.")

    def reset_pir():
        time.sleep(3)
        device_state["pir"] = "NORMAL"
        with client_lock:
            if active_client:
                send_status(active_client)

    threading.Thread(target=reset_pir, daemon=True).start()

def toggle_door():
    global sequence
    sequence += 1
    device_state["door"] = "OPEN" if device_state["door"] == "CLOSED" else "CLOSED"
    is_armed = device_state["alarmState"] in ["ARMADA", "ALERTA"]
    if device_state["door"] == "OPEN" and is_armed:
        device_state["alarmState"] = "ALERTA"
        device_state["siren"] = True
        device_state["strobe"] = True

    event = {
        "type": "sensor_event",
        "deviceId": device_state["deviceId"],
        "sequence": sequence,
        "sensor": "REED_DOOR",
        "value": "DOOR_OPEN" if device_state["door"] == "OPEN" else "DOOR_CLOSED",
        "armed": is_armed
    }

    with client_lock:
        if active_client:
            send_message(active_client, event)
            send_status(active_client)
            print(f"[SIMULACIÓN] Puerta cambiada a: {device_state['door']}")
        else:
            print("[INFO] No hay cliente conectado.")

def main():
    global running
    port = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PORT

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(("0.0.0.0", port))
        server.listen(2)
        print("=" * 70)
        print(f"SERVIDOR DE PRUEBAS TCP - ALARMA PERIMETRAL BODEGA (PUERTO {port})")
        print("=" * 70)
        print("Presione:")
        print("  [p] + Enter -> Disparar sensor PIR")
        print("  [d] + Enter -> Alternar puerta (Abierta/Cerrada)")
        print("  [s] + Enter -> Enviar telemetría de estado")
        print("  [q] + Enter -> Salir")
        print("=" * 70)
    except Exception as e:
        print(f"No se pudo iniciar el servidor en el puerto {port}: {e}")
        return

    def accept_thread():
        while running:
            try:
                sock, addr = server.accept()
                threading.Thread(target=handle_client, args=(sock, addr), daemon=True).start()
            except Exception:
                break

    threading.Thread(target=accept_thread, daemon=True).start()

    while running:
        try:
            line = input().strip().lower()
            if line == 'p':
                trigger_pir()
            elif line == 'd':
                toggle_door()
            elif line == 's':
                with client_lock:
                    if active_client:
                        send_status(active_client)
                    else:
                        print("Sin cliente conectado.")
            elif line == 'q':
                running = False
                break
        except (EOFError, KeyboardInterrupt):
            break

    server.close()
    print("\nServidor finalizado.")

if __name__ == "__main__":
    main()
