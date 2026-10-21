package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRecord
import com.example.data.model.DeviceRole
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DeviceSecurityRegistry(context: Context, private val keyStoreHelper: KeyStoreHelper) {

    private val prefs: SharedPreferences = context.getSharedPreferences("device_security_registry_v2", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTHORIZED_DEVICES = "authorized_devices_json"
        private const val KEY_MY_DEVICE_ID = "my_device_id"
        private const val KEY_MY_DEVICE_NAME = "my_device_name"
        private const val KEY_MY_DEVICE_ROLE = "my_device_role"
        private const val KEY_ACTIVE_PAIRING_CODE = "active_pairing_code"
        private const val KEY_PAIRING_CODE_EXPIRY = "pairing_code_expiry"
        private const val KEY_PAIRING_ATTEMPTS = "pairing_attempts"
        private const val MAX_PAIRING_ATTEMPTS = 3
        private const val PAIRING_EXPIRY_MS = 5 * 60 * 1000L // 5 minutos
    }

    init {
        if (!prefs.contains(KEY_MY_DEVICE_ID)) {
            val randomId = "android_" + UUID.randomUUID().toString().substring(0, 8)
            val defaultName = "Dispositivo Android"
            prefs.edit()
                .putString(KEY_MY_DEVICE_ID, randomId)
                .putString(KEY_MY_DEVICE_NAME, defaultName)
                .putString(KEY_MY_DEVICE_ROLE, DeviceRole.EMISOR_RECEPTOR.name)
                .apply()
        }
    }

    fun getMyDeviceId(): String = prefs.getString(KEY_MY_DEVICE_ID, "android_local") ?: "android_local"

    fun getMyDeviceName(): String = prefs.getString(KEY_MY_DEVICE_NAME, "Terminal Central") ?: "Terminal Central"

    fun setMyDeviceName(name: String) {
        prefs.edit().putString(KEY_MY_DEVICE_NAME, name.trim()).apply()
    }

    fun getMyDeviceRole(): DeviceRole {
        val raw = prefs.getString(KEY_MY_DEVICE_ROLE, DeviceRole.EMISOR_RECEPTOR.name)
        return try { DeviceRole.valueOf(raw!!) } catch (_: Exception) { DeviceRole.EMISOR_RECEPTOR }
    }

    fun setMyDeviceRole(role: DeviceRole) {
        prefs.edit().putString(KEY_MY_DEVICE_ROLE, role.name).apply()
    }

    /**
     * Genera un nuevo código temporal de 6 dígitos que expira en 5 minutos.
     */
    fun createNewPairingCode(): String {
        val code = keyStoreHelper.generatePairingCode()
        val expiry = System.currentTimeMillis() + PAIRING_EXPIRY_MS
        prefs.edit()
            .putString(KEY_ACTIVE_PAIRING_CODE, code)
            .putLong(KEY_PAIRING_CODE_EXPIRY, expiry)
            .putInt(KEY_PAIRING_ATTEMPTS, 0)
            .apply()
        return code
    }

    fun getActivePairingCode(): String? {
        val expiry = prefs.getLong(KEY_PAIRING_CODE_EXPIRY, 0L)
        return if (System.currentTimeMillis() < expiry) {
            prefs.getString(KEY_ACTIVE_PAIRING_CODE, null)
        } else {
            null
        }
    }

    /**
     * Valida un código de emparejamiento entrante con límite de intentos y expiración.
     */
    fun validatePairingCode(code: String): Boolean {
        val attempts = prefs.getInt(KEY_PAIRING_ATTEMPTS, 0)
        if (attempts >= MAX_PAIRING_ATTEMPTS) {
            return false // Bloqueado por intentos excesivos
        }

        val active = getActivePairingCode() ?: return false
        val matches = (active == code.trim())

        if (!matches) {
            prefs.edit().putInt(KEY_PAIRING_ATTEMPTS, attempts + 1).apply()
        } else {
            // Consumir código de un solo uso
            prefs.edit()
                .remove(KEY_ACTIVE_PAIRING_CODE)
                .remove(KEY_PAIRING_CODE_EXPIRY)
                .putInt(KEY_PAIRING_ATTEMPTS, 0)
                .apply()
        }
        return matches
    }

    fun getAuthorizedDevices(): List<DeviceRecord> {
        val raw = prefs.getString(KEY_AUTHORIZED_DEVICES, null) ?: return defaultKnownDevices()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DeviceRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DeviceRecord(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        kind = try { DeviceKind.valueOf(obj.getString("kind")) } catch (_: Exception) { DeviceKind.PHONE },
                        role = try { DeviceRole.valueOf(obj.getString("role")) } catch (_: Exception) { DeviceRole.EMISOR_RECEPTOR },
                        ipAddress = if (obj.has("ipAddress") && !obj.isNull("ipAddress")) obj.getString("ipAddress") else null,
                        port = obj.optInt("port", 8888),
                        isAuthorized = obj.optBoolean("isAuthorized", true),
                        batteryPercent = if (obj.has("batteryPercent")) obj.getInt("batteryPercent") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            defaultKnownDevices()
        }
    }

    private fun defaultKnownDevices(): List<DeviceRecord> {
        return listOf(
            DeviceRecord(
                id = "esp32_bodega_01",
                name = "ESP32 Perimetral Bodega",
                kind = DeviceKind.ESP32,
                role = DeviceRole.EMISOR_DETECTOR,
                ipAddress = "192.168.4.1",
                port = 8888,
                isAuthorized = true,
                activeSensors = listOf("PIR", "Reed Switch")
            ),
            DeviceRecord(
                id = "tablet_vigilancia",
                name = "Tablet Estación de Vigilancia",
                kind = DeviceKind.TABLET,
                role = DeviceRole.RECEPTOR_MONITOR,
                ipAddress = "192.168.1.120",
                port = 8888,
                isAuthorized = true,
                activeSensors = emptyList()
            )
        )
    }

    fun saveAuthorizedDevice(device: DeviceRecord) {
        val list = getAuthorizedDevices().toMutableList()
        val index = list.indexOfFirst { it.id == device.id }
        if (index >= 0) {
            list[index] = device
        } else {
            list.add(device)
        }
        persistDevices(list)
    }

    fun revokeDevice(deviceId: String) {
        val list = getAuthorizedDevices().map { dev ->
            if (dev.id == deviceId) dev.copy(isAuthorized = false) else dev
        }
        persistDevices(list)
    }

    fun deleteDevice(deviceId: String) {
        val list = getAuthorizedDevices().filter { it.id != deviceId }
        persistDevices(list)
    }

    fun isDeviceAuthorized(deviceId: String): Boolean {
        // ESP32 por defecto autorizado para compatibilidad directa
        if (deviceId.contains("ESP32", ignoreCase = true) || deviceId.contains("esp32", ignoreCase = true)) {
            return true
        }
        return getAuthorizedDevices().any { it.id == deviceId && it.isAuthorized }
    }

    private fun persistDevices(list: List<DeviceRecord>) {
        val arr = JSONArray()
        list.forEach { dev ->
            val obj = JSONObject()
            obj.put("id", dev.id)
            obj.put("name", dev.name)
            obj.put("kind", dev.kind.name)
            obj.put("role", dev.role.name)
            dev.ipAddress?.let { obj.put("ipAddress", it) }
            obj.put("port", dev.port)
            obj.put("isAuthorized", dev.isAuthorized)
            dev.batteryPercent?.let { obj.put("batteryPercent", it) }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_AUTHORIZED_DEVICES, arr.toString()).apply()
    }
}
