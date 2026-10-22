package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.communication.BluetoothRfcommTransport
import com.example.communication.ConnectionDiagnosticHelper
import com.example.communication.DeviceTarget
import com.example.communication.DiscoveredDevice
import com.example.communication.NsdDiscoveryManager
import com.example.communication.TcpTransport
import com.example.data.local.AlarmDatabase
import com.example.data.local.AlarmEventEntity
import com.example.data.local.DeviceSecurityRegistry
import com.example.data.local.KeyStoreHelper
import com.example.data.model.AlarmState
import com.example.data.model.ConnectionState
import com.example.data.model.DeviceKind
import com.example.data.model.DeviceRecord
import com.example.data.model.DeviceRole
import com.example.data.model.ProtocolConstants
import com.example.data.model.ProtocolMessage
import com.example.data.model.ProtocolParser
import com.example.data.model.TransportType
import com.example.service.AlarmEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectionScenariosTest {

    private lateinit var context: Context
    private lateinit var keyStoreHelper: KeyStoreHelper
    private lateinit var securityRegistry: DeviceSecurityRegistry
    private lateinit var database: AlarmDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        keyStoreHelper = KeyStoreHelper(context)
        securityRegistry = DeviceSecurityRegistry(context, keyStoreHelper)
        database = AlarmDatabase.getInstance(context)
    }

    // 1. Handshake request serialization and parsing
    @Test
    fun testHandshakeRequestAndResponseParsing() {
        val req = ProtocolMessage.HandshakeRequest(
            deviceId = "phone_test_01",
            deviceName = "Celular Emisor",
            deviceKind = "PHONE",
            role = "EMISOR_DETECTOR",
            protocolVersion = "2.0"
        )
        val json = req.toJsonString()
        val parsed = ProtocolParser.parse(json)

        assertTrue(parsed is ProtocolMessage.HandshakeRequest)
        val parsedReq = parsed as ProtocolMessage.HandshakeRequest
        assertEquals("phone_test_01", parsedReq.deviceId)
        assertEquals("Celular Emisor", parsedReq.deviceName)
        assertEquals("2.0", parsedReq.protocolVersion)
        assertEquals("EMISOR_DETECTOR", parsedReq.role)

        val resp = ProtocolMessage.HandshakeResponse(
            deviceId = "tablet_test_01",
            deviceName = "Tablet Monitor",
            compatible = true,
            protocolVersion = "2.0",
            assignedRole = "RECEPTOR_MONITOR",
            requiresPairing = false,
            message = "Protocolo compatible"
        )
        val respJson = resp.toJsonString()
        val parsedResp = ProtocolParser.parse(respJson)

        assertTrue(parsedResp is ProtocolMessage.HandshakeResponse)
        val parsedResponse = parsedResp as ProtocolMessage.HandshakeResponse
        assertTrue(parsedResponse.compatible)
        assertFalse(parsedResponse.requiresPairing)
        assertEquals("RECEPTOR_MONITOR", parsedResponse.assignedRole)
    }

    // 2. Mutual authorization and ephemeral pairing code expiration & limits
    @Test
    fun testMutualAuthorizationPairingCode() {
        val code = securityRegistry.createNewPairingCode()
        assertNotNull(code)
        assertEquals(6, code.length)

        // Invalid code should fail
        assertFalse(securityRegistry.validatePairingCode("000000"))

        // Active code should match
        val active = securityRegistry.getActivePairingCode()
        assertEquals(code, active)

        // Validating the code should succeed and consume it (single-use)
        assertTrue(securityRegistry.validatePairingCode(code))

        // Re-validating consumed code must fail
        assertFalse(securityRegistry.validatePairingCode(code))
    }

    // 3. Transport separation: TcpTransport rejects non-TCP targets
    @Test
    fun testTcpTransportRejectsNonTcpTargets() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val tcp = TcpTransport(scope)
        val btTarget = DeviceTarget.BluetoothRfcomm(deviceAddress = "00:11:22:33:44:55")

        val result = tcp.connect(btTarget)
        assertTrue(result.isFailure)
        assertTrue(tcp.lastError.value?.contains("TcpTransport sólo admite destinos TcpClient o TcpServer") == true)
        assertEquals(ConnectionState.ERROR, tcp.connectionState.value)
    }

    // 4. Bluetooth separation: BluetoothRfcommTransport rejects non-BT targets
    @Test
    fun testBluetoothTransportRejectsNonBluetoothTargets() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val bt = BluetoothRfcommTransport(context, scope)
        val tcpTarget = DeviceTarget.TcpClient(ipAddress = "192.168.1.50", port = 8888)

        val result = bt.connect(tcpTarget)
        assertTrue(result.isFailure)
        assertTrue(bt.lastError.value?.contains("BluetoothRfcommTransport sólo admite destinos Bluetooth") == true)
        assertEquals(ConnectionState.ERROR, bt.connectionState.value)
    }

    // 5. Packet framing: Messages exceeding MAX_MESSAGE_SIZE are rejected safely
    @Test
    fun testPacketFramingMaxPayloadLimit() {
        // Construct payload > 65536 bytes
        val hugeString = "A".repeat(70000)
        val parsed = ProtocolParser.parse(hugeString)
        assertNull(parsed)
    }

    // 6. Roundtrip Ping/Pong verification
    @Test
    fun testPingPongProtocol() {
        val nonce = UUID.randomUUID().toString()
        val ping = ProtocolMessage.Ping(nonce = nonce)
        val pingJson = ping.toJsonString()

        val parsedPing = ProtocolParser.parse(pingJson)
        assertTrue(parsedPing is ProtocolMessage.Ping)
        assertEquals(nonce, (parsedPing as ProtocolMessage.Ping).nonce)

        val pong = ProtocolMessage.Pong(nonce = nonce)
        val pongJson = pong.toJsonString()

        val parsedPong = ProtocolParser.parse(pongJson)
        assertTrue(parsedPong is ProtocolMessage.Pong)
        assertEquals(nonce, (parsedPong as ProtocolMessage.Pong).nonce)
    }

    // 7. Offline queue retention in Room: events are preserved on disconnect
    @Test
    fun testOfflineQueueRetentionOnDisconnect() = runBlocking {
        val dao = database.alarmEventDao()
        val initialPending = dao.getPendingSyncEvents().size

        val offlineEvent = AlarmEventEntity(
            eventId = UUID.randomUUID().toString(),
            deviceId = "phone_detector",
            eventType = "INTRUSION_CAMERA",
            sensor = "CAMERA_MOTION",
            details = "Movimiento detectado sin conexión",
            alarmState = AlarmState.ARMADA.name,
            sirenActive = true,
            strobeActive = true,
            isSimulated = false,
            priority = "CRITICA",
            deliveryState = "PENDIENTE_SYNC",
            confidence = 0.95f
        )
        dao.insertEvent(offlineEvent)

        val pendingAfterInsert = dao.getPendingSyncEvents()
        assertEquals(initialPending + 1, pendingAfterInsert.size)
        assertTrue(pendingAfterInsert.any { it.eventId == offlineEvent.eventId })

        // Mark as synced
        dao.markEventAsSynced(offlineEvent.eventId)
        val pendingAfterSync = dao.getPendingSyncEvents()
        assertEquals(initialPending, pendingAfterSync.size)
    }

    // 8. Device record authorization and revocation
    @Test
    fun testDeviceAuthorizationRegistry() {
        val devId = "tablet_remota_42"
        val dev = DeviceRecord(
            id = devId,
            name = "Tablet Vigilancia",
            kind = DeviceKind.TABLET,
            role = DeviceRole.RECEPTOR_MONITOR,
            isAuthorized = true
        )
        securityRegistry.saveAuthorizedDevice(dev)
        assertTrue(securityRegistry.isDeviceAuthorized(devId))

        // Revoke access
        securityRegistry.revokeDevice(devId)
        assertFalse(securityRegistry.isDeviceAuthorized(devId))

        // Delete device
        securityRegistry.deleteDevice(devId)
        assertFalse(securityRegistry.getAuthorizedDevices().any { it.id == devId })
    }

    // 9. NSD DiscoveredDevice data structure
    @Test
    fun testDiscoveredDeviceData() {
        val dev = DiscoveredDevice(
            id = "nsd_192_168_1_150_8888",
            name = "Tablet Monitor",
            hostAddress = "192.168.1.150",
            port = 8888,
            deviceKind = DeviceKind.TABLET,
            transportType = TransportType.TCP,
            isCompatible = true,
            protocolVersion = "2.0"
        )
        assertEquals("192.168.1.150", dev.hostAddress)
        assertEquals(8888, dev.port)
        assertEquals(DeviceKind.TABLET, dev.deviceKind)
        assertTrue(dev.isCompatible)
    }

    // 10. Connection Diagnostic tool
    @Test
    fun testConnectionDiagnosticHelper() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val nsd = NsdDiscoveryManager(context, scope)
        val bt = BluetoothRfcommTransport(context, scope)
        val helper = ConnectionDiagnosticHelper(context, nsd, bt)

        val diag = helper.runFullDiagnostic()
        assertNotNull(diag)
        assertTrue(diag.items.isNotEmpty())
        assertNotNull(diag.generalAdvice)
        // Verify items include Wi-Fi, AP Isolation, Bluetooth, and NSD
        assertTrue(diag.items.any { it.title.contains("Wi-Fi") || it.title.contains("Red") })
        assertTrue(diag.items.any { it.title.contains("Aislamiento") })
        assertTrue(diag.items.any { it.title.contains("Bluetooth") })
    }

    // 11. Role swap between phone and tablet
    @Test
    fun testRoleSwapLogic() {
        securityRegistry.setMyDeviceRole(DeviceRole.EMISOR_DETECTOR)
        assertEquals(DeviceRole.EMISOR_DETECTOR, securityRegistry.getMyDeviceRole())

        // Swap to receptor
        val swapped = if (securityRegistry.getMyDeviceRole() == DeviceRole.EMISOR_DETECTOR) {
            DeviceRole.RECEPTOR_MONITOR
        } else {
            DeviceRole.EMISOR_DETECTOR
        }
        securityRegistry.setMyDeviceRole(swapped)
        assertEquals(DeviceRole.RECEPTOR_MONITOR, securityRegistry.getMyDeviceRole())
    }

    // 12. Read-only monitor cannot send Arm/Disarm commands
    @Test
    fun testReadOnlyMonitorCannotArm() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val engine = AlarmEngine(context, scope, database)
        engine.securityRegistry.setMyDeviceRole(DeviceRole.MONITOR_SOLO_LECTURA)

        val armResult = engine.sendArmCommand()
        assertFalse(armResult)

        val disarmResult = engine.sendDisarmCommand()
        assertFalse(disarmResult)
    }
}
