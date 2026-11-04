package app.siansa.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the phone asks the TV again for its queue (issue #114): 0.52.0-dev, after a new queue went to the TV, the phone
 * saw none (no mini player) while the TV played on.
 */
class CastQueueCheckTest {
    @Test fun anEmptyQueueOnThePhoneWhileTheTvPlaysIsStale() {
        assertTrue(CastQueueCheck.stale(phoneCount = 0, tvCount = 40, tvHasMedia = true))
        assertTrue(CastQueueCheck.stale(phoneCount = 0, tvCount = 0, tvHasMedia = true))
    }

    @Test fun aDifferentNumberOfSongsIsStale() {
        assertTrue(CastQueueCheck.stale(phoneCount = 39, tvCount = 40, tvHasMedia = true))
    }

    @Test fun inStepOrNothingAnywhereIsFine() {
        assertFalse(CastQueueCheck.stale(phoneCount = 40, tvCount = 40, tvHasMedia = true))
        assertFalse(CastQueueCheck.stale(phoneCount = 0, tvCount = 0, tvHasMedia = false))
        // The TV's list not fetched yet, but the phone has the queue: nothing to ask.
        assertFalse(CastQueueCheck.stale(phoneCount = 40, tvCount = 0, tvHasMedia = true))
    }
}
