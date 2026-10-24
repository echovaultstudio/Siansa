package app.siansa

import app.siansa.data.newCover
import app.siansa.ui.RadioMore
import app.siansa.ui.radioMore
import org.junit.Assert.assertEquals
import org.junit.Test

/** Beta report 95a30366: an artist radio's later songs, and artist photos on Home. */
class RadioLaterSongsTest {
    @Test fun laterSongsJoinOnlyTheirOwnRadio() {
        // Still the radio's own list: added after its first songs.
        assertEquals(RadioMore.ADD, radioMore(startedEmpty = false, restEmpty = false, startedAt = 4, now = 4))
        // Another radio started, or playback was stopped (long-press), meanwhile: dropped, nothing comes back.
        assertEquals(RadioMore.DROP, radioMore(startedEmpty = false, restEmpty = false, startedAt = 4, now = 5))
        assertEquals(RadioMore.DROP, radioMore(startedEmpty = true, restEmpty = false, startedAt = 4, now = 6))
        // A radio that had nothing to start with plays its later songs, unless something else started.
        assertEquals(RadioMore.PLAY, radioMore(startedEmpty = true, restEmpty = false, startedAt = 4, now = 4))
        assertEquals(RadioMore.NOTHING, radioMore(startedEmpty = false, restEmpty = true, startedAt = 4, now = 4))
    }

    @Test fun anArtistPhotoIsKeptWhenTheServerHasANewOne() {
        assertEquals(true, newCover("ar-02GG", "ar-02GG_16624aa63147eb28"))
        assertEquals(true, newCover(null, "ar-02GG_1"))
        assertEquals(false, newCover("ar-02GG_1", "ar-02GG_1"))
        assertEquals(false, newCover("ar-02GG", null))
        assertEquals(false, newCover("ar-02GG", ""))
    }
}
