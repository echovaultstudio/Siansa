package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One mini player, the player started last (beta #45). Each test walks a sequence from the owner's screen
 * recording or report through [activeAfter] and checks what the bar shows after every step.
 */
class ActivePlayerTest {
    private class Bar {
        var active = MiniFor.NONE
        var players = Players(false, false, false, false)
        fun step(musicLoaded: Boolean, musicPlaying: Boolean, bookLoaded: Boolean, bookPlaying: Boolean): MiniFor {
            val now = Players(musicLoaded, musicPlaying, bookLoaded, bookPlaying)
            active = activeAfter(active, players, now)
            players = now
            return miniShown(active, musicLoaded, bookLoaded)
        }
    }

    @Test fun pausingMusicKeepsMusicsMiniPlayerOverAPausedPodcast() {
        // The recording: podcast plays, music starts (podcast pauses), music paused: the bar showed the podcast.
        val bar = Bar()
        assertEquals(MiniFor.BOOK, bar.step(musicLoaded = false, musicPlaying = false, bookLoaded = true, bookPlaying = true))
        assertEquals(MiniFor.BOOK, bar.step(true, false, true, true))
        assertEquals(MiniFor.MUSIC, bar.step(true, true, true, false))
        assertEquals(MiniFor.MUSIC, bar.step(true, false, true, false))
    }

    @Test fun stoppingTheBookShowsNothingBehindIt() {
        // Music plays, a book starts (music pauses), the book is stopped: no music mini player from behind.
        val bar = Bar()
        assertEquals(MiniFor.MUSIC, bar.step(true, true, false, false))
        assertEquals(MiniFor.BOOK, bar.step(true, false, true, true))
        assertEquals(MiniFor.BOOK, bar.step(true, false, true, false))
        assertEquals(MiniFor.NONE, bar.step(true, false, false, false))
    }

    @Test fun stoppingMusicShowsNothingBehindIt() {
        val bar = Bar()
        assertEquals(MiniFor.BOOK, bar.step(false, false, true, true))
        assertEquals(MiniFor.MUSIC, bar.step(true, true, true, false))
        assertEquals(MiniFor.NONE, bar.step(false, false, true, false))
    }

    @Test fun neverTwoAtOnceWhateverTheOrder() {
        val bar = Bar()
        val seen = listOf(
            bar.step(true, true, false, false),
            bar.step(true, true, true, true),
            bar.step(true, false, true, true),
            bar.step(true, true, true, false),
            bar.step(true, false, true, false),
        )
        assertEquals(listOf(MiniFor.MUSIC, MiniFor.BOOK, MiniFor.BOOK, MiniFor.MUSIC, MiniFor.MUSIC), seen)
    }

    @Test fun startingAgainHandsItBack() {
        // Book stopped, music hidden; playing the hidden music again (or from headphones) shows it.
        val bar = Bar()
        bar.step(true, true, false, false); bar.step(true, false, true, true); bar.step(true, false, false, false)
        assertEquals(MiniFor.MUSIC, bar.step(true, true, false, false))
    }

    @Test fun yesterdaysQueueShowsWhenTheAppOpens() {
        val bar = Bar()
        assertEquals(MiniFor.NONE, bar.step(false, false, false, false))
        assertEquals(MiniFor.MUSIC, bar.step(true, false, false, false))
    }

    @Test fun aQueueAlreadyThereDoesntComeBackOnItsOwn() {
        // Only a queue that newly appears shows by itself; one that was hidden behind a stopped book doesn't.
        assertEquals(MiniFor.NONE, activeAfter(MiniFor.NONE, Players(true, false, true, false), Players(true, false, false, false)))
    }

    @Test fun nothingLoadedShowsNothing() {
        assertEquals(MiniFor.NONE, miniShown(MiniFor.MUSIC, musicLoaded = false, bookLoaded = true))
        assertEquals(MiniFor.NONE, miniShown(MiniFor.BOOK, musicLoaded = true, bookLoaded = false))
        assertEquals(MiniFor.NONE, miniShown(MiniFor.NONE, musicLoaded = true, bookLoaded = true))
    }
}
