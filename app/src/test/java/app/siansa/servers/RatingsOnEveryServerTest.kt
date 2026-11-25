package app.siansa.servers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.siansa.data.Ratings
import app.siansa.net.MusicSource
import app.siansa.net.SongDetails
import app.siansa.net.SourceKind
import app.siansa.net.parseSongDetails
import app.siansa.ui.ratingShown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Star ratings on every server (issue #1043): Navidrome and other Subsonic servers keep them; Emby and Jellyfin have
 * no five-star rating, so nothing about stars shows there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RatingsOnEveryServerTest {
    /** A server that keeps ratings, noting what it's sent; [fails] makes it refuse. */
    private class Keeping(base: MusicSource, var fails: Boolean = false) : MusicSource by base {
        val sent = mutableListOf<Pair<String, Int>>()
        var onServer: Int? = null
        override val keepsRatings: Boolean get() = true
        override suspend fun setRating(id: String, stars: Int) {
            if (fails) throw java.io.IOException("No route to host")
            sent += id to stars
            onServer = stars.takeIf { it > 0 }
        }
        override suspend fun songDetails(id: String): SongDetails = SongDetails(userRating = onServer)
    }

    private fun ratings(source: MusicSource) = Ratings(ApplicationProvider.getApplicationContext(), source, CoroutineScope(Dispatchers.Unconfined))

    @Test fun onlySubsonicServersKeepRatings() {
        EveryServer.all.forEach { server ->
            val keeps = server.kind == SourceKind.OPENSUBSONIC
            assertEquals(server.name, keeps, server.source().keepsRatings)
            assertEquals(server.name, keeps, ratings(server.source()).kept)
            // Nothing about stars shows where they aren't kept, or with ratings off, or for a radio station.
            assertEquals(server.name, keeps, ratingShown(on = true, kept = server.source().keepsRatings, songId = server.songId))
            assertFalse(server.name, ratingShown(on = false, kept = server.source().keepsRatings, songId = server.songId))
            assertFalse(server.name, ratingShown(on = true, kept = true, songId = null))
        }
    }

    @Test fun aRatingGoesToTheServerAndShowsAtOnce() {
        EveryServer.all.forEach { server ->
            val keeping = Keeping(server.source())
            val ratings = ratings(keeping)
            ratings.rate(server.songId, 0)
            ratings.rate(server.songId, 4)
            assertEquals(server.name, 4, ratings.of(server.songId))
            assertEquals(server.name, 4, ratings.stars.value[server.songId])
            ratings.rate(server.songId, 4)
            assertEquals("${server.name}: the same rating isn't sent twice", listOf(server.songId to 4), keeping.sent)
            // Taken off again.
            ratings.rate(server.songId, 0)
            assertEquals(server.name, 0, ratings.of(server.songId))
            assertFalse(server.name, server.songId in ratings.stars.value)
            assertEquals(server.name, listOf(server.songId to 4, server.songId to 0), keeping.sent)
        }
    }

    @Test fun aRatingTheServerRefusesIsUndoneAndSaid() {
        val server = EveryServer.all.first()
        val keeping = Keeping(server.source())
        val ratings = ratings(keeping)
        ratings.rate(server.songId, 3)
        keeping.fails = true
        var said: String? = null
        ratings.rate(server.songId, 5) { said = it }
        assertEquals(3, ratings.of(server.songId))
        assertTrue(said, said!!.startsWith("Couldn't save the rating on your server"))
    }

    @Test fun theRatingIsKeptOnThePhoneAndFollowsTheServer() = runBlocking {
        val server = EveryServer.all.first()
        val keeping = Keeping(server.source())
        ratings(keeping).rate(server.songId, 2)
        // The app opened again: the phone's copy shows before the server answers.
        val again = ratings(keeping)
        assertEquals(2, again.of(server.songId))
        // Rated in another app meanwhile.
        keeping.onServer = 5
        again.refresh(server.songId)
        assertEquals(5, again.of(server.songId))
        keeping.onServer = null
        again.refresh(server.songId)
        assertEquals(0, again.of(server.songId))
        // The server out of reach: the phone's copy stays.
        again.learn(server.songId, 4)
        again.refresh("unreachable").also { }
        assertEquals(4, again.of(server.songId))
    }

    /** An album's rating (issue #1334) goes the same way, by the album's id; none where the server keeps none. */
    @Test fun anAlbumsRatingGoesToTheServerByItsId() {
        EveryServer.all.forEach { server ->
            val keeps = server.source().keepsRatings
            assertEquals(server.name, keeps, ratingShown(on = true, kept = keeps, songId = "al-1"))
            if (!keeps) return@forEach
            val keeping = Keeping(server.source())
            val r = ratings(keeping)
            r.rate("al-1", 5)
            assertEquals(server.name, listOf("al-1" to 5), keeping.sent)
            assertEquals(5, r.of("al-1"))
            r.learn("al-1", null)  // the album as the server has it, not rated
            assertEquals(0, r.of("al-1"))
        }
    }

    @Test fun theServersRatingIsReadFromASongsDetails() {
        fun details(json: String) = parseSongDetails(Json.parseToJsonElement(json).jsonObject)
        assertEquals(4, details("""{"id":"1","suffix":"flac","userRating":4}""").userRating)
        assertNull(details("""{"id":"1","suffix":"flac"}""").userRating)
        assertNull("0 is not rated", details("""{"id":"1","suffix":"flac","userRating":0}""").userRating)
    }
}
