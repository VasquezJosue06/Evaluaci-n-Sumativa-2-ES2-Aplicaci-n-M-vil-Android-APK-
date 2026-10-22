package com.example.communication

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRole
import com.example.data.model.ProtocolConstants
import com.example.data.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val deviceKind: DeviceKind = DeviceKind.PHONE,
    val transportType: TransportType = TransportType.TCP,
    val isCompatible: Boolean = true,
    val protocolVersion: String = ProtocolConstants.PROTOCOL_VERSION,
    val acceptingConnections: Boolean = true,
    val role: DeviceRole = DeviceRole.EMISOR_RECEPTOR
)

class NsdDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "NsdDiscoveryManager"
    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _lastDiscoveryError = MutableStateFlow<String?>(null)
    val lastDiscoveryError: StateFlow<String?> = _lastDiscoveryError.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    /**
     * Registra el servicio de la alarma en la red Wi-Fi local vía mDNS/DNS-SD.
     */
    fun startRegistration(deviceName: String, port: Int, role: DeviceRole) {
        val manager = nsdManager ?: return
        stopRegistration()

        val cleanName = "Alarma_${deviceName.replace(" ", "_")}"
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = cleanName
            serviceType = ProtocolConstants.SERVICE_TYPE_NSD
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("role", role.name)
                setAttribute("version", ProtocolConstants.PROTOCOL_VERSION)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                registeredServiceName = info?.serviceName
                _isRegistered.value = true
                Log.d(tag, "Servicio NSD registrado: ${info?.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                _isRegistered.value = false
                _lastDiscoveryError.value = "Fallo al registrar anuncio NSD (código $errorCode)"
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                _isRegistered.value = false
                registeredServiceName = null
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                _isRegistered.value = false
            }
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            _isRegistered.value = false
            _lastDiscoveryError.value = "Error iniciando anuncio de red: ${e.message}"
        }
    }

    /**
     * Cancela el anuncio del servicio en la red local.
     */
    fun stopRegistration() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        try {
            manager.unregisterService(listener)
        } catch (_: Exception) {}
        registrationListener = null
        _isRegistered.value = false
    }

    /**
     * Inicia la búsqueda automática de dispositivos compatibles en la red Wi-Fi.
     */
    fun startDiscovery() {
        val manager = nsdManager ?: run {
            _lastDiscoveryError.value = "NSD no disponible en este dispositivo"
            return
        }
        stopDiscovery()
        _discoveredDevices.value = emptyList()
        _lastDiscoveryError.value = null
        _isSearching.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                _isSearching.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                if (service == null) return
                if (service.serviceName == registeredServiceName) {
                    // Evitar auto-descubrimiento del propio dispositivo
                    return
                }

                // Resolver servicio para obtener IP y puerto
                resolveFoundService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                if (service == null) return
                val current = _discoveredDevices.value.toMutableList()
                current.removeAll { it.name == service.serviceName }
                _discoveredDevices.value = current
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                _isSearching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isSearching.value = false
                _lastDiscoveryError.value = "No se pudo iniciar la búsqueda NSD (código $errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isSearching.value = false
            }
        }

        try {
            manager.discoverServices(ProtocolConstants.SERVICE_TYPE_NSD, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            _isSearching.value = false
            _lastDiscoveryError.value = "Fallo al iniciar escaneo de red: ${e.message}"
        }
    }

    private fun resolveFoundService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "Fallo al resolver servicio NSD: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo?) {
                if (info == null) return
                val host = info.host?.hostAddress ?: return
                val port = info.port
                val name = info.serviceName ?: "Alarma Remota"

                val isTablet = name.contains("Tablet", ignoreCase = true)
                val isEsp32 = name.contains("ESP32", ignoreCase = true)
                val kind = when {
                    isTablet -> DeviceKind.TABLET
                    isEsp32 -> DeviceKind.ESP32
                    else -> DeviceKind.PHONE
                }

                val discovered = DiscoveredDevice(
                    id = "nsd_${host.replace(".", "_")}_$port",
                    name = name.replace("Alarma_", "").replace("_", " "),
                    hostAddress = host,
                    port = port,
                    deviceKind = kind,
                    transportType = TransportType.TCP,
                    isCompatible = true,
                    acceptingConnections = true
                )

                scope.launch(Dispatchers.Main) {
                    val current = _discoveredDevices.value.toMutableList()
                    val existingIndex = current.indexOfFirst { it.hostAddress == host && it.port == port }
                    if (existingIndex >= 0) {
                        current[existingIndex] = discovered
                    } else {
                        current.add(discovered)
                    }
                    _discoveredDevices.value = current
                }
            }
        }

        try {
            manager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(tag, "Error ejecutando resolveService: ${e.message}")
        }
    }

    /**
     * Detiene la búsqueda de dispositivos.
     */
    fun stopDiscovery() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        discoveryListener = null
        _isSearching.value = false
    }

    /**
     * Obtiene la dirección IPv4 local del dispositivo en la interfaz Wi-Fi activa.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
