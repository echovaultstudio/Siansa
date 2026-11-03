package app.siansa.data

import app.siansa.net.StationPictures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Editing a station keeps it where it is; its picture is looked for once and kept with it (issue #353). */
class StationEditTest {
    private val a = Station("own-a", "First", "https://a.example.com/s", addedAt = 3)
    private val b = Station("own-b", "Second", "https://b.example.com/s", addedAt = 2, image = "https://b.example.com/found.png", homePage = "https://b.example.com/", pictureCheckedAt = 7)
    private val c = Station("own-c", "Third", "https://c.example.com/s", addedAt = 1)

    private lateinit var dir: File
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    /** Every lookup finds this picture, unless [reachable] is off. */
    private class Sources(var reachable: Boolean = true) : StationPictures.Sources {
        var lookups = 0
        override suspend fun radioBrowserByUrl(url: String) = if (reachable) emptyList<StationPictures.RadioBrowserStation>() else null
        override suspend fun radioBrowserByName(name: String) =
            if (reachable) listOf(StationPictures.RadioBrowserStation(name, "", "", "https://rb.example.com/$name.png", "")).also { lookups++ } else null
        override suspend fun websitePictures(page: String) = if (reachable) emptyList<String>() else null
        override suspend fun isPicture(url: String) = true
    }

    @Before fun setUp() {
        dir = Files.createTempDirectory("stations").toFile()
        File(dir, "stations.json").writeText(AppJson.encodeToString(OwnStations.serializer(), OwnStations(stations = listOf(a, b, c))))
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun repository(sources: StationPictures.Sources) =
        StationRepository(dir, OkHttpClient(), { AppSettings() }, sources, scope) { emptyList() }

    @Test fun anEditedStationKeepsItsIdWhenItWasAddedAndItsPlace() {
        val edited = StationRepository.edited(b, "  Renamed ", b.link, b.streamUrl, null, null, now = 99)
        assertEquals("own-b", edited.id)
        assertEquals(2L, edited.addedAt)
        assertEquals("Renamed", edited.name)
        val list = StationRepository.replaced(listOf(a, b, c), edited)
        assertEquals(listOf("own-a", "own-b", "own-c"), list.map { it.id })
        assertEquals("Renamed", list[1].name)
    }

    @Test fun aBlankPictureLinkLooksAgainShowingTheOldPictureMeanwhile() {
        val edited = StationRepository.edited(b, "Second", b.link, b.streamUrl, null, null, now = 99)
        assertEquals(0L, edited.pictureCheckedAt)
        assertEquals("https://b.example.com/found.png", edited.image)
        assertEquals("https://b.example.com/", edited.homePage)
    }

    @Test fun aPictureLinkIsThePicture() {
        val edited = StationRepository.edited(b, "Second", b.link, b.streamUrl, null, "https://p.example.com/mine.png", now = 99)
        assertEquals("https://p.example.com/mine.png", edited.image)
        assertEquals("https://p.example.com/mine.png", edited.pictureLink)
        assertEquals(99L, edited.pictureCheckedAt)
        // Cleared again: the entered picture goes, and one is looked for.
        val cleared = StationRepository.edited(edited, "Second", b.link, b.streamUrl, null, null, now = 100)
        assertNull(cleared.image)
        assertNull(cleared.pictureLink)
        assertEquals(0L, cleared.pictureCheckedAt)
    }

    @Test fun aNewLinkDropsWhatBelongedToTheOldOne() {
        val edited = StationRepository.edited(b, "", "https://new.example.com/x.pls", "https://new.example.com/live", "New FM", null, now = 99)
        assertEquals("https://new.example.com/x.pls", edited.link)
        assertEquals("https://new.example.com/live", edited.streamUrl)
        // A blank name takes the playlist's title.
        assertEquals("New FM", edited.name)
        assertNull(edited.image)
        assertNull(edited.homePage)
        assertEquals("new.example.com", StationRepository.edited(b, "", "https://new.example.com/s", "https://new.example.com/s", null, null, 1).name)
    }

    @Test fun editingSavesInPlaceAndFindsThePictureOnce() = runBlocking {
        val sources = Sources()
        val repo = repository(sources)
        // a and c have never been looked for; editing b looks for it again too.
        repo.editStation("own-b", b.link, "Renamed", "").getOrThrow()
        repo.findPictures()
        val stations = repo.mine().stations
        assertEquals(listOf("own-a", "own-b", "own-c"), stations.map { it.id })
        assertEquals("Renamed", stations[1].name)
        assertEquals("https://rb.example.com/Renamed.png", stations[1].image)
        assertEquals("https://rb.example.com/First.png", stations[0].image)
        assertTrue(stations.all { it.pictureCheckedAt > 0 })
        // Kept with the station: not looked for again.
        val before = sources.lookups
        repo.findPictures()
        assertEquals(before, sources.lookups)
        // And it's in the file, for next time.
        val again = repository(sources).mine().stations
        assertEquals("https://rb.example.com/Renamed.png", again[1].image)
    }

    @Test fun aLookupThatReachedNothingIsTriedAgain() = runBlocking {
        val sources = Sources(reachable = false)
        val repo = repository(sources)
        repo.findPictures()
        assertEquals(0L, repo.mine().stations[0].pictureCheckedAt)
        sources.reachable = true
        repo.findPictures()
        assertEquals("https://rb.example.com/First.png", repo.mine().stations[0].image)
    }

    @Test fun aPictureLinkThatIsntALinkIsRefused() = runBlocking {
        val repo = repository(Sources())
        val result = repo.editStation("own-a", a.link, "First", "not a link")
        assertTrue(result.isFailure)
        assertEquals("The picture link isn't a web link", result.exceptionOrNull()?.message)
        assertEquals("First", repo.mine().stations[0].name)
    }

    @Test fun serverStationsGetThePicturesKeptForThem() {
        val server = Station("server-1", "Radio Paradise", "https://rp.example.com/s", homePage = null, fromServer = true)
        val pictures = mapOf("https://rp.example.com/s" to StationPicture(image = "https://rp.example.com/i.png", homePage = "https://rp.example.com/", checkedAt = 5))
        val shown = StationRepository.withPictures(listOf(server, server.copy(id = "server-2", streamUrl = "https://x.example.com/s")), pictures)
        assertEquals("https://rp.example.com/i.png", shown[0].image)
        assertEquals("https://rp.example.com/", shown[0].homePage)
        assertEquals(5L, shown[0].pictureCheckedAt)
        assertNull(shown[1].image)
    }
}
