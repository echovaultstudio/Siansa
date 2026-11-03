package app.siansa.playback

import app.siansa.playback.CastScreens.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which TV screen each Cast device gets (issue #114): Siansa's where it can start, Google's everywhere else. */
class CastScreensTest {
    private val own = "14717CD8"

    @Test fun aTvOfferedSiansasScreenGetsIt() {
        assertEquals(CastScreens.Choice(own, Why.Own), CastScreens(own).pick("tv-1", ownOffered = true))
    }

    @Test fun aTvNotOfferedSiansasScreenGetsGooglesSoCastingStillWorks() {
        // Unpublished Cast app, TV not registered: Cast doesn't list Siansa's screen for it.
        assertEquals(CastScreens.Choice(CastReceiver.DEFAULT, Why.NotOffered), CastScreens(own).pick("shield", ownOffered = false))
    }

    @Test fun aBuildWithoutItsOwnScreenAlwaysUsesGooglesScreen() {
        val screens = CastScreens(CastReceiver.DEFAULT)
        assertEquals(CastScreens.Choice(CastReceiver.DEFAULT, Why.NoOwnScreen), screens.pick("tv-1", ownOffered = true))
        assertFalse(screens.startFailed("tv-1", CastReceiver.DEFAULT, cancelled = false))
    }

    @Test fun whenSiansasScreenFailsToStartTheTvGetsGooglesFromThenOn() {
        val screens = CastScreens(own)
        assertTrue(screens.startFailed("tv-1", own, cancelled = false))
        assertEquals(CastScreens.Choice(CastReceiver.DEFAULT, Why.FailedBefore), screens.pick("tv-1", ownOffered = true))
        // Other TVs still get Siansa's screen.
        assertEquals(CastScreens.Choice(own, Why.Own), screens.pick("tv-2", ownOffered = true))
    }

    @Test fun googlesScreenFailingIsntRetried() {
        val screens = CastScreens(own)
        assertFalse(screens.startFailed("tv-1", CastReceiver.DEFAULT, cancelled = false))
        assertEquals(Why.Own, screens.pick("tv-1", ownOffered = true).why)
    }

    @Test fun cancellingTheCastIsntAFailure() {
        val screens = CastScreens(own)
        assertFalse(screens.startFailed("tv-1", own, cancelled = true))
        assertEquals(Why.Own, screens.pick("tv-1", ownOffered = true).why)
    }

    @Test fun anUnknownTvIsntRetried() {
        val screens = CastScreens(own)
        assertFalse(screens.startFailed(null, own, cancelled = false))
        assertFalse(screens.startFailed("", own, cancelled = false))
        assertFalse(screens.startFailed("tv-1", null, cancelled = false))
    }

    @Test fun aRetryWithGooglesScreenThatFailsTooStopsThere() {
        val screens = CastScreens(own)
        assertTrue(screens.startFailed("tv-1", own, cancelled = false))
        // The retry ran with Google's screen and failed as well: no endless loop.
        assertFalse(screens.startFailed("tv-1", CastReceiver.DEFAULT, cancelled = false))
    }

    @Test fun anUnknownDeviceIdStillGetsAScreen() {
        assertEquals(Why.Own, CastScreens(own).pick(null, ownOffered = true).why)
        assertEquals(Why.NotOffered, CastScreens(own).pick(null, ownOffered = false).why)
    }

    @Test fun theLogSaysWhy() {
        val screens = CastScreens(own)
        assertTrue(screens.describe(screens.pick("tv", true)).contains(own))
        assertTrue(screens.describe(screens.pick("tv", false)).contains("Google"))
    }
}
