package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object ProtocolConstants {
    const val PROTOCOL_VERSION = "2.0"
    const val MAX_MESSAGE_SIZE = 8192
    const val DEFAULT_TCP_PORT = 8888
    const val DEFAULT_ESP32_IP = "192.168.4.1"
    const val BT_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
}

sealed class ProtocolMessage {
    abstract val type: String
    abstract fun toJsonString(): String

    data class SensorEvent(
        val deviceId: String,
        val sequence: Long,
        val sensor: String, // "PIR", "REED_DOOR", "CAMERA_MOTION"
        val value: String, // "MOTION_DETECTED", "MOTION_CLEARED", "DOOR_OPEN", "DOOR_CLOSED", "FAULT"
        val armed: Boolean,
        val eventId: String = UUID.randomUUID().toString(),
        val confidence: Float = 1.0f,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "sensor_event"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("sequence", sequence)
            json.put("sensor", sensor)
            json.put("value", value)
            json.put("armed", armed)
            json.put("eventId", eventId)
            json.put("confidence", confidence.toDouble())
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class DeviceStatus(
        val deviceId: String,
        val pir: String = "NORMAL",
        val door: String = "CLOSED",
        val alarmState: String = "DESARMADA",
        val siren: Boolean = false,
        val strobe: Boolean = false,
        val scheduleActive: Boolean = false,
        val uptimeSeconds: Long = 0L,
        val role: String = "EMISOR_RECEPTOR",
        val batteryPercent: Int? = null,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "device_status"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("pir", pir)
            json.put("door", door)
            json.put("alarmState", alarmState)
            json.put("siren", siren)
            json.put("strobe", strobe)
            json.put("scheduleActive", scheduleActive)
            json.put("uptimeSeconds", uptimeSeconds)
            json.put("role", role)
            batteryPercent?.let { json.put("batteryPercent", it) }
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class Command(
        val commandId: String,
        val action: String, // "ARM", "DISARM", "SILENCE_SIREN", "TEST_ALARM", "SYNC_SCHEDULE", "GET_STATUS"
        val targetDevice: String = "ALL",
        val senderRole: String = "EMISOR_RECEPTOR",
        val authCode: String? = null,
        val params: Map<String, Any> = emptyMap()
    ) : ProtocolMessage() {
        override val type: String = "command"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("commandId", commandId)
            json.put("action", action)
            json.put("targetDevice", targetDevice)
            json.put("senderRole", senderRole)
            authCode?.let { json.put("authCode", it) }
            if (params.isNotEmpty()) {
                val p = JSONObject()
                params.forEach { (k, v) -> p.put(k, v) }
                json.put("params", p)
            }
            return json.toString()
        }
    }

    data class CommandResult(
        val commandId: String,
        val success: Boolean,
        val message: String,
        val action: String = "",
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "command_result"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("commandId", commandId)
            json.put("success", success)
            json.put("message", message)
            json.put("action", action)
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class PairingRequest(
        val deviceId: String,
        val deviceName: String,
        val proposedRole: String,
        val pairingCode: String,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "pairing_request"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("deviceName", deviceName)
            json.put("proposedRole", proposedRole)
            json.put("pairingCode", pairingCode)
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class PairingResponse(
        val deviceId: String,
        val success: Boolean,
        val message: String,
        val assignedRole: String,
        val authToken: String? = null,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "pairing_response"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("success", success)
            json.put("message", message)
            json.put("assignedRole", assignedRole)
            authToken?.let { json.put("authToken", it) }
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class SyncPendingEvents(
        val deviceId: String,
        val eventsJson: String, // Array serializado de eventos pendientes
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "sync_pending_events"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("events", JSONArray(eventsJson))
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class SyncAck(
        val deviceId: String,
        val acknowledgedEventIds: List<String>,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "sync_ack"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("version", ProtocolConstants.PROTOCOL_VERSION)
            json.put("deviceId", deviceId)
            json.put("acknowledgedEventIds", JSONArray(acknowledgedEventIds))
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }

    data class Heartbeat(
        val deviceId: String,
        val sequence: Long,
        val uptimeSeconds: Long,
        val timestamp: String = Instant.now().toString()
    ) : ProtocolMessage() {
        override val type: String = "heartbeat"
        override fun toJsonString(): String {
            val json = JSONObject()
            json.put("type", type)
            json.put("deviceId", deviceId)
            json.put("sequence", sequence)
            json.put("uptimeSeconds", uptimeSeconds)
            json.put("timestamp", timestamp)
            return json.toString()
        }
    }
}

object ProtocolParser {
    fun parse(rawMessage: String): ProtocolMessage? {
        val trimmed = rawMessage.trim()
        if (trimmed.isEmpty() || trimmed.length > ProtocolConstants.MAX_MESSAGE_SIZE) {
            return null
        }
        return try {
            val json = JSONObject(trimmed)
            when (json.optString("type")) {
                "sensor_event" -> ProtocolMessage.SensorEvent(
                    deviceId = json.optString("deviceId", "unknown"),
                    sequence = json.optLong("sequence", 0L),
                    sensor = json.optString("sensor", "UNKNOWN"),
                    value = json.optString("value", "UNKNOWN"),
                    armed = json.optBoolean("armed", false),
                    eventId = json.optString("eventId", UUID.randomUUID().toString()),
                    confidence = json.optDouble("confidence", 1.0).toFloat(),
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "device_status" -> ProtocolMessage.DeviceStatus(
                    deviceId = json.optString("deviceId", "unknown"),
                    pir = json.optString("pir", "NORMAL"),
                    door = json.optString("door", "CLOSED"),
                    alarmState = json.optString("alarmState", "DESARMADA"),
                    siren = json.optBoolean("siren", false),
                    strobe = json.optBoolean("strobe", false),
                    scheduleActive = json.optBoolean("scheduleActive", false),
                    uptimeSeconds = json.optLong("uptimeSeconds", 0L),
                    role = json.optString("role", "EMISOR_RECEPTOR"),
                    batteryPercent = if (json.has("batteryPercent")) json.optInt("batteryPercent") else null,
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "command" -> {
                    val pMap = mutableMapOf<String, Any>()
                    json.optJSONObject("params")?.let { pObj ->
                        val keys = pObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            pMap[k] = pObj.get(k)
                        }
                    }
                    ProtocolMessage.Command(
                        commandId = json.optString("commandId", ""),
                        action = json.optString("action", ""),
                        targetDevice = json.optString("targetDevice", "ALL"),
                        senderRole = json.optString("senderRole", "EMISOR_RECEPTOR"),
                        authCode = if (json.has("authCode")) json.optString("authCode") else null,
                        params = pMap
                    )
                }
                "command_result" -> ProtocolMessage.CommandResult(
                    commandId = json.optString("commandId", ""),
                    success = json.optBoolean("success", false),
                    message = json.optString("message", ""),
                    action = json.optString("action", ""),
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "pairing_request" -> ProtocolMessage.PairingRequest(
                    deviceId = json.optString("deviceId", "unknown"),
                    deviceName = json.optString("deviceName", "Dispositivo"),
                    proposedRole = json.optString("proposedRole", "RECEPTOR_MONITOR"),
                    pairingCode = json.optString("pairingCode", ""),
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "pairing_response" -> ProtocolMessage.PairingResponse(
                    deviceId = json.optString("deviceId", "unknown"),
                    success = json.optBoolean("success", false),
                    message = json.optString("message", ""),
                    assignedRole = json.optString("assignedRole", "RECEPTOR_MONITOR"),
                    authToken = if (json.has("authToken")) json.optString("authToken") else null,
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "sync_pending_events" -> ProtocolMessage.SyncPendingEvents(
                    deviceId = json.optString("deviceId", "unknown"),
                    eventsJson = json.optJSONArray("events")?.toString() ?: "[]",
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                "sync_ack" -> {
                    val arr = json.optJSONArray("acknowledgedEventIds")
                    val list = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            list.add(arr.getString(i))
                        }
                    }
                    ProtocolMessage.SyncAck(
                        deviceId = json.optString("deviceId", "unknown"),
                        acknowledgedEventIds = list,
                        timestamp = json.optString("timestamp", Instant.now().toString())
                    )
                }
                "heartbeat" -> ProtocolMessage.Heartbeat(
                    deviceId = json.optString("deviceId", "unknown"),
                    sequence = json.optLong("sequence", 0L),
                    uptimeSeconds = json.optLong("uptimeSeconds", 0L),
                    timestamp = json.optString("timestamp", Instant.now().toString())
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
