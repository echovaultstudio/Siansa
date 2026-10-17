package app.musix.playback.dlna

import org.w3c.dom.Element
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * The small parts of UPnP/DLNA that musix needs to play on a TV or speaker without Chromecast: finding
 * "media renderers" on the Wi-Fi (SSDP), reading their description, and the SOAP messages that tell them
 * what to play. Plain text in, plain text out, so it's tested without a device.
 */
object Upnp {
    const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900
    const val RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    /** The multicast search for media renderers, answered by each one on the network. */
    fun searchRequest(waitSeconds: Int = 2): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $waitSeconds\r\n" +
            "ST: $RENDERER\r\n" +
            "USER-AGENT: Android UPnP/1.1 musix/1\r\n\r\n"

    /** Headers of an SSDP answer, by lower-case name; empty when it isn't one. */
    fun ssdpHeaders(response: String): Map<String, String> {
        val lines = response.split("\r\n", "\n")
        if (lines.firstOrNull()?.startsWith("HTTP/1.1 200", ignoreCase = true) != true &&
            lines.firstOrNull()?.startsWith("NOTIFY", ignoreCase = true) != true
        ) return emptyMap()
        return lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
    }

    /** Reads a renderer's description: its name and where to send play and volume commands. */
    fun parseDescription(xml: String, location: String): DlnaDevice? {
        val doc = runCatching { parse(xml) }.getOrNull() ?: return null
        val root = doc.documentElement
        val base = text(root, "URLBase")?.takeIf { it.isNotBlank() } ?: location
        val devices = root.getElementsByTagName("device")
        for (i in 0 until devices.length) {
            val device = devices.item(i) as? Element ?: continue
            val services = device.getElementsByTagName("service")
            var transport: String? = null
            var rendering: String? = null
            for (j in 0 until services.length) {
                val service = services.item(j) as? Element ?: continue
                // Only the device's own services, not those of devices nested inside it.
                if (service.parentNode?.parentNode !== device) continue
                val type = text(service, "serviceType").orEmpty()
                val control = text(service, "controlURL") ?: continue
                when {
                    type.startsWith("urn:schemas-upnp-org:service:AVTransport:") -> transport = resolve(base, control)
                    type.startsWith("urn:schemas-upnp-org:service:RenderingControl:") -> rendering = resolve(base, control)
                }
            }
            if (transport == null) continue
            val udn = text(device, "UDN") ?: location
            val name = text(device, "friendlyName")?.takeIf { it.isNotBlank() } ?: "TV or speaker"
            return DlnaDevice(
                id = udn,
                name = name.trim(),
                model = listOfNotNull(text(device, "manufacturer"), text(device, "modelName")).joinToString(" ").trim().ifEmpty { null },
                transportUrl = transport,
                renderingUrl = rendering,
            )
        }
        return null
    }

    /** A SOAP request body for [action] on [service] with [args] in order. */
    fun soapBody(service: String, action: String, args: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
        append("<s:Body><u:$action xmlns:u=\"$service\">")
        args.forEach { (name, value) -> append("<$name>").append(escape(value)).append("</$name>") }
        append("</u:$action></s:Body></s:Envelope>")
    }

    /** The SOAPACTION header for [action] on [service]. */
    fun soapAction(service: String, action: String) = "\"$service#$action\""

    /** A value from a SOAP answer (e.g. CurrentTransportState), whatever prefix the device uses. */
    fun value(response: String, name: String): String? =
        Regex("<(?:[\\w-]+:)?$name(?:\\s[^>]*)?>([^<]*)</(?:[\\w-]+:)?$name>").find(response)?.groupValues?.get(1)?.let(::unescape)

    /** The SOAP fault's error code and text, for the log. */
    fun fault(response: String): String? {
        val code = value(response, "errorCode") ?: return null
        return listOfNotNull(code, value(response, "errorDescription")).joinToString(" ")
    }

    /**
     * What the TV shows while playing: the song's DIDL-Lite description, with its stream address, format,
     * length and cover.
     */
    fun didl(id: String, title: String, artist: String, album: String, url: String, mime: String, durationSec: Int, cover: String?): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">")
        append("<item id=\"").append(escape(id)).append("\" parentID=\"0\" restricted=\"1\">")
        append("<dc:title>").append(escape(title)).append("</dc:title>")
        if (artist.isNotBlank()) {
            append("<dc:creator>").append(escape(artist)).append("</dc:creator>")
            append("<upnp:artist>").append(escape(artist)).append("</upnp:artist>")
        }
        if (album.isNotBlank()) append("<upnp:album>").append(escape(album)).append("</upnp:album>")
        cover?.let { append("<upnp:albumArtURI>").append(escape(it)).append("</upnp:albumArtURI>") }
        append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
        append("<res protocolInfo=\"http-get:*:").append(mime).append(":*\"")
        if (durationSec > 0) append(" duration=\"").append(time(durationSec * 1000L)).append(".000\"")
        append(">").append(escape(url)).append("</res>")
        append("</item></DIDL-Lite>")
    }

    /** "H:MM:SS" as UPnP writes times. */
    fun time(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Milliseconds from a UPnP time ("0:03:21", "00:03:21.500"); null when it isn't one (e.g. "NOT_IMPLEMENTED"). */
    fun parseTime(text: String?): Long? {
        val parts = text?.trim()?.split(':') ?: return null
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val sec = parts[2].toDoubleOrNull() ?: return null
        return h * 3_600_000 + m * 60_000 + (sec * 1000).toLong()
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun resolve(base: String, path: String): String =
        runCatching { URI(base).resolve(path.trim()).toString() }.getOrDefault(path.trim())

    private fun text(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        return if (nodes.length == 0) null else nodes.item(0).textContent?.trim()
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        // Device descriptions come from the network: no outside documents or entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        isExpandEntityReferences = false
        isNamespaceAware = false
    }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
}

/** A TV or speaker on the Wi-Fi that plays music sent to it (a UPnP/DLNA media renderer). */
data class DlnaDevice(
    /** Its UPnP id (UDN), the same every time it's found. */
    val id: String,
    val name: String,
    val model: String?,
    /** Where play, pause, seek and stop go (AVTransport). */
    val transportUrl: String,
    /** Where volume goes (RenderingControl), when it has one. */
    val renderingUrl: String?,
)
