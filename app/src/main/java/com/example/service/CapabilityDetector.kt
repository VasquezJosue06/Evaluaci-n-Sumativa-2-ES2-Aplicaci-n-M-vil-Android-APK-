package com.example.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

enum class CapabilityStatus(val label: String) {
    DISPONIBLE("Disponible"),
    NO_DISPONIBLE("No disponible en este hardware"),
    PERMISO_PENDIENTE("Requiere conceder permiso"),
    PERMISO_DENEGADO("Permiso denegado por el usuario"),
    EXTERNO_REQUERIDO("Requiere dispositivo externo (ESP32)")
}

data class DeviceCapability(
    val id: String,
    val name: String,
    val description: String,
    val status: CapabilityStatus,
    val detail: String? = null
)

class CapabilityDetector(private val context: Context) {

    fun detectAllCapabilities(): List<DeviceCapability> {
        val list = mutableListOf<DeviceCapability>()

        // 1. Cámara Trasera
        list.add(checkCamera(CameraCharacteristics.LENS_FACING_BACK, "Cámara Trasera", "Vigilancia visual perimetral"))

        // 2. Cámara Frontal
        list.add(checkCamera(CameraCharacteristics.LENS_FACING_FRONT, "Cámara Frontal", "Vigilancia de acceso directo"))

        // 3. Acelerómetro
        list.add(checkHardwareSensor(Sensor.TYPE_ACCELEROMETER, "Acelerómetro", "Detección de manipulación o vibración física del equipo"))

        // 4. Giroscopio
        list.add(checkHardwareSensor(Sensor.TYPE_GYROSCOPE, "Giroscopio", "Detección de orientación y desplazamiento brusco"))

        // 5. Sensor de Proximidad
        list.add(checkHardwareSensor(Sensor.TYPE_PROXIMITY, "Sensor de Proximidad", "Detección de objetos cercanos a la pantalla"))

        // 6. Conectividad Wi-Fi
        val hasWifi = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        list.add(
            DeviceCapability(
                id = "wifi",
                name = "Wi-Fi 802.11",
                description = "Transporte TCP/IP local principal entre celulares, tablets y ESP32",
                status = if (hasWifi) CapabilityStatus.DISPONIBLE else CapabilityStatus.NO_DISPONIBLE,
                detail = if (hasWifi) "Soporta modo Cliente y Servidor TCP" else "Sin soporte Wi-Fi"
            )
        )

        // 7. Bluetooth Classic
        val btStatus = try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            when {
                btAdapter == null -> CapabilityStatus.NO_DISPONIBLE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                    CapabilityStatus.PERMISO_PENDIENTE
                else -> {
                    try {
                        if (btAdapter.isEnabled) CapabilityStatus.DISPONIBLE else CapabilityStatus.DISPONIBLE
                    } catch (_: SecurityException) {
                        CapabilityStatus.PERMISO_PENDIENTE
                    }
                }
            }
        } catch (_: Throwable) {
            CapabilityStatus.NO_DISPONIBLE
        }

        list.add(
            DeviceCapability(
                id = "bluetooth",
                name = "Bluetooth Classic (RFCOMM)",
                description = "Transporte serie alternativo SPP para ESP32 o PC",
                status = btStatus,
                detail = "Compatible con perfil SPP"
            )
        )

        // 8. Vibración y Alertas Hápticas
        val hasVibrator = checkVibrator()
        list.add(
            DeviceCapability(
                id = "vibrator",
                name = "Vibración Háptica",
                description = "Retroalimentación táctil de pulsaciones y alerta de intrusión",
                status = if (hasVibrator) CapabilityStatus.DISPONIBLE else CapabilityStatus.NO_DISPONIBLE
            )
        )

        // 9. Salida de Audio / Sirena Local
        list.add(
            DeviceCapability(
                id = "audio_speaker",
                name = "Altavoz de Audio",
                description = "Emisión de tonos de sirena de alta intensidad desde el propio dispositivo",
                status = CapabilityStatus.DISPONIBLE,
                detail = "Disponible mediante AudioTrack / ToneGenerator"
            )
        )

        // 10. Notificaciones del Sistema
        val notifGranted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }

        list.add(
            DeviceCapability(
                id = "notifications",
                name = "Notificaciones de Alerta",
                description = "Alertas visuales en barra de estado ante intrusiones o desconexión",
                status = if (notifGranted) CapabilityStatus.DISPONIBLE else CapabilityStatus.PERMISO_PENDIENTE
            )
        )

        // 11. Batería y Energía
        val batteryLevel = getBatteryLevel()
        list.add(
            DeviceCapability(
                id = "battery",
                name = "Monitoreo de Batería",
                description = "Reporte de nivel de energía para alertas de batería baja",
                status = CapabilityStatus.DISPONIBLE,
                detail = if (batteryLevel != null) "Nivel actual: $batteryLevel%" else "Alimentación continua"
            )
        )

        // 12. Sensor PIR Externo
        list.add(
            DeviceCapability(
                id = "external_pir",
                name = "Sensor PIR Infrarrojo",
                description = "Sensor físico pasivo para detección en bodega",
                status = CapabilityStatus.EXTERNO_REQUERIDO,
                detail = "Requiere ESP32 conectado en GPIO 13"
            )
        )

        // 13. Reed Switch Magnético
        list.add(
            DeviceCapability(
                id = "external_reed",
                name = "Sensor Magnético de Puerta",
                description = "Detección de apertura de portón o acceso",
                status = CapabilityStatus.EXTERNO_REQUERIDO,
                detail = "Requiere ESP32 conectado en GPIO 14"
            )
        )

        return list
    }

    private fun checkCamera(lensFacing: Int, name: String, desc: String): DeviceCapability {
        return try {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                return DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.NO_DISPONIBLE)
            }

            val cameraIds = cameraManager.cameraIdList
            var found = false
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == lensFacing) {
                    found = true
                    break
                }
            }

            if (!found) {
                DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.NO_DISPONIBLE)
            } else if (!hasPerm) {
                DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.PERMISO_PENDIENTE, "Permiso android.permission.CAMERA requerido")
            } else {
                DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.DISPONIBLE, "CameraX operativa para análisis de movimiento")
            }
        } catch (_: Throwable) {
            DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.NO_DISPONIBLE)
        }
    }

    private fun checkHardwareSensor(type: Int, name: String, desc: String): DeviceCapability {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sensorManager?.getDefaultSensor(type)
            DeviceCapability(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                description = desc,
                status = if (sensor != null) CapabilityStatus.DISPONIBLE else CapabilityStatus.NO_DISPONIBLE,
                detail = sensor?.let { "${it.name} (${it.vendor})" }
            )
        } catch (_: Throwable) {
            DeviceCapability(name.lowercase().replace(" ", "_"), name, desc, CapabilityStatus.NO_DISPONIBLE)
        }
    }

    private fun checkVibrator(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.hasVibrator() == true
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.hasVibrator() == true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getBatteryLevel(): Int? {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
