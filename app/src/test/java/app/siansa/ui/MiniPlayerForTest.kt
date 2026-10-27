package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which mini player shows (beta #44): the playing one, else the one played last, else whatever is loaded. */
class MiniPlayerForTest {
    @Test fun aBookPlayingShowsOverAPausedSong() {
        // The report: a song paused when the book started kept its mini player over the book's.
        assertEquals(MiniFor.BOOK, miniPlayerFor(musicLoaded = true, musicPlaying = false, bookLoaded = true, bookPlaying = true, last = MiniFor.MUSIC))
    }

    @Test fun musicPlayingShowsOverAPausedBook() {
        assertEquals(MiniFor.MUSIC, miniPlayerFor(musicLoaded = true, musicPlaying = true, bookLoaded = true, bookPlaying = false, last = MiniFor.BOOK))
    }

    @Test fun bothPausedShowsTheOnePlayedLast() {
        assertEquals(MiniFor.BOOK, miniPlayerFor(true, false, true, false, MiniFor.BOOK))
        assertEquals(MiniFor.MUSIC, miniPlayerFor(true, false, true, false, MiniFor.MUSIC))
    }

    @Test fun onlyOneLoadedShowsThatOne() {
        assertEquals(MiniFor.BOOK, miniPlayerFor(false, false, true, false, MiniFor.MUSIC))
        assertEquals(MiniFor.MUSIC, miniPlayerFor(true, false, false, false, MiniFor.BOOK))
        assertEquals(MiniFor.NONE, miniPlayerFor(false, false, false, false, MiniFor.BOOK))
    }
}
