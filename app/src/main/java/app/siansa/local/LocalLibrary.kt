package app.siansa.local

import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.ArtistRef
import app.siansa.data.GuestMusic
import app.siansa.data.Song
import app.siansa.data.isVariousArtists
import app.siansa.data.normalizeKey
import app.siansa.data.splitArtists
import java.time.Instant

/**
 * Files on the phone as library music (issue #352): songs, their albums and artists, ready to be stored next to the
 * server's with [app.siansa.data.LibraryRepository.putSource].
 *
 * An artist a file names is the library's own artist when it's the same one: by MusicBrainz ID first, else by name
 * when the library has exactly one artist of that name. Then a local song shows on the server artist's page, and
 * the artist isn't listed twice. Only artists the library doesn't have get a local artist of their own.
 *
 * Several artists in one tag ("A feat. B") are split when the file has one MusicBrainz artist ID for each part, as
 * Picard writes them; otherwise the library's joined-artist rules (#359) split them like the server's.
 */
object LocalLibrary {
    /** The picture kept for [t]'s album (by album artist and album name), the same for every file of the album. */
    fun coverKey(t: LocalTrack): String = LocalIds.hash(normalizeKey(t.albumArtist.ifBlank { t.artist }) + "|" + normalizeKey(t.album))

    /** [known]: the library's own artists (the server's), to reuse. */
    fun build(tracks: List<LocalTrack>, known: List<Artist>): GuestMusic {
        val byMbid = known.filter { !it.mbid.isNullOrBlank() }.groupBy { it.mbid!!.lowercase() }
        val byKey = known.groupBy { normalizeKey(it.name) }
        val created = LinkedHashMap<String, Artist>()
        val createdByKey = HashMap<String, String>()

        fun resolve(name: String, mbid: String?): ArtistRef {
            mbid?.let { id -> byMbid[id]?.singleOrNull()?.let { return ArtistRef(it.id, it.name) } }
            val key = normalizeKey(name)
            byKey[key]?.singleOrNull()?.let { a ->
                // The same name with another MusicBrainz ID is another artist.
                if (mbid == null || a.mbid.isNullOrBlank() || a.mbid.equals(mbid, ignoreCase = true)) return ArtistRef(a.id, a.name)
            }
            // One local artist per name: a file without the ID is the same artist as one with it.
            val id = (if (mbid == null) createdByKey[key] else null) ?: LocalIds.artist(mbid ?: key)
            val artist = created.getOrPut(id) { Artist(id, name, mbid = mbid) }
            createdByKey.putIfAbsent(key, id)
            return ArtistRef(artist.id, artist.name)
        }

        val songs = ArrayList<Song>(tracks.size)
        val albumTracks = LinkedHashMap<String, MutableList<Pair<LocalTrack, Song>>>()
        val albumArtists = HashMap<String, ArtistRef>()
        val albumCovers = HashMap<String, String>()
        val pending = ArrayList<Triple<LocalTrack, List<ArtistRef>, String>>()
        for (t in tracks) {
            val refs = artistRefs(t).map { (name, mbid) -> resolve(name, mbid) }.distinctBy { it.id }
            val albumName = t.album.ifBlank { UNKNOWN_ALBUM }
            val albumArtistName = t.albumArtist.ifBlank { refs.firstOrNull()?.name ?: t.artist }.ifBlank { UNKNOWN_ARTIST }
            val albumId = LocalIds.album(albumArtistName, albumName)
            albumArtists.getOrPut(albumId) {
                val mbid = t.albumArtistMbids.singleOrNull() ?: if (t.albumArtist.isBlank()) t.artistIds.firstOrNull() else null
                if (t.albumArtist.isBlank() && refs.isNotEmpty()) refs.first() else resolve(albumArtistName, mbid)
            }
            if (t.hasCover && albumId !in albumCovers) albumCovers[albumId] = LocalIds.cover(coverKey(t))
            pending += Triple(t, refs, albumId)
        }
        for ((t, refs, albumId) in pending) {
            val albumName = t.album.ifBlank { UNKNOWN_ALBUM }
            val song = Song(
                id = t.id,
                title = t.title,
                artist = t.artist.ifBlank { refs.joinToString(", ") { it.name } },
                artistId = refs.firstOrNull()?.id,
                album = albumName,
                albumId = albumId,
                coverArt = albumCovers[albumId],
                durationSec = t.durationSec,
                track = t.track,
                disc = t.disc,
                year = t.year,
                genre = t.genre,
                trackGain = t.trackGain,
                albumGain = t.albumGain,
                trackPeak = t.trackPeak,
                albumPeak = t.albumPeak,
                artists = refs,
                suffix = t.suffix,
                mbid = t.recording,
            )
            songs += song
            albumTracks.getOrPut(albumId) { ArrayList() } += t to song
        }

        val albums = albumTracks.map { (id, list) ->
            val first = list.first().second
            val artist = albumArtists.getValue(id)
            val name = list.first().first.albumArtist.ifBlank { artist.name }
            Album(
                id = id,
                name = first.album,
                artist = name,
                artistId = artist.id,
                coverArt = first.coverArt,
                songCount = list.size,
                durationSec = list.sumOf { it.second.durationSec },
                year = list.mapNotNull { it.second.year }.maxOrNull(),
                genre = list.mapNotNull { it.second.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                // When the newest file arrived, so the album sorts among Recently added.
                created = list.maxOf { it.first.modified }.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
                isCompilation = list.any { it.first.compilation } || isVariousArtists(name),
            )
        }
        val albumCounts = albums.groupingBy { it.artistId }.eachCount()
        // An artist only on the phone shows the cover of one of their albums (no photo of their own).
        val artists = created.values.map { a ->
            val cover = albums.firstOrNull { it.artistId == a.id && it.coverArt != null }?.coverArt
                ?: songs.firstOrNull { s -> s.coverArt != null && s.artists.any { it.id == a.id } }?.coverArt
            a.copy(albumCount = albumCounts[a.id] ?: 0, coverArt = cover)
        }
        return GuestMusic(artists = artists, albums = albums, songs = songs)
    }

    /**
     * The artists of [t] with their MusicBrainz IDs, in order: one name per ID when they line up (several ARTISTS
     * values, or one joined name that splits into as many parts as there are IDs); else the names without IDs, except
     * a single name with a single ID.
     */
    internal fun artistRefs(t: LocalTrack): List<Pair<String, String?>> {
        val ids = t.artistIds
        val names = t.artists.ifEmpty { listOf(t.artist) }.filter { it.isNotBlank() }
        if (names.isEmpty()) return listOf(UNKNOWN_ARTIST to null)
        if (names.size == ids.size) return names.zip(ids)
        if (names.size == 1 && ids.size > 1) {
            val parts = splitArtists(names.first())
            if (parts.size == ids.size) return parts.zip(ids)
        }
        return names.map { it to null }
    }

    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"
}

/** A server song, as much as matching needs (issue #352). */
data class ServerSong(val id: String, val title: String, val artist: String, val durationSec: Int, val mbid: String?)

/**
 * Which server song each local song is (issue #352), so a file on the phone can use the server copy's AudioMuse
 * analysis (mixes, radios, loudness): the same MusicBrainz recording ID, else the same title and artist with
 * lengths at most [SLACK_SEC] apart. Songs with no match are only on the phone.
 */
object LocalMatch {
    const val SLACK_SEC = 3

    fun match(local: List<Song>, server: List<ServerSong>): Map<String, String> {
        val byMbid = server.filter { !it.mbid.isNullOrBlank() }.groupBy { it.mbid!!.lowercase() }
        val byKey = server.groupBy { key(it.title, it.artist) }
        val out = HashMap<String, String>()
        for (song in local) {
            val mbid = song.mbid?.lowercase()
            val viaId = mbid?.let { byMbid[it] }?.let { closest(it, song.durationSec) }
            val found = viaId ?: byKey[key(song.title, song.artist)]?.let { candidates ->
                candidates.filter { close(it.durationSec, song.durationSec) }.let { closest(it, song.durationSec) }
            }
            if (found != null) out[song.id] = found.id
        }
        return out
    }

    private fun key(title: String, artist: String) = normalizeKey(title) + "|" + normalizeKey(artist)

    private fun close(a: Int, b: Int) = a > 0 && b > 0 && kotlin.math.abs(a - b) <= SLACK_SEC

    private fun closest(list: List<ServerSong>, seconds: Int): ServerSong? =
        list.minByOrNull { if (it.durationSec > 0 && seconds > 0) kotlin.math.abs(it.durationSec - seconds) else Int.MAX_VALUE / 2 }
}
