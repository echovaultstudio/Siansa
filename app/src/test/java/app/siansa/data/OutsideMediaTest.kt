package app.siansa.data

import app.siansa.net.OutsideSource
import app.siansa.playback.Crossfade
import app.siansa.scrobble.scrobblesSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stations and podcast episodes as songs in the one player (issue #353). */
class OutsideMediaTest {
    private val station = Station(id = "own-1", name = "Radio Paradise", streamUrl = "https://stream.radioparadise.com/aac-320", image = "https://rp.example.com/logo.png")
    private val feed = PodcastFeed(id = "pod-1", feedUrl = "https://f.example.com/rss", title = "Rocket Hour", image = "https://f.example.com/c.jpg")
    private val episode = Episode(id = "e1", title = "Ep. 1", url = "https://cdn.example.com/1.mp3", durationSec = 3600)

    @Test fun aStationIsALiveSongWithItsAddressInItsId() {
        val song = OutsideMedia.liveSong(station)
        assertEquals("live:https://stream.radioparadise.com/aac-320", song.id)
        assertEquals("Radio Paradise", song.title)
        assertEquals(OutsideMedia.LIVE_ARTIST, song.artist)
        assertEquals("web:https://rp.example.com/logo.png", song.coverArt)
        assertTrue(OutsideMedia.isLive(song.id))
        assertEquals(station.streamUrl, OutsideMedia.addressOf(song.id))
    }

    @Test fun anEpisodeIsASongOfItsPodcast() {
        val song = OutsideMedia.episodeSong(feed, episode)
        assertEquals("ep:https://cdn.example.com/1.mp3", song.id)
        assertEquals("Rocket Hour", song.artist)
        assertEquals(3600, song.durationSec)
        // No picture of its own: the podcast's.
        assertEquals("web:https://f.example.com/c.jpg", song.coverArt)
        assertTrue(OutsideMedia.isEpisode(song.id))
        assertFalse(OutsideMedia.isLive(song.id))
    }

    @Test fun serverSongsAreNotOutside() {
        assertFalse(OutsideMedia.isOutside("tr-12345"))
        assertFalse(OutsideMedia.isOutside(null))
        assertEquals(null, OutsideMedia.addressOf("tr-12345"))
        assertFalse(OutsideSource.handles("al-1"))
        assertTrue(OutsideSource.handles("web:https://x.example.com/a.png"))
        assertTrue(OutsideSource.handles("live:https://x.example.com/s"))
    }

    @Test fun thePlayersAddressCarriesAMarkThatIsNeverSent() {
        val url = OutsideMedia.forPlayer("https://s.example.com/live?x=1#old")
        assertEquals("https://s.example.com/live?x=1#siansa-outside", url)
        assertTrue(OutsideMedia.isMarked(url))
        assertEquals("https://s.example.com/live?x=1", OutsideMedia.unmarked(url))
        // A reload marker added after it (Streaming.withReload) doesn't hide it.
        assertTrue(OutsideMedia.isMarked("$url&siansaReload=2"))
        assertEquals("https://s.example.com/live?x=1", OutsideMedia.unmarked("$url&siansaReload=2"))
        assertFalse(OutsideMedia.isMarked("https://music.example.com/rest/stream?id=1"))
    }

    @Test fun aStationShowsWhatItsStreamSaysIsPlaying() {
        val song = OutsideMedia.liveSong(station)
        val now = OutsideMedia.nowOnAir(song, "Fontaines D.C. - Romance")
        assertEquals("Fontaines D.C. - Romance", now.title)
        assertEquals("Radio Paradise", now.artist)
        assertEquals(song.id, now.id)
        // Nothing (or only the station's own name) sent: the station as it is.
        assertSame(song, OutsideMedia.nowOnAir(song, null))
        assertSame(song, OutsideMedia.nowOnAir(song, "  "))
        assertSame(song, OutsideMedia.nowOnAir(song, "Radio Paradise"))
        // Only live stations: an episode keeps its own title.
        val ep = OutsideMedia.episodeSong(feed, episode)
        assertSame(ep, OutsideMedia.nowOnAir(ep, "Something"))
    }

    @Test fun stationsAndEpisodesNeverBlendOrScrobble() {
        val live = OutsideMedia.liveSong(station).id
        val ep = OutsideMedia.episodeSong(feed, episode).id
        assertTrue(Crossfade.blends("tr-1", "tr-2"))
        assertFalse(Crossfade.blends("tr-1", live))
        assertFalse(Crossfade.blends(live, "tr-1"))
        assertFalse(Crossfade.blends(ep, ep + "x"))
        assertTrue(scrobblesSong("tr-1"))
        assertFalse(scrobblesSong(live))
        assertFalse(scrobblesSong(ep))
    }

    @Test fun aPlaylistAddressIsLookedInto() {
        assertTrue(StationRepository.looksLikePlaylist("https://somafm.com/groovesalad.pls"))
        assertTrue(StationRepository.looksLikePlaylist("https://x.example.com/radio.m3u?token=1"))
        assertFalse(StationRepository.looksLikePlaylist("https://stream.example.com/live.mp3"))
        assertFalse(StationRepository.looksLikePlaylist("https://stream.example.com/hls/live.m3u8"))
        assertEquals("radioparadise.com", StationRepository.nameFrom("https://www.radioparadise.com:443/aac"))
    }
}
