package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What now playing names at the top (the owner, 18 Nov: a mix showed "Playing from album" and the song's album). */
class PlayingFromTest {
    private var generation = 0L
    private val from = PlayingFrom { generation }

    @Test fun eachPageSaysWhatItIs() {
        assertEquals(PlayingFrom.Kind.MIX, PlayingFrom.kindOf("mix/beats-abc123"))  // a Siansa Beats mix, kept as a mix
        assertEquals(PlayingFrom.Kind.MIX, PlayingFrom.kindOf("mix/2026-11-18-evening"))  // the phone's own
        assertEquals(PlayingFrom.Kind.MIX, PlayingFrom.kindOf("carousel/c1"))
        assertEquals(PlayingFrom.Kind.PLAYLIST, PlayingFrom.kindOf("playlist/p1"))
        assertEquals(PlayingFrom.Kind.PLAYLIST, PlayingFrom.kindOf("liked"))
        assertEquals(PlayingFrom.Kind.RADIO, PlayingFrom.kindOf("radio/genre/Rock"))
        assertEquals(PlayingFrom.Kind.ARTIST, PlayingFrom.kindOf("artist/a1"))
        assertEquals(PlayingFrom.Kind.OTHER, PlayingFrom.kindOf("pinned/c9"))
        assertNull(PlayingFrom.kindOf("album/al1"))
        assertEquals("PLAYING FROM MIX", PlayingFrom.Kind.MIX.label)
    }

    @Test fun namedWhileItsListPlays() {
        generation = 4
        from.note("mix/beats-abc123", "Kinetic Pulse Flow")
        assertEquals("Kinetic Pulse Flow", from.live(hasMedia = true)?.title)
        assertEquals("mix/beats-abc123", from.live(hasMedia = true)?.route)
        assertNull("nothing playing", from.live(hasMedia = false))
        // A new list played from anywhere else: the album shows again.
        generation = 5
        assertNull(from.live(hasMedia = true))
        // An album's page played: nothing to name, and the mix before is forgotten.
        from.note("mix/m1", "Evening")
        from.note("album/al1", "Silent Attraction")
        assertNull(from.live(hasMedia = true))
    }
}
