package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.Station
import app.siansa.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * A radio station's picture (issue #353), looked for once and kept with the station:
 *
 * 1. A picture link you entered when editing the station: used as it is, nothing is looked up.
 * 2. Radio Browser (radio-browser.info, a free, open list of stations): by the station's address, then by its exact
 *    name. Its `favicon` is the picture; its `homepage` is kept when the station has none.
 * 3. The station's website (from your server, or from Radio Browser): its `og:image`, then `apple-touch-icon`, then
 *    its largest `<link rel=icon>`.
 *
 * Each picture found is checked to be one (an image that isn't too big) before it's taken. Only the station's address
 * or name goes to Radio Browser; websites get a normal page request.
 */
object StationPictures {
    /** A station in Radio Browser's answer: only what's used here. */
    data class RadioBrowserStation(
        val name: String,
        val url: String,
        val urlResolved: String,
        val favicon: String,
        val homepage: String,
        val votes: Int = 0,
    )

    /** Where a picture came from, for the log. */
    enum class Source(val words: String) {
        LINK("your picture link"),
        RADIO_BROWSER("Radio Browser"),
        WEBSITE("the station's website"),
        NONE("nowhere"),
    }

    /**
     * What a lookup found: the [image] (null when none), a [homePage] the station didn't have, and whether any
     * service [answered], so a lookup that couldn't reach anything is tried again another time.
     */
    data class Found(val image: String?, val homePage: String?, val source: Source, val answered: Boolean)

    /** Where pictures are looked for; null answers mean it couldn't be reached. Tests stand in for the web. */
    interface Sources {
        suspend fun radioBrowserByUrl(url: String): List<RadioBrowserStation>?
        suspend fun radioBrowserByName(name: String): List<RadioBrowserStation>?
        suspend fun websitePictures(page: String): List<String>?
        suspend fun isPicture(url: String): Boolean
    }

    /** Looks for [station]'s picture in the order above. */
    suspend fun find(station: Station, sources: Sources): Found {
        station.pictureLink?.takeIf { it.isNotBlank() }?.let { return Found(it, null, Source.LINK, true) }
        var answered = false
        var rb: RadioBrowserStation? = null
        for (url in lookupUrls(station)) {
            val list = sources.radioBrowserByUrl(url) ?: continue
            answered = true
            rb = pickByUrl(list, station)
            if (rb != null) break
        }
        if (rb == null && station.name.isNotBlank()) {
            sources.radioBrowserByName(station.name)?.let { list ->
                answered = true
                rb = pickByName(list, station.name)
            }
        }
        val rbHome = rb?.homepage?.trim()?.takeIf { StreamLinks.isWeb(it) }
        val newHome = if (station.homePage == null) rbHome else null
        rb?.favicon?.trim()?.takeIf { usable(it) }?.let { icon ->
            if (sources.isPicture(icon)) return Found(icon, newHome, Source.RADIO_BROWSER, true)
        }
        for (page in listOfNotNull(station.homePage, rbHome).distinct()) {
            val pictures = sources.websitePictures(page) ?: continue
            answered = true
            pictures.firstOrNull { sources.isPicture(it) }?.let { return Found(it, newHome, Source.WEBSITE, true) }
        }
        return Found(null, newHome, Source.NONE, answered)
    }

    /** The addresses asked for at Radio Browser: the stream, the link it came from, then the stream over the other scheme. */
    fun lookupUrls(station: Station): List<String> {
        val swapped = when {
            station.streamUrl.startsWith("http://", ignoreCase = true) -> "https://" + station.streamUrl.substring(7)
            station.streamUrl.startsWith("https://", ignoreCase = true) -> "http://" + station.streamUrl.substring(8)
            else -> null
        }
        return listOfNotNull(station.streamUrl, station.link, swapped).distinctBy { it.trim() }
    }

    /**
     * An address to compare streams by: without http/https, `www.`, default ports, a fragment and trailing slashes; the
     * host in lower case. `http://Stream.example.com:80/live/` and `https://stream.example.com/live` are the same.
     */
    fun streamKey(url: String): String {
        val t = url.trim().substringBefore('#')
        val rest = t.substringAfter("://", t)
        val hostEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val host = rest.substring(0, hostEnd).lowercase().removeSuffix(":80").removeSuffix(":443").removePrefix("www.")
        val path = rest.substring(hostEnd).trimEnd('/').removeSuffix("?").trimEnd('/')
        return host + path
    }

    /** Whether two stream addresses are the same station (see [streamKey]). */
    fun sameStream(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && streamKey(a) == streamKey(b)

    /** The station in Radio Browser's answer whose address (as listed, or as it resolves to) is [station]'s. */
    fun pickByUrl(list: List<RadioBrowserStation>, station: Station): RadioBrowserStation? {
        val mine = listOf(station.streamUrl, station.link)
        return best(list.filter { rb -> mine.any { sameStream(it, rb.url) || sameStream(it, rb.urlResolved) } })
    }

    /** The station in Radio Browser's answer named exactly [name] (ignoring case and extra spaces), if there is one. */
    fun pickByName(list: List<RadioBrowserStation>, name: String): RadioBrowserStation? {
        val wanted = plainName(name)
        return best(list.filter { plainName(it.name) == wanted })
    }

    private fun plainName(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** One with a picture first, then the one with the most votes. */
    private fun best(list: List<RadioBrowserStation>): RadioBrowserStation? =
        list.sortedWith(compareByDescending<RadioBrowserStation> { usable(it.favicon.trim()) }.thenByDescending { it.votes }).firstOrNull()

    /** Radio Browser's station list, as `/json/stations/...` answers; empty when it can't be read. */
    fun parseRadioBrowser(json: String): List<RadioBrowserStation> {
        val array = runCatching { AppJson.parseToJsonElement(json) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            RadioBrowserStation(
                name = s("name"),
                url = s("url"),
                urlResolved = s("url_resolved"),
                favicon = s("favicon"),
                homepage = s("homepage"),
                votes = (o["votes"] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }
    }

    /**
     * Pictures named in a web page's HTML, best first: `og:image`, then `apple-touch-icon` (largest first), then
     * `<link rel=icon>` (largest first). Relative addresses are made whole against [pageUrl]; SVGs and `data:` pictures
     * are left out (they can't be shown everywhere a cover is).
     */
    fun websitePictures(html: String, pageUrl: String): List<String> {
        val base = pageUrl.toHttpUrlOrNull() ?: return emptyList()
        val og = ArrayList<String>()
        val touch = ArrayList<Pair<Int, String>>()
        val icons = ArrayList<Pair<Int, String>>()
        TAG.findAll(html).forEach { m ->
            val tag = m.groupValues[1].lowercase()
            val attrs = attributes(m.groupValues[2])
            if (tag == "meta") {
                val key = (attrs["property"] ?: attrs["name"])?.lowercase()?.trim()
                if (key in OG_KEYS) attrs["content"]?.let { og += it }
            } else {
                val rel = attrs["rel"]?.lowercase()?.split(Regex("\\s+")).orEmpty()
                val href = attrs["href"] ?: return@forEach
                if (attrs["type"]?.lowercase()?.contains("svg") == true) return@forEach
                val size = sizeOf(attrs["sizes"])
                when {
                    rel.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" } -> touch += size to href
                    "icon" in rel -> icons += size to href
                }
            }
        }
        val ordered = og + touch.sortedByDescending { it.first }.map { it.second } + icons.sortedByDescending { it.first }.map { it.second }
        return ordered.mapNotNull { resolve(base, entities(it.trim())) }.filter { usable(it) }.distinct()
    }

    /** An http(s) picture that isn't an SVG. */
    private fun usable(url: String): Boolean =
        StreamLinks.isWeb(url) && !url.substringBefore('?').lowercase().endsWith(".svg")

    private fun resolve(base: HttpUrl, href: String): String? {
        if (href.isEmpty() || href.startsWith("data:", ignoreCase = true)) return null
        return base.resolve(href)?.toString()
    }

    /** The largest side in a `sizes` attribute ("32x32 192x192"); 0 when there's none. */
    private fun sizeOf(sizes: String?): Int =
        sizes.orEmpty().lowercase().split(Regex("\\s+")).mapNotNull { it.substringBefore('x').toIntOrNull() }.maxOrNull() ?: 0

    private fun attributes(text: String): Map<String, String> =
        ATTR.findAll(text).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty())
        }

    private fun entities(text: String): String = text
        .replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x2F;", "/", ignoreCase = true).replace("&#47;", "/")

    private val TAG = Regex("<(meta|link)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val OG_KEYS = setOf("og:image", "og:image:url", "og:image:secure_url")
}

/**
 * [StationPictures.Sources] on the web, through the app's HTTP client with short time limits. Radio Browser is asked
 * at `all.api.radio-browser.info` (which picks a working server), then at its servers by name when that fails; the one
 * that answered is asked first next time. Websites are read only as far as [PAGE_LIMIT], pictures only checked.
 */
class WebStationPictures(http: OkHttpClient, private val userAgent: String) : StationPictures.Sources {
    private val client = http.newBuilder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build()

    @Volatile private var goodHost: String? = null

    override suspend fun radioBrowserByUrl(url: String): List<StationPictures.RadioBrowserStation>? =
        radioBrowser { host -> base(host).addPathSegments("json/stations/byurl").addQueryParameter("url", url) }

    override suspend fun radioBrowserByName(name: String): List<StationPictures.RadioBrowserStation>? =
        radioBrowser { host ->
            base(host).addPathSegments("json/stations/byname").addPathSegment(name.trim())
                .addQueryParameter("limit", "100").addQueryParameter("order", "votes").addQueryParameter("reverse", "true")
        }

    private fun base(host: String) = HttpUrl.Builder().scheme("https").host(host)

    private suspend fun radioBrowser(build: (String) -> HttpUrl.Builder): List<StationPictures.RadioBrowserStation>? = withContext(Dispatchers.IO) {
        val hosts = listOfNotNull(goodHost) + RADIO_BROWSER_HOSTS.filter { it != goodHost }
        for (host in hosts) {
            val url = build(host).build()
            try {
                val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
                val text = client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) throw ApiException("HTTP ${r.code}")
                    r.body?.string().orEmpty()
                }
                goodHost = host
                return@withContext StationPictures.parseRadioBrowser(text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.d("Stations", "Radio Browser at $host didn't answer: ${e.message}")
            }
        }
        null
    }

    override suspend fun websitePictures(page: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(page).header("User-Agent", userAgent).header("Accept", "text/html").build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val type = r.header("Content-Type").orEmpty().lowercase()
                if (type.isNotEmpty() && !type.contains("html")) return@withContext emptyList()
                val body = r.body ?: return@withContext emptyList()
                val source = body.source()
                source.request(PAGE_LIMIT)
                val text = source.buffer.readString(minOf(PAGE_LIMIT, source.buffer.size), body.contentType()?.charset() ?: Charsets.UTF_8)
                StationPictures.websitePictures(text, r.request.url.toString())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLog.d("Stations", "Website ${AppLog.redact(page)} not read: ${e.message}")
            null
        }
    }

    override suspend fun isPicture(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { r ->
                val type = r.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                val length = r.body?.contentLength() ?: -1L
                r.isSuccessful && type.startsWith("image/") && !type.contains("svg") && length <= PICTURE_LIMIT
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    companion object {
        private const val CALL_TIMEOUT_S = 12L
        /** Only the start of a website is read: its <head> is where pictures are named. */
        private const val PAGE_LIMIT = 512L * 1024
        /** Pictures bigger than this aren't taken as a station's cover. */
        private const val PICTURE_LIMIT = 4L * 1024 * 1024
        val RADIO_BROWSER_HOSTS = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")
    }
}
