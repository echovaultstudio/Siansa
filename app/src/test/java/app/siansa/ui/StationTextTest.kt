package app.siansa.ui

import app.siansa.data.Episode
import app.siansa.data.PodcastFeed
import app.siansa.data.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** The lines under stations, podcasts and episodes in Your Stations (issue #353). */
class StationTextTest {
    @Test fun anEpisodeShowsItsDateAndLength() {
        val line = episodeLine(1709632800000L, 3510, ZoneOffset.UTC)
        assertTrue(line, line.endsWith(" · 58 min"))
        assertTrue(line, line.contains("2024"))
        assertEquals("1 h 2 min", episodeLine(0, 3723, ZoneOffset.UTC))
        // Under a minute, or unknown: no length.
        assertEquals("", episodeLine(0, 30, ZoneOffset.UTC))
    }

    @Test fun stationsAndPodcastsSayWhereTheyAreFrom() {
        assertEquals("Radio · From your server", stationLine(Station("server-1", "A", "https://a.example.com/s", fromServer = true)))
        assertEquals("Radio · On this phone", stationLine(Station("own-1", "A", "https://a.example.com/s")))
        val one = PodcastFeed("p", "https://f.example.com", "Rocket Hour", author = "Ada", episodes = listOf(Episode("e", "Ep", "https://c.example.com/1.mp3")))
        assertEquals("Podcast · Ada · 1 episode", podcastLine(one))
        assertEquals("Podcast · 0 episodes", podcastLine(one.copy(author = "", episodes = emptyList())))
    }
}
