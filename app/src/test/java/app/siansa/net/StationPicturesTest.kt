package app.siansa.net

import app.siansa.data.Station
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A radio station's picture: Radio Browser, the station's website, or a link you entered (issue #353). */
class StationPicturesTest {
    private val rockAntenne = Station(
        id = "own-1",
        name = "Rock Antenne Heavy Metal",
        streamUrl = "http://mp3channels.webradio.rockantenne.de/heavy-metal",
    )

    private val sampleJson = """
        [
          {"changeuuid": "x", "stationuuid": "a", "name": "ROCK ANTENNE Heavy Metal",
           "url": "https://mp3channels.webradio.rockantenne.de/heavy-metal/",
           "url_resolved": "https://s1-webradio.rockantenne.de/heavy-metal/stream/mp3",
           "homepage": "https://www.rockantenne.de/", "favicon": "https://www.rockantenne.de/logos/heavy-metal.png",
           "votes": 120, "lastcheckok": 1},
          {"name": "Heavy Metal Radio", "url": "https://other.example.com/hm", "url_resolved": "", "homepage": "",
           "favicon": "", "votes": 900},
          {"name": 5, "url": null}
        ]
    """

    @Test fun radioBrowserAnswersAreRead() {
        val list = StationPictures.parseRadioBrowser(sampleJson)
        assertEquals(3, list.size)
        val first = list.first()
        assertEquals("ROCK ANTENNE Heavy Metal", first.name)
        assertEquals("https://s1-webradio.rockantenne.de/heavy-metal/stream/mp3", first.urlResolved)
        assertEquals("https://www.rockantenne.de/logos/heavy-metal.png", first.favicon)
        assertEquals("https://www.rockantenne.de/", first.homepage)
        assertEquals(120, first.votes)
        // Fields of the wrong kind are read as empty.
        assertEquals("", list[2].name)
        assertEquals(emptyList<Any>(), StationPictures.parseRadioBrowser("not json"))
        assertEquals(emptyList<Any>(), StationPictures.parseRadioBrowser("""{"error": "x"}"""))
    }

    @Test fun streamsAreTheSameWhateverTheSchemeOrTrailingSlash() {
        assertTrue(StationPictures.sameStream("http://mp3channels.webradio.rockantenne.de/heavy-metal", "https://mp3channels.webradio.rockantenne.de/heavy-metal/"))
        assertTrue(StationPictures.sameStream("http://Stream.Example.com:80/live", "https://stream.example.com/live"))
        assertTrue(StationPictures.sameStream("https://www.example.com/live#x", "http://example.com/live"))
        assertFalse(StationPictures.sameStream("https://example.com/live", "https://example.com/live2"))
        assertFalse(StationPictures.sameStream("https://example.com/Live", "https://example.com/live"))
        assertFalse(StationPictures.sameStream(null, "https://example.com/live"))
        assertFalse(StationPictures.sameStream("", ""))
    }

    @Test fun theStationIsFoundByItsStreamOrWhereItResolvesTo() {
        val list = StationPictures.parseRadioBrowser(sampleJson)
        assertEquals("ROCK ANTENNE Heavy Metal", StationPictures.pickByUrl(list, rockAntenne)?.name)
        val resolved = rockAntenne.copy(streamUrl = "https://s1-webradio.rockantenne.de/heavy-metal/stream/mp3", link = "https://x.example.com/hm.m3u")
        assertEquals("ROCK ANTENNE Heavy Metal", StationPictures.pickByUrl(list, resolved)?.name)
        assertNull(StationPictures.pickByUrl(list, rockAntenne.copy(streamUrl = "https://nope.example.com/s", link = "https://nope.example.com/s")))
    }

    @Test fun byNameOnlyAnExactNameCountsIgnoringCase() {
        val list = StationPictures.parseRadioBrowser(sampleJson)
        assertEquals("ROCK ANTENNE Heavy Metal", StationPictures.pickByName(list, "rock antenne  heavy metal")?.name)
        // "Heavy Metal" is in both names, but neither is exactly that.
        assertNull(StationPictures.pickByName(list, "Heavy Metal"))
    }

    @Test fun ofSeveralMatchesOneWithAPictureAndMoreVotesWins() {
        val a = StationPictures.RadioBrowserStation("X", "https://a.example.com/s", "", "", "", votes = 500)
        val b = StationPictures.RadioBrowserStation("x", "https://a.example.com/s", "", "https://a.example.com/b.png", "", votes = 3)
        val c = StationPictures.RadioBrowserStation("X ", "https://a.example.com/s", "", "https://a.example.com/c.png", "", votes = 30)
        assertEquals(c, StationPictures.pickByName(listOf(a, b, c), "X"))
    }

    @Test fun lookupAddressesAreTheStreamItsLinkAndTheOtherScheme() {
        val st = rockAntenne.copy(link = "https://x.example.com/hm.pls")
        assertEquals(
            listOf("http://mp3channels.webradio.rockantenne.de/heavy-metal", "https://x.example.com/hm.pls", "https://mp3channels.webradio.rockantenne.de/heavy-metal"),
            StationPictures.lookupUrls(st),
        )
        assertEquals(2, StationPictures.lookupUrls(rockAntenne).size)
    }

    @Test fun websitePicturesComeOgImageThenTouchIconThenLargestIcon() {
        val html = """
            <!doctype html><html><head>
            <link rel="icon" href="/favicon-16.png" sizes="16x16">
            <link rel="shortcut icon" href="/favicon.ico">
            <LINK REL='icon' HREF='/icons/favicon-192.png' SIZES='192x192' type='image/png'>
            <link rel="icon" type="image/svg+xml" href="/logo.svg">
            <link rel="apple-touch-icon" sizes="120x120" href="touch-120.png">
            <link rel="apple-touch-icon" sizes="180x180" href="https://cdn.example.com/touch-180.png?v=2&amp;x=1">
            <meta property="og:image" content="//cdn.example.com/og.jpg">
            <meta name="og:image" content="data:image/png;base64,AAAA">
            </head><body><img src="/not-this.png"></body></html>
        """
        assertEquals(
            listOf(
                "https://cdn.example.com/og.jpg",
                "https://cdn.example.com/touch-180.png?v=2&x=1",
                "https://radio.example.com/station/touch-120.png",
                "https://radio.example.com/icons/favicon-192.png",
                "https://radio.example.com/favicon-16.png",
                "https://radio.example.com/favicon.ico",
            ),
            StationPictures.websitePictures(html, "https://radio.example.com/station/index.html"),
        )
    }

    @Test fun aPageWithoutPicturesGivesNone() {
        assertEquals(emptyList<String>(), StationPictures.websitePictures("<html><head><title>Hi</title></head></html>", "https://a.example.com/"))
        assertEquals(emptyList<String>(), StationPictures.websitePictures("<link rel=icon href=/a.png>", "not a url"))
        assertEquals(listOf("https://a.example.com/a.png"), StationPictures.websitePictures("<link rel=icon href=/a.png>", "https://a.example.com/x"))
    }

    /** Stands in for the web: what each source answers, and which addresses are pictures. Records what was asked. */
    private class FakeSources(
        val byUrl: Map<String, List<StationPictures.RadioBrowserStation>> = emptyMap(),
        val byName: List<StationPictures.RadioBrowserStation>? = emptyList(),
        val pages: Map<String, List<String>> = emptyMap(),
        val pictures: Set<String> = emptySet(),
        val reachable: Boolean = true,
    ) : StationPictures.Sources {
        val asked = ArrayList<String>()
        override suspend fun radioBrowserByUrl(url: String) = if (!reachable) null else byUrl[url].orEmpty().also { asked += "url:$url" }
        override suspend fun radioBrowserByName(name: String) = if (!reachable) null else byName.also { asked += "name:$name" }
        override suspend fun websitePictures(page: String) = if (!reachable) null else pages[page].orEmpty().also { asked += "page:$page" }
        override suspend fun isPicture(url: String) = (url in pictures).also { asked += "picture:$url" }
    }

    private fun rb(name: String, url: String, favicon: String = "", homepage: String = "") =
        StationPictures.RadioBrowserStation(name, url, "", favicon, homepage)

    @Test fun anEnteredPictureLinkIsUsedWithoutLookingAnythingUp() = runBlocking {
        val sources = FakeSources()
        val found = StationPictures.find(rockAntenne.copy(pictureLink = "https://p.example.com/mine.png"), sources)
        assertEquals("https://p.example.com/mine.png", found.image)
        assertEquals(StationPictures.Source.LINK, found.source)
        assertEquals(emptyList<String>(), sources.asked)
    }

    @Test fun radioBrowserByAddressComesFirst() = runBlocking {
        val sources = FakeSources(
            byUrl = mapOf(rockAntenne.streamUrl to listOf(rb("Rock Antenne Heavy Metal", rockAntenne.streamUrl, "https://r.example.com/f.png", "https://www.rockantenne.de/"))),
            pictures = setOf("https://r.example.com/f.png"),
        )
        val found = StationPictures.find(rockAntenne, sources)
        assertEquals("https://r.example.com/f.png", found.image)
        assertEquals(StationPictures.Source.RADIO_BROWSER, found.source)
        // The station had no website: Radio Browser's is kept.
        assertEquals("https://www.rockantenne.de/", found.homePage)
        assertFalse(sources.asked.any { it.startsWith("name:") || it.startsWith("page:") })
    }

    @Test fun thenByNameThenTheWebsiteWhenRadioBrowsersPictureIsBroken() = runBlocking {
        val sources = FakeSources(
            byName = listOf(rb("ROCK ANTENNE HEAVY METAL", "https://elsewhere.example.com/s", "https://r.example.com/broken.png", "https://www.rockantenne.de/")),
            pages = mapOf("https://www.rockantenne.de/" to listOf("https://www.rockantenne.de/og.jpg", "https://www.rockantenne.de/touch.png")),
            pictures = setOf("https://www.rockantenne.de/touch.png"),
        )
        val found = StationPictures.find(rockAntenne, sources)
        assertEquals("https://www.rockantenne.de/touch.png", found.image)
        assertEquals(StationPictures.Source.WEBSITE, found.source)
        assertEquals(
            listOf(
                "url:http://mp3channels.webradio.rockantenne.de/heavy-metal",
                "url:https://mp3channels.webradio.rockantenne.de/heavy-metal",
                "name:Rock Antenne Heavy Metal",
                "picture:https://r.example.com/broken.png",
                "page:https://www.rockantenne.de/",
                "picture:https://www.rockantenne.de/og.jpg",
                "picture:https://www.rockantenne.de/touch.png",
            ),
            sources.asked,
        )
    }

    @Test fun theServersWebsiteIsTriedBeforeRadioBrowsers() = runBlocking {
        val st = rockAntenne.copy(homePage = "https://own.example.com/")
        val sources = FakeSources(
            byName = listOf(rb("Rock Antenne Heavy Metal", "x", homepage = "https://rb.example.com/")),
            pages = mapOf("https://own.example.com/" to listOf("https://own.example.com/i.png"), "https://rb.example.com/" to listOf("https://rb.example.com/i.png")),
            pictures = setOf("https://own.example.com/i.png", "https://rb.example.com/i.png"),
        )
        val found = StationPictures.find(st, sources)
        assertEquals("https://own.example.com/i.png", found.image)
        // It had a website already: none is given back.
        assertNull(found.homePage)
    }

    @Test fun nothingFoundIsSaidAndSoIsNothingReached() = runBlocking {
        val none = StationPictures.find(rockAntenne, FakeSources())
        assertNull(none.image)
        assertEquals(StationPictures.Source.NONE, none.source)
        assertTrue(none.answered)
        val offline = StationPictures.find(rockAntenne.copy(homePage = "https://own.example.com/"), FakeSources(reachable = false))
        assertNull(offline.image)
        assertFalse(offline.answered)
    }
}
