package vn.hongducdev.honortoolkit.core.quickshare.endpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class EndpointInfoTest {
    @Test
    fun `visible info round trips name and device type`() {
        val parsed = EndpointInfo.parse(EndpointInfo("Magic7 Pro — Đức", DeviceType.PHONE).serialize())
        assertEquals("Magic7 Pro — Đức", parsed?.name)
        assertEquals(DeviceType.PHONE, parsed?.deviceType)
    }

    @Test
    fun `hidden info carries no name`() {
        val parsed = EndpointInfo.parse(EndpointInfo(null, DeviceType.TABLET).serialize())
        assertNull(parsed?.name)
        assertEquals(DeviceType.TABLET, parsed?.deviceType)
    }

    @Test
    fun `truncated info is rejected`() {
        assertNull(EndpointInfo.parse(ByteArray(10)))
        val cut = EndpointInfo("abcdef", DeviceType.PHONE).serialize().copyOf(20)
        assertNull(EndpointInfo.parse(cut))
    }

    @Test
    fun `mdns service name decodes to pcp, endpoint id, service id and padding`() {
        val raw = Base64.getUrlDecoder().decode(EndpointNaming.mdnsServiceName("AbC1"))
        assertEquals(10, raw.size)
        assertEquals(0x23, raw[0].toInt())
        assertEquals("AbC1", String(raw, 1, 4, Charsets.US_ASCII))
        assertEquals(listOf(0xFC, 0x9F, 0x5E, 0, 0), raw.drop(5).map { it.toInt() and 0xFF })
    }
}
