package app.siansa.playback

import androidx.media3.common.Player
import app.siansa.playback.dlna.LinkPlay
import app.siansa.playback.dlna.Upnp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/** A LinkPlay speaker (JBL Bar, WiiM…) playing a list of songs by itself (issue #691). */
class LinkPlayTest {
    /** The JBL Bar 800 MK2's description, trimmed. */
    private val jbl = """<?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>JBL BAR 800MK2</friendlyName><manufacturer>Harman International Industries Inc</manufacturer>
        <modelName>JBL BAR 800MK2</modelName><UDN>uuid:FFB8F005-9FA6-F61D-FFF5-8004FFB8F005</UDN>
        <serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
          <controlURL>/upnp/control/rendertransport1</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
          <controlURL>/upnp/control/rendercontrol1</controlURL></service>
        <service><serviceType>urn:schemas-wiimu-com:service:PlayQueue:1</serviceType><serviceId>urn:wiimu-com:serviceId:PlayQueue</serviceId>
          <controlURL>/upnp/control/PlayQueue1</controlURL></service>
        </serviceList></device></root>"""

    @Test fun theJblsQueueServiceIsFound() {
        val device = Upnp.parseDescription(jbl, "http://192.168.1.51:49152/description.xml")!!
        assertEquals("http://192.168.1.51:49152/upnp/control/PlayQueue1", device.queueUrl)
        assertEquals("http://192.168.1.51:49152/upnp/control/rendertransport1", device.transportUrl)
    }

    @Test fun aPlainRendererHasNoQueue() {
        val plain = jbl.replace(Regex("<service><serviceType>urn:schemas-wiimu-com.*?</service>", RegexOption.DOT_MATCHES_ALL), "")
        assertNull(Upnp.parseDescription(plain, "http://192.168.1.51:49152/description.xml")!!.queueUrl)
    }

    private val tracks = listOf(
        LinkPlay.Track("a1", "http://nd:4533/rest/stream?id=a1&u=max&format=mp3", "<DIDL-Lite><item><dc:title>Rock & Roll</dc:title></item></DIDL-Lite>"),
        LinkPlay.Track("b2", "http://nd:4533/rest/stream?id=b2&u=max&format=mp3", "<DIDL-Lite/>"),
    )

    @Test fun theListIsWellFormedAndNumberedFromOne() {
        val xml = LinkPlay.queueContext(tracks)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val root = doc.documentElement
        assertEquals("PlayList", root.tagName)
        assertEquals("Siansa", root.getElementsByTagName("ListName").item(0).textContent)
        assertEquals("2", root.getElementsByTagName("TrackNumber").item(0).textContent)
        assertEquals("0", root.getElementsByTagName("FadeEnable").item(0).textContent)
        // The address and description come back as they were sent, escaped inside the list.
        assertEquals(tracks[0].url, root.getElementsByTagName("Track1").item(0).firstChild.textContent)
        assertEquals(tracks[0].metadata, (root.getElementsByTagName("Track1").item(0) as org.w3c.dom.Element).getElementsByTagName("Metadata").item(0).textContent)
        assertEquals(1, root.getElementsByTagName("Track2").length)
        assertEquals(0, root.getElementsByTagName("Track3").length)
    }

    @Test fun onlyWhatChangedIsSent() {
        // The queue grew: only add.
        assertEquals(LinkPlay.Plan(null, listOf("d")), LinkPlay.plan(listOf("b", "c"), listOf("b", "c", "d")))
        // Nothing changed.
        assertTrue(LinkPlay.plan(listOf("b", "c"), listOf("b", "c")).nothing)
        // A song in the middle changed: keep what matches, replace the rest.
        assertEquals(LinkPlay.Plan(1, listOf("x", "d")), LinkPlay.plan(listOf("b", "c", "d"), listOf("b", "x", "d")))
        // The queue was cut short: take out the extra.
        assertEquals(LinkPlay.Plan(1, emptyList()), LinkPlay.plan(listOf("b", "c"), listOf("b")))
        // Everything new.
        assertEquals(LinkPlay.Plan(0, listOf("y")), LinkPlay.plan(listOf("b"), listOf("y")))
    }

    @Test fun theSongsAheadFollowRepeat() {
        assertEquals(listOf(3, 4), LinkPlay.ahead(2, 5, Player.REPEAT_MODE_OFF))
        assertEquals(listOf(3, 4, 0, 1), LinkPlay.ahead(2, 5, Player.REPEAT_MODE_ALL))
        assertEquals(emptyList<Int>(), LinkPlay.ahead(2, 5, Player.REPEAT_MODE_ONE))
        assertEquals((1..8).toList(), LinkPlay.ahead(0, 50, Player.REPEAT_MODE_OFF))
        assertEquals(emptyList<Int>(), LinkPlay.ahead(4, 5, Player.REPEAT_MODE_OFF))
    }

    @Test fun aBriefStopBetweenListedSongsIsntTheEnd() {
        // Without a list, the first stop is the end, as before.
        assertTrue(LinkPlay.ended(listed = false, at = 0, listSize = 0, stoppedPolls = 1))
        // On a list with more songs to come: only once it stays stopped.
        assertFalse(LinkPlay.ended(listed = true, at = 0, listSize = 5, stoppedPolls = 1))
        assertTrue(LinkPlay.ended(listed = true, at = 0, listSize = 5, stoppedPolls = LinkPlay.STOPPED_POLLS))
        // Its last song: done.
        assertTrue(LinkPlay.ended(listed = true, at = 4, listSize = 5, stoppedPolls = 1))
    }
}
