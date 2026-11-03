package app.siansa.net

import app.siansa.data.Lyrics
import app.siansa.data.OutsideMedia
import app.siansa.data.Song

/**
 * Where a station's or a podcast episode's id is routed (issue #353): it plays from its own address and its cover is
 * a web address ([OutsideMedia]). Nothing about it goes to the music server, which has never heard of it: no
 * scrobbles, timeline, likes, lyrics or sound-alikes. Every other call is the music server's, as for book covers.
 */
class OutsideSource(music: MusicSource) : MusicSource by music {
    override fun streamUrl(id: String): String = OutsideMedia.addressOf(id)?.let(OutsideMedia::forPlayer) ?: id
    override fun coverUrl(id: String, size: Int?): String = id.removePrefix(OutsideMedia.COVER)

    override suspend fun song(id: String): Song = throw ApiException("Not on your music server")
    override suspend fun songDetails(id: String): SongDetails = SongDetails()
    override suspend fun lyrics(song: Song): Lyrics? = null
    override suspend fun similarSongs(songId: String, count: Int): List<Song> = emptyList()
    override suspend fun sonicSimilar(songId: String, count: Int): List<Pair<Song, Double>> = emptyList()
    override suspend fun sonicPath(startId: String, endId: String, count: Int): List<Song> = emptyList()
    override suspend fun star(id: String) {}
    override suspend fun unstar(id: String) {}
    override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) {}
    override suspend fun playbackProgress(id: String, positionMs: Long, paused: Boolean) {}
    override suspend fun playbackStopped(id: String, positionMs: Long) {}

    companion object {
        /** Whether a song, cover or other id belongs here rather than to the music server. */
        fun handles(id: String?): Boolean = OutsideMedia.isOutside(id) || OutsideMedia.isWebCover(id)
    }
}
