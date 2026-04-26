package org.sharenow.fileshare.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConnectionPayloadTest {

    @Test
    fun testEncodeDecode() {
        val payload = ConnectionPayload(
            host = "192.168.1.1",
            port = 8080,
            deviceName = "Test Device"
        )
        val encoded = payload.encode()
        val decoded = ConnectionPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(payload.host, decoded.host)
        assertEquals(payload.port, decoded.port)
        assertEquals(payload.deviceName, decoded.deviceName)
    }

    @Test
    fun testDecodeInvalidString() {
        val invalid = "invalid|string"
        val decoded = ConnectionPayload.decode(invalid)
        assertNull(decoded)
    }

    @Test
    fun testDeviceNameWithPipe() {
        val payload = ConnectionPayload(
            host = "1.2.3.4",
            port = 1234,
            deviceName = "Device|Name"
        )
        val encoded = payload.encode()
        val decoded = ConnectionPayload.decode(encoded)
        
        assertNotNull(decoded)
        // Note: encode replaces | with /
        assertEquals("Device/Name", decoded.deviceName)
    }

    @Test
    fun testEncodeDecodeWithHotspotCredentials() {
        val payload = ConnectionPayload(
            host = "192.168.43.1",
            port = 54321,
            deviceName = "Sender Phone",
            ssid = "ShareNow-1234",
            password = "secure-password"
        )

        val decoded = ConnectionPayload.decode(payload.encode())

        assertNotNull(decoded)
        assertEquals(payload.host, decoded.host)
        assertEquals(payload.port, decoded.port)
        assertEquals(payload.deviceName, decoded.deviceName)
        assertEquals(payload.ssid, decoded.ssid)
        assertEquals(payload.password, decoded.password)
    }

    @Test
    fun testDecodeLegacyPayloadWithoutHotspotCredentials() {
        val decoded = ConnectionPayload.decode("10.0.0.4|4567|Old Sender")

        assertNotNull(decoded)
        assertEquals("10.0.0.4", decoded.host)
        assertEquals(4567, decoded.port)
        assertEquals("Old Sender", decoded.deviceName)
        assertNull(decoded.ssid)
        assertNull(decoded.password)
    }
}
