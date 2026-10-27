package app.siansa.net

import app.siansa.data.Book
import app.siansa.data.bookLength
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audiobookshelf's items and progress are read into books, chapters and progress (issue #521). */
class AudiobookshelfApiTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    private val item = """
        {"id":"li_1","libraryId":"lib_1","mediaType":"book","addedAt":1700000000000,
         "media":{"duration":43860.5,"metadata":{"title":"The Tide Keeper","subtitle":null,
           "authors":[{"id":"a1","name":"Ada Quill"}],"narrators":["Simon Rhodes"],
           "series":[{"id":"s1","name":"Lantern Coast","sequence":"2"}],"publishedYear":"2023",
           "description":"A lighthouse…","genres":["Fiction","Mystery"]},
           "chapters":[{"id":0,"start":0,"end":1334.2,"title":"The Sea Wall"},{"id":1,"start":1334.2,"end":2460,"title":""}]}}
    """

    @Test fun aBookWithChaptersIsRead() {
        val b = AudiobookshelfApi.parseBook(json(item))!!
        assertEquals("The Tide Keeper", b.title)
        assertEquals("Ada Quill", b.author)
        assertEquals("Simon Rhodes", b.narrator)
        assertEquals("Lantern Coast", b.series)
        assertEquals("2", b.sequence)
        assertEquals(2023, b.year)
        assertEquals(43860, b.durationSec)
        assertEquals(listOf("Fiction", "Mystery"), b.genres)
        assertEquals(2, b.chapters.size)
        assertEquals("Chapter 2", b.chapters[1].title)
        assertEquals("bk-li_1", b.cover)
        assertTrue(!b.podcast)
        // The chapter at a position in the whole book.
        assertEquals("The Sea Wall", b.chapterAt(10.0)?.title)
        assertEquals(1, b.chapterAt(1334.2)?.index)
    }

    @Test fun aMinifiedItemStillReads() {
        // Library listings send authorName and seriesName instead of the lists.
        val b = AudiobookshelfApi.parseBook(json("""{"id":"li_2","mediaType":"book","media":{"duration":100,"metadata":{"title":"Short","authorName":"X","seriesName":"Coast #3"}}}"""))!!
        assertEquals(listOf("X"), b.authors)
        assertEquals("Coast", b.series)
        assertTrue(b.chapters.isEmpty())
        assertNull(b.chapterAt(5.0))
    }

    @Test fun aPodcastIsMarked() {
        val b = AudiobookshelfApi.parseBook(json("""{"id":"li_3","mediaType":"podcast","media":{"metadata":{"title":"Night Shift Radio","author":"NSR"}}}"""))!!
        assertTrue(b.podcast)
        assertEquals("NSR", b.author)
    }

    @Test fun notABookIsSkipped() {
        assertNull(AudiobookshelfApi.parseBook(json("""{"id":"x"}""")))
    }

    @Test fun progressIsRead() {
        val p = AudiobookshelfApi.parseProgress(json("""{"libraryItemId":"li_1","currentTime":18120.5,"duration":43860.5,"progress":0.413,"isFinished":false,"lastUpdate":1729900000000}"""), "li_1")
        assertEquals(18120.5, p.currentSec, 0.01)
        assertTrue(p.started)
        assertEquals(25740.0, p.leftSec, 0.01)
        assertEquals("7 h 9 min", bookLength(p.leftSec))
        assertEquals("48 min", bookLength(2880.0))
        assertEquals("2 h", bookLength(7200.0))
    }

    @Test fun theCoverIdCarriesThePrefix() {
        assertEquals("bk-abc", Book(id = "abc", title = "T").cover)
    }

    @Test fun aSearchReplyListsBooksThenPodcasts() {
        val reply = """{"book":[{"libraryItem":$item,"matchKey":"title","matchText":"Tide"}],
            "podcast":[{"libraryItem":{"id":"li_2","libraryId":"lib_2","mediaType":"podcast","media":{"metadata":{"title":"Tide Talk"}}}}],
            "authors":[{"name":"Ada Quill"}],"series":[],"tags":[],"narrators":[]}"""
        val hits = AudiobookshelfApi.parseSearch(json(reply))
        assertEquals(listOf("The Tide Keeper", "Tide Talk"), hits.map { it.title })
        assertEquals(listOf(false, true), hits.map { it.podcast })
    }

    @Test fun aSearchReplyWithoutHitsIsEmpty() {
        assertEquals(emptyList<Any>(), AudiobookshelfApi.parseSearch(json("""{"authors":[]}""")))
    }
}
