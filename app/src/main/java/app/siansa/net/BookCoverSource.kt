package app.siansa.net

import app.siansa.data.Book

/**
 * The source a book's cover id ("bk-<item>") is routed to (issue #521): its cover comes from Audiobookshelf,
 * and every other call is the music server's, which never sees a book id. Lets book covers go through the
 * same cover cache, memory keys and downloads as album and artist covers, with no change to the callers.
 */
class BookCoverSource(private val books: AudiobookshelfApi, music: MusicSource) : MusicSource by music {
    override fun coverUrl(id: String, size: Int?): String = books.coverUrl(id.removePrefix(Book.COVER_PREFIX), size)
}
