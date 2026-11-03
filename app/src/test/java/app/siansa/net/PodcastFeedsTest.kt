package app.siansa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A podcast's RSS feed read into its episodes (issue #353). */
class PodcastFeedsTest {
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Rocket Hour</title>
            <link>https://rocket.example.com</link>
            <description><![CDATA[<p>Talk about <b>space</b> &amp; music.</p>]]></description>
            <itunes:author>Ada Lovelace</itunes:author>
            <itunes:image href="https://rocket.example.com/cover.jpg"/>
            <image><url>https://rocket.example.com/small.jpg</url><title>Rocket Hour</title></image>
            <item>
              <title>Ep. 479 · Exploring the Dopamine Chamber</title>
              <guid isPermaLink="false">rocket-479</guid>
              <pubDate>Tue, 05 Mar 2024 10:00:00 GMT</pubDate>
              <itunes:duration>58:30</itunes:duration>
              <enclosure url="https://cdn.example.com/479.mp3" length="123" type="audio/mpeg"/>
              <itunes:image href="https://rocket.example.com/479.jpg"/>
            </item>
            <item>
              <title>A blog post without audio</title>
              <guid>post-1</guid>
            </item>
            <item>
              <title>Ep. 478</title>
              <pubDate>Mon, 4 Mar 2024 09:30:00 +0100</pubDate>
              <itunes:duration>3723</itunes:duration>
              <enclosure url="/media/478.m4a" type="audio/x-m4a"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test fun theFeedGivesItsTitleAuthorPictureAndEpisodes() {
        val parsed = PodcastFeeds.parse(feed, "https://rocket.example.com/feed.xml")!!
        assertEquals("Rocket Hour", parsed.title)
        assertEquals("Ada Lovelace", parsed.author)
        assertEquals("https://rocket.example.com/cover.jpg", parsed.image)
        assertEquals("Talk about space & music.", parsed.description)
        // The blog post has no audio, so it isn't an episode.
        assertEquals(listOf("Ep. 479 · Exploring the Dopamine Chamber", "Ep. 478"), parsed.episodes.map { it.title })
    }

    @Test fun episodesHaveTheirFileLengthDateAndPicture() {
        val (first, second) = PodcastFeeds.parse(feed, "https://rocket.example.com/feed.xml")!!.episodes
        assertEquals("https://cdn.example.com/479.mp3", first.url)
        assertEquals(58 * 60 + 30, first.durationSec)
        assertEquals(1709632800000L, first.publishedAt)
        assertEquals("https://rocket.example.com/479.jpg", first.image)
        // A relative enclosure is made whole against the feed's address; no guid falls back to the file.
        assertEquals("https://rocket.example.com/media/478.m4a", second.url)
        assertEquals(3723, second.durationSec)
        assertEquals(1709541000000L, second.publishedAt)
        assertNull(second.image)
        assertEquals(PodcastFeeds.idOf("https://rocket.example.com/media/478.m4a"), second.id)
        assertNotEquals(first.id, second.id)
    }

    @Test fun somethingThatIsNotAFeedGivesNothing() {
        assertNull(PodcastFeeds.parse("<!doctype html><html><body>Hi</body></html>", "https://example.com/"))
        assertNull(PodcastFeeds.parse("not xml at all", "https://example.com/"))
        assertNull(PodcastFeeds.parse("<rss><channel></channel></rss>", "https://example.com/"))
    }

    @Test fun lengthsInEveryShapeFeedsUse() {
        assertEquals(3723, PodcastFeeds.duration("1:02:03"))
        assertEquals(3723, PodcastFeeds.duration("62:03"))
        assertEquals(3723, PodcastFeeds.duration("3723"))
        assertEquals(3723, PodcastFeeds.duration("3723.6"))
        assertEquals(0, PodcastFeeds.duration(""))
        assertEquals(0, PodcastFeeds.duration("about an hour"))
        assertEquals(0, PodcastFeeds.duration(null))
    }

    @Test fun datesInRfc822AndItsCommonVariants() {
        assertEquals(1709632800000L, PodcastFeeds.date("Tue, 05 Mar 2024 10:00:00 GMT"))
        assertEquals(1709632800000L, PodcastFeeds.date("Tue, 5 Mar 2024 10:00:00 +0000"))
        // A zone name RFC 1123 doesn't take is read as UTC rather than not at all.
        assertEquals(1709632800000L, PodcastFeeds.date("Tue, 05 Mar 2024 10:00:00 EST"))
        assertEquals(0L, PodcastFeeds.date("yesterday"))
    }
}
