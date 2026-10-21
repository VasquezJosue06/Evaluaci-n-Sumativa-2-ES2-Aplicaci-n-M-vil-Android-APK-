package com.example

import com.example.data.model.AlarmSchedule
import com.example.data.model.ProtocolMessage
import com.example.data.model.ProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testProtocolParser_SensorEventValid() {
        val raw = """{"type":"sensor_event","deviceId":"ESP32_01","sequence":10,"sensor":"PIR","value":"MOTION_DETECTED","armed":true}"""
        val parsed = ProtocolParser.parse(raw)
        assertNotNull(parsed)
        assertTrue(parsed is ProtocolMessage.SensorEvent)
        val event = parsed as ProtocolMessage.SensorEvent
        assertEquals("PIR", event.sensor)
        assertEquals("MOTION_DETECTED", event.value)
        assertTrue(event.armed)
    }

    @Test
    fun testProtocolParser_DeviceStatusValid() {
        val raw = """{"type":"device_status","deviceId":"ESP32_01","pir":"NORMAL","door":"CLOSED","alarmState":"ARMADA","siren":false,"strobe":false}"""
        val parsed = ProtocolParser.parse(raw)
        assertNotNull(parsed)
        assertTrue(parsed is ProtocolMessage.DeviceStatus)
        val status = parsed as ProtocolMessage.DeviceStatus
        assertEquals("ARMADA", status.alarmState)
        assertFalse(status.siren)
    }

    @Test
    fun testProtocolParser_MalformedJsonReturnsNull() {
        val malformed = """{"type":"sensor_event", incomplete json..."""
        val parsed = ProtocolParser.parse(malformed)
        assertNull(parsed)
    }

    @Test
    fun testProtocolParser_OversizedMessageReturnsNull() {
        val oversized = "a".repeat(5000)
        val parsed = ProtocolParser.parse(oversized)
        assertNull(parsed)
    }

    @Test
    fun testSchedule_CrossMidnightWindow() {
        // Horario nocturno: 20:00 a 06:00, todos los días
        val schedule = AlarmSchedule(
            enabled = true,
            startHour = 20,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            activeDays = setOf(1, 2, 3, 4, 5, 6, 7),
            manualOverride = false
        )

        val zone = ZoneId.systemDefault()
        // 22:30 (dentro de ventana)
        val evening = ZonedDateTime.of(LocalDateTime.of(2026, 10, 21, 22, 30), zone)
        assertTrue(schedule.isTimeInProtectionWindow(evening))

        // 03:15 (dentro de ventana de madrugada)
        val earlyMorning = ZonedDateTime.of(LocalDateTime.of(2026, 10, 22, 3, 15), zone)
        assertTrue(schedule.isTimeInProtectionWindow(earlyMorning))

        // 14:00 (fuera de ventana)
        val afternoon = ZonedDateTime.of(LocalDateTime.of(2026, 10, 21, 14, 0), zone)
        assertFalse(schedule.isTimeInProtectionWindow(afternoon))
    }

    @Test
    fun testSchedule_DayFiltering() {
        // Horario solo Lunes a Viernes (1..5), 08:00 a 18:00
        val schedule = AlarmSchedule(
            enabled = true,
            startHour = 8,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            activeDays = setOf(1, 2, 3, 4, 5),
            manualOverride = false
        )

        val zone = ZoneId.systemDefault()
        // 2026-10-21 es Miércoles (dayOfWeek = 3)
        val wednesdayNoon = ZonedDateTime.of(LocalDateTime.of(2026, 10, 21, 12, 0), zone)
        assertTrue(schedule.isTimeInProtectionWindow(wednesdayNoon))

        // 2026-10-25 es Domingo (dayOfWeek = 7)
        val sundayNoon = ZonedDateTime.of(LocalDateTime.of(2026, 10, 25, 12, 0), zone)
        assertFalse(schedule.isTimeInProtectionWindow(sundayNoon))
    }

    @Test
    fun testPbkdf2Hashing_ConsistentVerification() {
        val password = "BodegaIoT#2026".toCharArray()
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val spec = PBEKeySpec(password, salt, 10000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash1 = skf.generateSecret(spec).encoded

        val spec2 = PBEKeySpec(password, salt, 10000, 256)
        val hash2 = skf.generateSecret(spec2).encoded

        assertTrue(hash1.contentEquals(hash2))
    }

    @Test
    fun testProtocolParser_PairingRequestValid() {
        val raw = """{"type":"pairing_request","deviceId":"TABLET_01","deviceName":"Tablet Bodega","proposedRole":"RECEPTOR_MONITOR","pairingCode":"482910"}"""
        val parsed = ProtocolParser.parse(raw)
        assertNotNull(parsed)
        assertTrue(parsed is ProtocolMessage.PairingRequest)
        val req = parsed as ProtocolMessage.PairingRequest
        assertEquals("TABLET_01", req.deviceId)
        assertEquals("Tablet Bodega", req.deviceName)
        assertEquals("RECEPTOR_MONITOR", req.proposedRole)
        assertEquals("482910", req.pairingCode)
    }

    @Test
    fun testProtocolParser_PairingResponseValid() {
        val raw = """{"type":"pairing_response","deviceId":"PHONE_MASTER","success":true,"message":"Emparejamiento exitoso","assignedRole":"RECEPTOR_MONITOR","authToken":"token_sec_1234"}"""
        val parsed = ProtocolParser.parse(raw)
        assertNotNull(parsed)
        assertTrue(parsed is ProtocolMessage.PairingResponse)
        val res = parsed as ProtocolMessage.PairingResponse
        assertTrue(res.success)
        assertEquals("RECEPTOR_MONITOR", res.assignedRole)
        assertEquals("token_sec_1234", res.authToken)
    }

    @Test
    fun testProtocolParser_SyncAckValid() {
        val raw = """{"type":"sync_ack","deviceId":"PHONE_MASTER","acknowledgedEventIds":["evt-1","evt-2","evt-3"]}"""
        val parsed = ProtocolParser.parse(raw)
        assertNotNull(parsed)
        assertTrue(parsed is ProtocolMessage.SyncAck)
        val ack = parsed as ProtocolMessage.SyncAck
        assertEquals(3, ack.acknowledgedEventIds.size)
        assertTrue(ack.acknowledgedEventIds.contains("evt-2"))
    }
}
