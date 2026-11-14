package app.siansa.car

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import app.siansa.data.Album
import app.siansa.data.Artist
import app.siansa.data.Mix
import app.siansa.data.Playlist
import app.siansa.data.PlaylistDetail
import app.siansa.data.RecentPlay
import app.siansa.data.SearchResults
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.data.normalizeKey
import app.siansa.data.picture
import app.siansa.mix.MoodRadio
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where an item of the car's browse tree (issue #62) sits. Its media id is "root", "home", "album:<id>",
 * "song:<list>:<song id>" and so on, with the variable parts encoded, so ids that hold a colon stay whole.
 */
sealed class CarId {
    object Root : CarId()
    object Home : CarId()
    object Recent : CarId()
    object Playlists : CarId()
    object Albums : CarId()
    object Artists : CarId()
    data class Album(val id: String) : CarId()
    data class Playlist(val id: String) : CarId()
    data class Artist(val id: String) : CarId()
    data class Mix(val id: String) : CarId()
    /** A mood radio: played, not opened, as its songs are found afresh each time. */
    data class Mood(val id: String) : CarId()
    /** The songs found for a search, so picking one plays the rest of them after it. */
    data class Search(val query: String) : CarId()
    /** One letter's part of a long list ([list] is Albums or Artists), named by its [key] in [CarIndex]. */
    data class Letter(val list: CarId, val key: String) : CarId()
    /** A song in a list: [list] is the list it was picked from, which then plays from it. */
    data class Song(val list: CarId, val songId: String) : CarId()

    /** This place as a media id. */
    val mediaId: String
        get() = when (this) {
            Root -> ROOT
            Home -> HOME
            Recent -> RECENT
            Playlists -> PLAYLISTS
            Albums -> ALBUMS
            Artists -> ARTISTS
            is Album -> "album:" + enc(id)
            is Playlist -> "playlist:" + enc(id)
            is Artist -> "artist:" + enc(id)
            is Mix -> "mix:" + enc(id)
            is Mood -> "mood:" + enc(id)
            is Search -> "search:" + enc(query)
            is Letter -> "letter:" + enc(list.mediaId) + ":" + enc(key)
            is Song -> "song:" + enc(list.mediaId) + ":" + enc(songId)
        }

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"

        /** The place a media id names, or null when it isn't one of the car's (a plain song id from the app). */
        fun parse(mediaId: String?): CarId? {
            mediaId ?: return null
            when (mediaId) {
                ROOT -> return Root
                HOME -> return Home
                RECENT -> return Recent
                PLAYLISTS -> return Playlists
                ALBUMS -> return Albums
                ARTISTS -> return Artists
            }
            val kind = mediaId.substringBefore(':', "")
            val rest = mediaId.substringAfter(':', "")
            if (kind.isEmpty() || rest.isEmpty()) return null
            return runCatching {
                when (kind) {
                    "album" -> Album(dec(rest))
                    "playlist" -> Playlist(dec(rest))
                    "artist" -> Artist(dec(rest))
                    "mix" -> Mix(dec(rest))
                    "mood" -> Mood(dec(rest))
                    "search" -> Search(dec(rest))
                    "letter" -> {
                        val parts = rest.split(':')
                        if (parts.size != 2) return null
                        val list = parse(dec(parts[0])) ?: return null
                        Letter(list, dec(parts[1]))
                    }
                    "song" -> {
                        val parts = rest.split(':')
                        if (parts.size != 2) return null
                        val list = parse(dec(parts[0])) ?: return null
                        Song(list, dec(parts[1]))
                    }
                    else -> null
                }
            }.getOrNull()
        }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
    }
}

/** What the car's browse tree is made from: the phone's library, mixes and the server's search. */
interface CarLibrary {
    /** The newest mix of each vibe, newest first. */
    suspend fun mixes(): List<Mix>
    suspend fun mix(id: String): Mix?
    /** The mood radios, when AudioMuse is set up to make them; otherwise none. */
    fun moods(): List<MoodRadio>
    suspend fun moodSongs(id: String): List<Song>
    /** What was last played as a whole on the phone (Home's shortcuts), newest first. */
    suspend fun recent(): List<RecentPlay>
    suspend fun playlists(): List<Playlist>
    suspend fun playlist(id: String): PlaylistDetail?
    suspend fun albums(): List<Album>
    suspend fun album(id: String): Album?
    suspend fun albumSongs(id: String): List<Song>
    suspend fun artists(): List<Artist>
    suspend fun artist(id: String): Artist?
    suspend fun artistSongs(id: String): List<Song>
    suspend fun search(query: String): SearchResults
    /** Songs to play when asked to play music without saying what. */
    suspend fun anything(): List<Song>
}

/**
 * Songs to play from the car: the list, where in it to start, and the page it came from as Home's shortcuts note it
 * ([route], [title], [cover]), so the phone's Home shows it under what was played last.
 */
data class CarQueue(
    val songs: List<Song>,
    val start: Int = 0,
    val route: String? = null,
    val title: String? = null,
    val cover: String? = null,
)

/** One folder of a long list split by letter: [key] is its name in the car, [range] says from which name to which when a letter is in several parts. */
data class CarGroup<T>(val key: String, val range: String?, val entries: List<T>)

/**
 * A long list of albums or artists as a folder for each letter (issues #1040, #1041). A list reaches the car whole
 * only up to 256 KB (Media3 cuts what's over, without a word), a few hundred albums or artists, and the car's own
 * "Jump to letter" only knows the letters of what reached it: with more, the later letters were grey and their
 * albums and artists couldn't be reached. So a list longer than [WHOLE] is handed over as letters, each opening to
 * its part, and a letter with more than [WHOLE] itself in parts of that size.
 */
object CarIndex {
    /** The longest list handed to the car as it is: well inside what reaches it (about 500 short entries). */
    const val WHOLE = 200

    /** What comes before the letters: names that start with a digit or a sign, as the car's own index calls them. */
    const val DIGITS = "123"

    /** The letter [name] is listed under, as the library orders it (no accents, no leading signs). */
    fun letterOf(name: String): String {
        val first = normalizeKey(name).firstOrNull() ?: return DIGITS
        return if (first.isLetter()) first.uppercaseChar().toString() else DIGITS
    }

    /** [entries] (in the library's order) by letter, or null when the list is short enough to go as it is. */
    fun <T> groups(entries: List<T>, limit: Int = WHOLE, name: (T) -> String): List<CarGroup<T>>? {
        if (entries.size <= limit) return null
        val byLetter = LinkedHashMap<String, MutableList<T>>()
        for (entry in entries) byLetter.getOrPut(letterOf(name(entry))) { ArrayList() }.add(entry)
        return byLetter.flatMap { (letter, list) ->
            if (list.size <= limit) return@flatMap listOf(CarGroup(letter, null, list))
            val parts = list.chunked(limit)
            parts.mapIndexed { i, part ->
                CarGroup("$letter (${i + 1} of ${parts.size})", "${name(part.first())} – ${name(part.last())}", part)
            }
        }
    }
}

/**
 * The browse tree Android Auto shows (issue #62): four tabs (Home, Playlists, Albums, Artists), each opening to songs
 * to play, and search. Kept apart from the service so it can be tested on its own: [library] gives the data and
 * [coverUri] the address the car loads a cover from.
 */
@OptIn(UnstableApi::class)
class CarTree(private val library: CarLibrary, private val coverUri: (String) -> Uri?) {

    /** The root, which the car asks for first; it says the tabs are lists and albums show as a grid. */
    fun root(): MediaItem = folder(CarId.Root, "Siansa")

    /** The item a media id names, as the car asks for it before opening it; null when there's none. */
    suspend fun item(mediaId: String): MediaItem? = when (val id = CarId.parse(mediaId)) {
        null -> null
        CarId.Root -> root()
        CarId.Home -> folder(id, "Home")
        CarId.Recent -> folder(id, "Recently played")
        CarId.Playlists -> folder(id, "Playlists")
        CarId.Albums -> folder(id, "Albums", grid = albumGroups() == null)
        CarId.Artists -> folder(id, "Artists")
        is CarId.Letter -> folder(id, id.key, grid = id.list == CarId.Albums)
        is CarId.Album -> library.album(id.id)?.let(::albumItem) ?: folder(id, "Album")
        is CarId.Artist -> library.artist(id.id)?.let(::artistItem) ?: folder(id, "Artist")
        is CarId.Playlist -> library.playlist(id.id)?.playlist?.let(::playlistItem) ?: folder(id, "Playlist")
        is CarId.Mix -> library.mix(id.id)?.let(::mixItem) ?: folder(id, "Mix")
        is CarId.Search -> folder(id, id.query)
        is CarId.Mood -> library.moods().firstOrNull { it.id == id.id }?.let(::moodItem)
        is CarId.Song -> songsOf(id.list).firstOrNull { it.id == id.songId }?.let { songItem(it, id.list, null) }
    }

    /** What's inside [parentId], or null when it isn't a list the car can open. */
    suspend fun children(parentId: String): List<MediaItem>? = when (val id = CarId.parse(parentId)) {
        CarId.Root -> listOf(
            folder(CarId.Home, "Home"),
            folder(CarId.Playlists, "Playlists"),
            // Albums show as a grid of covers; their letters, when there are too many for one list, as a list.
            folder(CarId.Albums, "Albums", grid = albumGroups() == null),
            folder(CarId.Artists, "Artists"),
        )
        CarId.Home -> home()
        CarId.Recent -> library.recent().mapNotNull(::recentItem)
        CarId.Playlists -> library.playlists().map(::playlistItem)
        CarId.Albums -> albumGroups()?.map { letterItem(CarId.Albums, it, "albums") } ?: library.albums().map(::albumItem)
        CarId.Artists -> artistGroups()?.map { letterItem(CarId.Artists, it, "artists") } ?: library.artists().map(::artistItem)
        is CarId.Letter -> when (id.list) {
            CarId.Albums -> albumGroups()?.firstOrNull { it.key == id.key }?.entries.orEmpty().map(::albumItem)
            CarId.Artists -> artistGroups()?.firstOrNull { it.key == id.key }?.entries.orEmpty().map(::artistItem)
            else -> null
        }
        is CarId.Artist -> artistSongs(id.id).map { songItem(it, id, it.album.ifBlank { null }) }
        is CarId.Album, is CarId.Playlist, is CarId.Mix, is CarId.Search -> songsOf(id).map { songItem(it, id, null) }
        else -> null
    }

    /** Search results: songs (which play the songs found from the one picked), then albums and artists to open. */
    suspend fun search(query: String): List<MediaItem> {
        val found = library.search(query)
        val list = CarId.Search(query)
        return found.songs.map { songItem(it, list, "Songs") } +
            found.albums.map { albumItem(it).grouped("Albums") } +
            found.artists.map { artistItem(it).grouped("Artists") }
    }

    /** What picking [mediaId] plays, or null when it's nothing the car can play. */
    suspend fun queue(mediaId: String): CarQueue? = when (val id = CarId.parse(mediaId)) {
        is CarId.Song -> {
            val songs = songsOf(id.list)
            val start = songs.indexOfFirst { it.id == id.songId }
            if (start < 0) null else whole(id.list, songs).copy(start = start)
        }
        is CarId.Mood, is CarId.Album, is CarId.Playlist, is CarId.Mix, is CarId.Artist, is CarId.Search ->
            songsOf(id).takeIf { it.isNotEmpty() }?.let { whole(id, it) }
        else -> null
    }

    /**
     * What "Play [query] on Siansa" plays: an artist or album named exactly so, else the songs found, else the first
     * album or artist found. Without words, the newest mix, or songs from the whole library.
     */
    suspend fun voice(query: String): CarQueue? {
        val q = query.trim()
        if (q.isEmpty()) {
            library.mixes().firstOrNull { it.songs.isNotEmpty() }?.let { return whole(CarId.Mix(it.id), it.songs) }
            return library.anything().takeIf { it.isNotEmpty() }?.let { CarQueue(it) }
        }
        val found = library.search(q)
        found.artists.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { a -> queue(CarId.Artist(a.id).mediaId)?.let { return it } }
        found.albums.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { a -> queue(CarId.Album(a.id).mediaId)?.let { return it } }
        if (found.songs.isNotEmpty()) return CarQueue(found.songs)
        found.albums.firstOrNull()?.let { a -> queue(CarId.Album(a.id).mediaId)?.let { return it } }
        found.artists.firstOrNull()?.let { a -> queue(CarId.Artist(a.id).mediaId)?.let { return it } }
        return null
    }

    /** The albums by letter when there are too many for the car to take as one list ([CarIndex]), else null. */
    private suspend fun albumGroups(): List<CarGroup<Album>>? = CarIndex.groups(library.albums()) { it.name }
    private suspend fun artistGroups(): List<CarGroup<Artist>>? = CarIndex.groups(library.artists()) { it.name }

    /** A letter of [list] to open: how many it holds, or from which name to which when the letter is in parts. */
    private fun letterItem(list: CarId, group: CarGroup<*>, what: String): MediaItem =
        folder(CarId.Letter(list, group.key), group.key, subtitle = group.range ?: "${group.entries.size} $what", grid = list == CarId.Albums)

    private suspend fun home(): List<MediaItem> {
        val recent = if (library.recent().any { recentItem(it) != null }) listOf(folder(CarId.Recent, "Recently played")) else emptyList()
        return recent +
            library.mixes().map { mixItem(it).grouped("Your mixes") } +
            library.moods().map { moodItem(it).grouped("Mood radios") }
    }

    /** An artist's songs with each album's together, in the order its albums first come. */
    private suspend fun artistSongs(id: String): List<Song> = library.artistSongs(id).groupBy { it.album }.values.flatten()

    private suspend fun songsOf(list: CarId): List<Song> = when (list) {
        is CarId.Album -> library.albumSongs(list.id)
        is CarId.Playlist -> library.playlist(list.id)?.songs.orEmpty()
        is CarId.Mix -> library.mix(list.id)?.songs.orEmpty()
        is CarId.Artist -> artistSongs(list.id)
        is CarId.Mood -> library.moodSongs(list.id)
        is CarId.Search -> library.search(list.query).songs
        else -> emptyList()
    }

    /** [songs] of [list] played from the start, with the page it came from for Home's shortcuts. */
    private suspend fun whole(list: CarId, songs: List<Song>): CarQueue {
        val cover = songs.firstOrNull()?.cover
        return when (list) {
            is CarId.Album -> CarQueue(songs, route = route("album", list.id), title = library.album(list.id)?.name ?: songs.first().album, cover = cover)
            is CarId.Playlist -> CarQueue(songs, route = route("playlist", list.id), title = library.playlist(list.id)?.playlist?.name, cover = cover)
            is CarId.Mix -> CarQueue(songs, route = route("mix", list.id), title = library.mix(list.id)?.title, cover = cover)
            is CarId.Artist -> CarQueue(songs, route = route("artist", list.id), title = library.artist(list.id)?.name, cover = cover)
            is CarId.Mood -> CarQueue(songs, route = "radio/mood/" + routePart(list.id), title = library.moods().firstOrNull { it.id == list.id }?.name, cover = cover)
            else -> CarQueue(songs)
        }.let { if (it.title == null) it.copy(route = null) else it }
    }

    /** A shortcut on Home as the car shows it, or null for the kinds the car can't open (books, cards and such). */
    private fun recentItem(play: RecentPlay): MediaItem? {
        val parts = play.route.split('/')
        val id = when {
            parts.size == 2 && parts[0] == "album" -> CarId.Album(routeValue(parts[1]))
            parts.size == 2 && parts[0] == "playlist" -> CarId.Playlist(routeValue(parts[1]))
            parts.size == 2 && parts[0] == "mix" -> CarId.Mix(routeValue(parts[1]))
            parts.size == 2 && parts[0] == "artist" -> CarId.Artist(routeValue(parts[1]))
            parts.size == 3 && parts[0] == "radio" && parts[1] == "mood" -> CarId.Mood(routeValue(parts[2]))
            else -> return null
        }
        if (id is CarId.Mood && library.moods().none { it.id == id.id }) return null
        val artwork = play.cover?.let(coverUri)
        return if (id is CarId.Mood) playable(id, play.title, "Mood radio", artwork) else folder(id, play.title, artwork = artwork)
    }

    private fun mixItem(mix: Mix) = folder(CarId.Mix(mix.id), mix.title, subtitle = "${mix.songs.size} songs", artwork = mix.songs.firstOrNull()?.cover?.let(coverUri))
    private fun moodItem(radio: MoodRadio) = playable(CarId.Mood(radio.id), radio.name, "Mood radio", null)
    private fun playlistItem(p: Playlist) = folder(CarId.Playlist(p.id), p.name, subtitle = "${p.songCount} songs", artwork = p.coverArt?.let(coverUri))
    private fun albumItem(a: Album) = folder(CarId.Album(a.id), a.name, subtitle = a.artist.ifBlank { null }, artwork = a.coverArt?.let(coverUri), playableToo = true)
    private fun artistItem(a: Artist) = folder(CarId.Artist(a.id), a.name, artwork = coverUri(a.picture), playableToo = true)

    private fun songItem(song: Song, list: CarId, group: String?): MediaItem {
        val extras = Bundle().apply { if (group != null) putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group) }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setSubtitle(song.artist)
            .setArtworkUri(song.cover?.let(coverUri))
            .setDurationMs(song.durationSec.takeIf { it > 0 }?.let { it * 1000L })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder().setMediaId(CarId.Song(list, song.id).mediaId).setMediaMetadata(metadata).build()
    }

    private fun playable(id: CarId, title: String, subtitle: String?, artwork: Uri?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        return MediaItem.Builder().setMediaId(id.mediaId).setMediaMetadata(metadata).build()
    }

    /**
     * A list to open. [grid] shows what's in it as a grid of covers (albums); [playableToo] lets the car offer to play
     * it whole as well.
     */
    private fun folder(id: CarId, title: String, subtitle: String? = null, artwork: Uri? = null, grid: Boolean = false, playableToo: Boolean = false): MediaItem {
        val extras = Bundle().apply {
            if (grid) putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artwork)
            .setIsBrowsable(true)
            .setIsPlayable(playableToo)
            .setMediaType(
                when (id) {
                    is CarId.Album -> MediaMetadata.MEDIA_TYPE_ALBUM
                    is CarId.Artist -> MediaMetadata.MEDIA_TYPE_ARTIST
                    is CarId.Playlist, is CarId.Mix -> MediaMetadata.MEDIA_TYPE_PLAYLIST
                    else -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                },
            )
            .setExtras(extras)
            .build()
        return MediaItem.Builder().setMediaId(id.mediaId).setMediaMetadata(metadata).build()
    }

    /** [this] shown under the heading [group] in its list. */
    private fun MediaItem.grouped(group: String): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group) }
        return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    companion object {
        /** The longest list handed to the car at once: longer ones are paged, and the car can't scroll far anyway. */
        const val MAX_ITEMS = 1000

        /** Page [page] of [items], [size] long, within the first [MAX_ITEMS]. */
        fun <T> page(items: List<T>, page: Int, size: Int): List<T> {
            val capped = items.take(MAX_ITEMS)
            val from = page.toLong() * size.coerceAtLeast(1)
            if (from >= capped.size) return emptyList()
            return capped.subList(from.toInt(), minOf(capped.size.toLong(), from + size).toInt())
        }

        /** A route for Home's shortcuts, filled in as the app's own pages note it. */
        fun route(kind: String, id: String): String = "$kind/" + routePart(id)

        private fun routePart(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private fun routeValue(part: String): String = runCatching { URLDecoder.decode(part, "UTF-8") }.getOrDefault(part)
    }
}
