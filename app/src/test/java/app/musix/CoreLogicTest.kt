package app.musix

import app.musix.covers.CoverDownload
import app.musix.covers.CoverStore
import app.musix.data.AddressException
import app.musix.data.Album
import app.musix.data.AlbumCategory
import app.musix.data.AppSettings
import app.musix.data.HomeSection
import app.musix.data.moved
import app.musix.data.ReplayGainMode
import app.musix.data.Song
import app.musix.data.category
import app.musix.data.cover
import app.musix.data.normalizeKey
import app.musix.data.parseAddress
import app.musix.playback.replayGainVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/** Server addresses as people paste them. */
class AddressTest {
    @Test fun addsSchemeAndKeepsPort() {
        assertEquals("http://192.168.1.10:4533", parseAddress("192.168.1.10:4533").url)
    }

    @Test fun dropsTrailingSlashAndWebAppPath() {
        assertEquals("https://music.example.com", parseAddress("https://music.example.com/app/#/album/123").url)
        assertEquals("https://music.example.com/navidrome", parseAddress("https://music.example.com/navidrome/").url)
    }

    @Test fun readsLoginWithAtSignInPassword() {
        val a = parseAddress("https://max:p%40ss@music.example.com")
        assertEquals("https://music.example.com", a.url)
        assertEquals("max", a.user)
        assertEquals("p@ss", a.password)
    }

    @Test fun emptyIsAllowed() {
        assertEquals("", parseAddress("  ").url)
    }

    @Test(expected = AddressException::class)
    fun rejectsNonNumericPort() {
        parseAddress("192.168.1.10:port")
    }
}

/** Recognising covers from their first bytes, which decides whether an animated original is kept. */
class CoverSniffTest {
    private fun webp(animated: Boolean, w: Int, h: Int): ByteArray {
        val b = ByteArray(30)
        "RIFF".forEachIndexed { i, c -> b[i] = c.code.toByte() }
        "WEBP".forEachIndexed { i, c -> b[8 + i] = c.code.toByte() }
        "VP8X".forEachIndexed { i, c -> b[12 + i] = c.code.toByte() }
        b[20] = if (animated) 0x02 else 0
        fun put24(at: Int, v: Int) { b[at] = (v and 0xFF).toByte(); b[at + 1] = (v shr 8 and 0xFF).toByte(); b[at + 2] = (v shr 16 and 0xFF).toByte() }
        put24(24, w - 1)
        put24(27, h - 1)
        return b
    }

    @Test fun animatedWebpWithSize() {
        val s = CoverStore.sniff(webp(true, 1080, 1080))!!
        assertTrue(s.animated)
        assertEquals("animated WebP", s.kind)
        assertEquals(1080, s.width)
        assertEquals(1080, s.height)
    }

    @Test fun stillWebp() {
        assertFalse(CoverStore.sniff(webp(false, 600, 600))!!.animated)
    }

    @Test fun gifSizeFromHeader() {
        val b = ByteArray(16)
        "GIF89a".forEachIndexed { i, c -> b[i] = c.code.toByte() }
        b[6] = 0xE8.toByte(); b[7] = 0x03 // 1000
        b[8] = 0x2C; b[9] = 0x01 // 300
        val s = CoverStore.sniff(b)!!
        assertEquals("GIF", s.kind)
        assertEquals(1000, s.width)
        assertEquals(300, s.height)
    }

    @Test fun jpegAndPngAreStill() {
        assertFalse(CoverStore.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))!!.animated)
        assertFalse(CoverStore.sniff(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))!!.animated)
    }

    @Test fun errorMessageIsNotAnImage() {
        assertNull(CoverStore.sniff("{\"subsonic-response\":".toByteArray()))
    }

    @Test fun coverIdsShareTheirAlbum() {
        assertEquals("al-abc", CoverStore.canonical("al-abc_e7605fcd483c77e4"))
        assertEquals("al-abc", CoverStore.canonical("al-abc"))
    }
}

/** ReplayGain as player volume. */
class ReplayGainTest {
    private val song = Song(id = "1", title = "t", trackGain = -6f, albumGain = -9f, trackPeak = 0.5f, albumPeak = 0.5f)

    @Test fun trackGain() {
        assertEquals(0.501f, replayGainVolume(song, AppSettings(replayGain = ReplayGainMode.TRACK)), 0.005f)
    }

    @Test fun albumGain() {
        assertEquals(0.355f, replayGainVolume(song, AppSettings(replayGain = ReplayGainMode.ALBUM)), 0.005f)
    }

    @Test fun offIsFullVolume() {
        assertEquals(1f, replayGainVolume(song, AppSettings(replayGain = ReplayGainMode.OFF)), 0f)
    }

    @Test fun neverAboveFullVolume() {
        val loud = song.copy(trackGain = 6f, trackPeak = null)
        assertEquals(1f, replayGainVolume(loud, AppSettings(replayGain = ReplayGainMode.TRACK)), 0f)
    }

    @Test fun peakKeepsItFromClipping() {
        val peaky = song.copy(trackGain = 0f, trackPeak = 1.25f)
        assertEquals(0.8f, replayGainVolume(peaky, AppSettings(replayGain = ReplayGainMode.TRACK)), 0.001f)
    }
}

/** Matching names across Navidrome, Koito and Last.fm. */
class NormalizeTest {
    @Test fun accentsCaseAndPunctuation() {
        assertEquals("beyonce", normalizeKey("Beyoncé"))
        assertEquals(normalizeKey("AC/DC"), normalizeKey("ac dc"))
        assertEquals("sigur ros", normalizeKey("  Sigur Rós! "))
    }
}

/** Release types from tags, for the library's sub-filters. */
class AlbumCategoryTest {
    private fun album(vararg types: String, compilation: Boolean = false) =
        Album(id = "a", name = "n", releaseTypes = types.toList(), isCompilation = compilation)

    @Test fun plainAlbum() = assertEquals(AlbumCategory.ALBUM, album().category)
    @Test fun ep() = assertEquals(AlbumCategory.EP, album("ep").category)
    @Test fun liveWinsOverAlbum() = assertEquals(AlbumCategory.LIVE, album("album", "live").category)
    @Test fun oneStringWithSeveralTypes() = assertEquals(AlbumCategory.LIVE, album("Album; Live").category)
    @Test fun compilationFlag() = assertEquals(AlbumCategory.COMPILATION, album(compilation = true).category)
    @Test fun unknownType() = assertEquals(AlbumCategory.OTHER, album("weird").category)
}

/** Song covers fall back to the album's. */
class SongCoverTest {
    @Test fun ownCoverFirst() = assertEquals("mf-1", Song(id = "1", title = "t", coverArt = "mf-1", albumId = "x").cover)
    @Test fun albumWhenMissing() = assertEquals("al-x", Song(id = "1", title = "t", albumId = "x").cover)
    @Test fun nothingAtAll() = assertNull(Song(id = "1", title = "t").cover)
}

/** "Home network" check for downloading all covers. */
class SubnetTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    @Test fun sameHomeNetwork() = assertTrue(CoverDownload.sameSubnet(ip("192.168.1.20"), 24, ip("192.168.1.106")))
    @Test fun otherNetwork() = assertFalse(CoverDownload.sameSubnet(ip("192.168.1.20"), 24, ip("192.168.2.106")))
    @Test fun widerPrefix() = assertTrue(CoverDownload.sameSubnet(ip("10.0.5.1"), 16, ip("10.0.200.9")))
    @Test fun oddPrefix() = assertFalse(CoverDownload.sameSubnet(ip("192.168.1.20"), 28, ip("192.168.1.106")))
    @Test fun ipv4AgainstIpv6() = assertFalse(CoverDownload.sameSubnet(ip("192.168.1.20"), 24, ip("fe80::1")))
}

/** The order of Home's sections, as saved under Arrange Home. */
class HomeLayoutTest {
    @Test fun nothingSavedGivesTheDefaultOrder() {
        assertEquals(HomeSection.default, HomeSection.parse(null))
        assertEquals(HomeSection.default, HomeSection.parse(""))
    }

    @Test fun savedOrderRoundTrips() {
        val order = HomeSection.default.reversed()
        assertEquals(order, HomeSection.parse(HomeSection.format(order)))
    }

    @Test fun unknownAndRepeatedIdsAreDropped() {
        val order = HomeSection.parse("random,gone,random," + HomeSection.format(HomeSection.default))
        assertEquals(HomeSection.entries.size, order.size)
        assertEquals(HomeSection.RANDOM, order.first())
    }

    @Test fun sectionsMissingFromAnOlderSaveGoBackAfterTheirNeighbour() {
        // Saved before "Radios" existed, with Rediscover moved to the top.
        val saved = HomeSection.default.filter { it != HomeSection.RADIOS }.let { listOf(HomeSection.REDISCOVER) + (it - HomeSection.REDISCOVER) }
        val order = HomeSection.parse(HomeSection.format(saved))
        assertEquals(HomeSection.REDISCOVER, order[0])
        assertEquals(order.indexOf(HomeSection.REDISCOVER) + 1, order.indexOf(HomeSection.RADIOS))
        assertEquals(HomeSection.entries.size, order.toSet().size)
    }

    @Test fun missingFirstSectionGoesToTheTop() {
        val order = HomeSection.parse(HomeSection.format(HomeSection.default.drop(1)))
        assertEquals(HomeSection.default, order)
    }

    @Test fun nothingSavedHidesNothing() {
        assertEquals(emptySet<HomeSection>(), HomeSection.parseHidden(null))
        assertEquals(emptySet<HomeSection>(), HomeSection.parseHidden(""))
        assertEquals(HomeSection.default, HomeSection.visible(HomeSection.default, HomeSection.parseHidden(null)))
    }

    @Test fun hiddenSectionsRoundTrip() {
        val hidden = setOf(HomeSection.GENRES, HomeSection.RADIOS)
        assertEquals("radios,genres", HomeSection.formatHidden(hidden))
        assertEquals(hidden, HomeSection.parseHidden(HomeSection.formatHidden(hidden)))
        assertEquals("", HomeSection.formatHidden(emptySet()))
    }

    @Test fun unknownHiddenIdsAreDropped() {
        assertEquals(setOf(HomeSection.MOOD), HomeSection.parseHidden("gone, mood ,mood,"))
    }

    @Test fun hiddenSectionsAreLeftOutOfHomeInOrder() {
        val order = HomeSection.default.reversed()
        val visible = HomeSection.visible(order, setOf(HomeSection.QUICK, HomeSection.RANDOM))
        assertEquals(order.size - 2, visible.size)
        assertFalse(HomeSection.QUICK in visible)
        assertFalse(HomeSection.RANDOM in visible)
        assertEquals(order.filter { it != HomeSection.QUICK && it != HomeSection.RANDOM }, visible)
    }

    @Test fun everySectionHiddenLeavesHomeEmpty() {
        assertTrue(HomeSection.visible(HomeSection.default, HomeSection.entries.toSet()).isEmpty())
    }

    @Test fun movingAnItem() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), list.moved(3, 0))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(5, 0))
    }
}
