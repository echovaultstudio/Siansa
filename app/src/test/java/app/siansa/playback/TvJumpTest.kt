package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #901: right after a skip the TV still names the song it's leaving; the phone mustn't go back to it. */
class TvJumpTest {
    @Test fun withoutAJumpTheTvIsFollowed() {
        assertEquals(TvJump.Follow.FOLLOW, TvJump.follow(jumpingTo = null, jumpedAt = 0, reported = 5, now = 10))
    }

    @Test fun theSongBeingLeftIsIgnoredUntilTheTvGetsThere() {
        // The phone jumped to entry 7 at 1000 ms; the TV says 6 (the old one), then 7.
        assertEquals(TvJump.Follow.IGNORE, TvJump.follow(jumpingTo = 7, jumpedAt = 1_000, reported = 6, now = 1_040))
        assertEquals(TvJump.Follow.IGNORE, TvJump.follow(jumpingTo = 7, jumpedAt = 1_000, reported = null, now = 1_100))
        assertEquals(TvJump.Follow.ARRIVED, TvJump.follow(jumpingTo = 7, jumpedAt = 1_000, reported = 7, now = 1_250))
    }

    @Test fun aTvThatNeverGetsThereIsFollowedAgainAfterAWhile() {
        assertEquals(TvJump.Follow.FOLLOW, TvJump.follow(jumpingTo = 7, jumpedAt = 1_000, reported = 6, now = 1_000 + TvJump.SETTLE_MS))
    }
}
