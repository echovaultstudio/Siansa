package app.siansa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The launcher icon (issue #360): the orb and sine wave as a picture for every screen density, a black
 * background, and a one-colour version for themed icons, all inside the part of the icon launchers show.
 */
class LauncherIconTest {
    // Unit tests run from the module folder (app/).
    private val res = File("src/main/res")

    @Test fun foregroundHasAPictureForEveryDensity() {
        val sizes = mapOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432)
        for ((density, px) in sizes) {
            val file = File(res, "mipmap-$density/ic_launcher_foreground.png")
            assertTrue("$file is missing", file.exists())
            val image = ImageIO.read(file)
            assertEquals("$density width", px, image.width)
            assertEquals("$density height", px, image.height)
            // The corners are see-through, so the black background shows round the orb.
            assertEquals("$density corner", 0, image.getRGB(0, 0) ushr 24)
        }
    }

    @Test fun adaptiveIconUsesAllThreeLayers() {
        val xml = File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(xml.contains("@drawable/ic_launcher_background"))
        assertTrue(xml.contains("@mipmap/ic_launcher_foreground"))
        assertTrue(xml.contains("@drawable/ic_launcher_monochrome"))
    }

    @Test fun themedIconStaysInsideTheVisibleCircle() {
        // Launchers show at least the middle 66dp circle of the 108dp canvas; each stroke's half-width counts too.
        val xml = File(res, "drawable/ic_launcher_monochrome.xml").readText()
        val paths = Regex("""strokeWidth="([\d.]+)"[^>]*pathData="([^"]+)"""").findAll(xml).toList()
        assertEquals("the orb's outline and the sine wave", 2, paths.size)
        for (path in paths) {
            val half = path.groupValues[1].toDouble() / 2
            // Absolute points: "M23.00,54.00 L24.20,52.10 …" in the wave, "M37.37,54" at the start of the circle.
            val points = Regex("""[ML]([\d.]+),([\d.]+)""").findAll(path.groupValues[2]).map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            for ((x, y) in points) {
                val distance = Math.hypot(x - 54, y - 54) + half
                assertTrue("($x,$y) reaches $distance from the centre", distance <= 33)
            }
        }
    }

    @Test fun themedOrbIsCentred() {
        val xml = File(res, "drawable/ic_launcher_monochrome.xml").readText()
        val (startX, startY, radius) = Regex("""M([\d.]+),([\d.]+)a([\d.]+),""").find(xml)!!.destructured
        assertEquals(54.0, startX.toDouble() + radius.toDouble(), 0.05)
        assertEquals(54.0, startY.toDouble(), 0.05)
    }
}
