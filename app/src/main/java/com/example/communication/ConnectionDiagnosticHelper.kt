package com.example.communication

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

data class DiagnosticItem(
    val title: String,
    val isOk: Boolean,
    val detail: String,
    val recommendation: String? = null
)

data class ConnectionDiagnosticResult(
    val canConnectWifi: Boolean,
    val canConnectBluetooth: Boolean,
    val localIp: String?,
    val items: List<DiagnosticItem>,
    val generalAdvice: String
)

class ConnectionDiagnosticHelper(
    private val context: Context,
    private val nsdDiscoveryManager: NsdDiscoveryManager,
    private val bluetoothTransport: BluetoothRfcommTransport
) {
    fun runFullDiagnostic(): ConnectionDiagnosticResult {
        val items = mutableListOf<DiagnosticItem>()

        // 1. Verificar Wi-Fi y Conectividad de Red
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val localIp = nsdDiscoveryManager.getLocalIpAddress()

        if (hasWifi && localIp != null) {
            items.add(
                DiagnosticItem(
                    title = "Red Wi-Fi Local",
                    isOk = true,
                    detail = "Conectado a Wi-Fi. Dirección IP asignada: $localIp",
                    recommendation = null
                )
            )
        } else if (localIp != null) {
            items.add(
                DiagnosticItem(
                    title = "Conexión de Red",
                    isOk = true,
                    detail = "Dirección IP detectada: $localIp (interfaz activa)",
                    recommendation = null
                )
            )
        } else {
            items.add(
                DiagnosticItem(
                    title = "Red Wi-Fi Local",
                    isOk = false,
                    detail = "No se detecta conexión Wi-Fi ni dirección IP local.",
                    recommendation = "Conéctate a una red Wi-Fi en ambos equipos o activa la 'Zona Wi-Fi / Hotspot' en este celular para conectar la tablet directamente."
                )
            )
        }

        // 2. Advertencia de aislamiento AP / Redes de invitados
        items.add(
            DiagnosticItem(
                title = "Aislamiento de Clientes (AP Isolation)",
                isOk = true,
                detail = "Algunos routers o 'Redes de Invitados' impiden que dos teléfonos se vean.",
                recommendation = "Si ambos dispositivos están en la misma red pero no se detectan automáticamente, ingresa la IP manualmente o activa 'Zona Wi-Fi portátil' en uno de ellos."
            )
        )

        // 3. Bluetooth Clásico RFCOMM
        val btSupported = bluetoothTransport.isBluetoothSupported()
        val btEnabled = bluetoothTransport.isBluetoothEnabled()

        if (!btSupported) {
            items.add(
                DiagnosticItem(
                    title = "Hardware Bluetooth",
                    isOk = false,
                    detail = "Este dispositivo no cuenta con adaptador Bluetooth compatible.",
                    recommendation = "Utiliza conexión Wi-Fi local."
                )
            )
        } else if (!btEnabled) {
            items.add(
                DiagnosticItem(
                    title = "Estado Bluetooth",
                    isOk = false,
                    detail = "El adaptador Bluetooth está apagado.",
                    recommendation = "Activa el Bluetooth si deseas usarlo como método de conexión alternativo."
                )
            )
        } else {
            val paired = bluetoothTransport.getPairedDevices()
            items.add(
                DiagnosticItem(
                    title = "Estado Bluetooth",
                    isOk = true,
                    detail = "Bluetooth activo. Dispositivos emparejados en el sistema: ${paired.size}",
                    recommendation = if (paired.isEmpty()) "Para conectar por Bluetooth, vincula primero los dos teléfonos desde los Ajustes de Android." else null
                )
            )
        }

        // 4. Mecanismo de Descubrimiento Automático (NSD)
        val nsdAvailable = localIp != null
        items.add(
            DiagnosticItem(
                title = "Descubrimiento Automático (NSD/mDNS)",
                isOk = nsdAvailable,
                detail = if (nsdAvailable) "Servicio mDNS listo (_perimetral._tcp.)" else "Requiere IP activa para anunciar servicios.",
                recommendation = if (!nsdAvailable) "Verifica que el Wi-Fi esté encendido." else null
            )
        )

        val canWifi = localIp != null
        val canBt = btSupported && btEnabled

        val generalAdvice = when {
            canWifi && canBt -> "Tu equipo está listo para conectar automáticamente por Wi-Fi o Bluetooth."
            canWifi -> "Conexión Wi-Fi lista. Recomendado para comunicar Celular y Tablet."
            canBt -> "Wi-Fi no disponible. Puedes conectar tus dispositivos mediante Bluetooth RFCOMM."
            else -> "Activa el Wi-Fi o Bluetooth en los ajustes para comenzar la conexión."
        }

        return ConnectionDiagnosticResult(
            canConnectWifi = canWifi,
            canConnectBluetooth = canBt,
            localIp = localIp,
            items = items,
            generalAdvice = generalAdvice
        )
    }
}
