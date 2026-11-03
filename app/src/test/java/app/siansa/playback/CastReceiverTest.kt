package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Which TV app plays while casting (issue #114): Siansa's own once it has an app id, else Google's. */
class CastReceiverTest {
    @Test fun noIdMeansGooglesStandardScreen() {
        assertEquals(CastReceiver.DEFAULT, CastReceiver.pick(""))
        assertEquals(CastReceiver.DEFAULT, CastReceiver.pick("   "))
        assertEquals(CastReceiver.DEFAULT, CastReceiver.pick(null))
        assertFalse(CastReceiver.isOwn(CastReceiver.pick("")))
    }

    @Test fun anAppIdFromTheCastConsoleIsUsed() {
        assertEquals("A1B2C3D4", CastReceiver.pick("A1B2C3D4"))
        assertTrue(CastReceiver.isOwn(CastReceiver.pick("A1B2C3D4")))
    }

    @Test fun caseAndSpacesDontMatter() {
        assertEquals("A1B2C3D4", CastReceiver.pick(" a1b2c3d4\n"))
    }

    @Test fun somethingThatIsntAnAppIdFallsBack() {
        // Too short, too long, not hex, or a placeholder left in: casting must still work.
        listOf("A1B2C3", "A1B2C3D4E5", "XYZ12345", "your-id", "\"A1B2C3D4\"").forEach {
            assertEquals(it, CastReceiver.DEFAULT, CastReceiver.pick(it))
        }
    }

    @Test fun theDefaultIsGooglesDefaultMediaReceiver() {
        assertEquals("CC1AD845", CastReceiver.DEFAULT)
    }

    @Test fun theBuildReadsTheIdFromOutside() {
        val root = listOf(File(".."), File(".")).first { File(it, "LICENSE").isFile }
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("CAST_RECEIVER_ID"))
        assertTrue(gradle.contains("siansa.castReceiverId"))
        assertTrue(File(root, "gradle.properties").readText().contains("siansa.castReceiverId="))
        assertTrue(File(root, "cast-receiver/index.html").isFile)
    }
}
