package app.siansa.playback

import app.siansa.playback.dlna.Upnp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding DLNA TVs and speakers and talking to them. */
class UpnpTest {
    @Test fun searchAsksForMediaRenderers() {
        val request = Upnp.searchRequest()
        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" in request)
        assertTrue("MAN: \"ssdp:discover\"\r\n" in request)
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test fun readsTheAnswersLocation() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.50:49152/description.xml\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        assertEquals("http://192.168.1.50:49152/description.xml", Upnp.ssdpHeaders(answer)["location"])
        assertTrue(Upnp.ssdpHeaders("GARBAGE\r\nLocation: x\r\n").isEmpty())
    }

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Living Room TV</friendlyName>
            <manufacturer>Samsung</manufacturer>
            <modelName>QE55</modelName>
            <UDN>uuid:1234</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/upnp/control/rc</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>upnp/control/avt</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test fun readsNameAndControlAddresses() {
        val device = Upnp.parseDescription(description, "http://192.168.1.50:49152/dmr/description.xml")!!
        assertEquals("Living Room TV", device.name)
        assertEquals("Samsung QE55", device.model)
        assertEquals("uuid:1234", device.id)
        assertEquals("http://192.168.1.50:49152/upnp/control/rc", device.renderingUrl)
        assertEquals("http://192.168.1.50:49152/dmr/upnp/control/avt", device.transportUrl)
    }

    @Test fun usesUrlBaseWhenGiven() {
        val xml = description.replace("<specVersion>", "<URLBase>http://10.0.0.9:8080/</URLBase><specVersion>")
        assertEquals("http://10.0.0.9:8080/upnp/control/avt", Upnp.parseDescription(xml, "http://10.0.0.9:1400/x.xml")!!.transportUrl)
    }

    @Test fun devicesWithoutPlaybackAreSkipped() {
        val xml = description.replace("AVTransport", "ConnectionManager")
        assertNull(Upnp.parseDescription(xml, "http://x/"))
        assertNull(Upnp.parseDescription("not xml", "http://x/"))
    }

    @Test fun soapBodyEscapesValues() {
        val body = Upnp.soapBody(Upnp.AV_TRANSPORT, "SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to "http://n/rest/stream?id=1&u=max"))
        assertTrue("<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" in body)
        assertTrue("<CurrentURI>http://n/rest/stream?id=1&amp;u=max</CurrentURI>" in body)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", Upnp.soapAction(Upnp.AV_TRANSPORT, "Play"))
    }

    @Test fun readsValuesFromAnswers() {
        val answer = "<s:Envelope><s:Body><u:GetPositionInfoResponse xmlns:u=\"x\"><Track>1</Track>" +
            "<TrackDuration>0:04:05</TrackDuration><RelTime>0:01:02</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"
        assertEquals("0:01:02", Upnp.value(answer, "RelTime"))
        assertEquals("PLAYING", Upnp.value("<m:CurrentTransportState>PLAYING</m:CurrentTransportState>", "CurrentTransportState"))
        assertNull(Upnp.value(answer, "Missing"))
        assertEquals("701 Transition not available", Upnp.fault("<errorCode>701</errorCode><errorDescription>Transition not available</errorDescription>"))
    }

    @Test fun timesBothWays() {
        assertEquals("0:01:02", Upnp.time(62_000))
        assertEquals("1:00:00", Upnp.time(3_600_000))
        assertEquals(62_000L, Upnp.parseTime("0:01:02"))
        assertEquals(62_500L, Upnp.parseTime("00:01:02.500"))
        assertNull(Upnp.parseTime("NOT_IMPLEMENTED"))
        assertNull(Upnp.parseTime(null))
    }

    @Test fun didlDescribesTheSong() {
        val didl = Upnp.didl("s1", "Rock & Roll", "AC/DC", "<Live>", "http://n/stream?id=1&f=mp3", "audio/mpeg", 245, "http://n/cover?id=al-1")
        assertTrue("<dc:title>Rock &amp; Roll</dc:title>" in didl)
        assertTrue("<upnp:album>&lt;Live&gt;</upnp:album>" in didl)
        assertTrue("protocolInfo=\"http-get:*:audio/mpeg:*\" duration=\"0:04:05.000\">http://n/stream?id=1&amp;f=mp3</res>" in didl)
        assertTrue("<upnp:class>object.item.audioItem.musicTrack</upnp:class>" in didl)
    }
}
