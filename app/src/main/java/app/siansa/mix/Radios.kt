package app.siansa.mix

import app.siansa.net.soundReady
import app.siansa.AppGraph
import app.siansa.data.Artist
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.MoodCentre

/** A ready-made mood radio: an AudioMuse sound search with its own tile colours. */
data class MoodRadio(val id: String, val name: String, val query: String, val colors: Pair<Long, Long>)

val moodRadios = listOf(
    MoodRadio("chill", "Chill", "chill relaxed laid back mellow grooves, soft beats", 0xFF2E8B9A to 0xFF143A52),
    MoodRadio("energy", "Energy", "energetic upbeat driving powerful fast tempo", 0xFFE8572A to 0xFF7A1530),
    MoodRadio("focus", "Focus", "calm instrumental steady focus music, no vocals, ambient electronic", 0xFF4B5FD6 to 0xFF1C1F55),
    MoodRadio("feelgood", "Feel good", "happy cheerful sunny feel good positive music", 0xFFF2B33D to 0xFFD9542B),
    MoodRadio("party", "Party", "danceable party club music, big beats, high energy", 0xFFD6338A to 0xFF4A1470),
    MoodRadio("melancholy", "Melancholy", "sad melancholic emotional slow music, minor key", 0xFF5D6B8A to 0xFF1E2433),
    MoodRadio("workout", "Workout", "intense workout music, aggressive heavy fast beat", 0xFFC62828 to 0xFF2B0A0A),
    MoodRadio("latenight", "Late night", "dark atmospheric late night electronic, deep and moody", 0xFF3A2D6B to 0xFF0D0B1F),
    MoodRadio("acoustic", "Acoustic", "acoustic guitar singer songwriter, warm and intimate", 0xFF9C6B3C to 0xFF3D2614),
    MoodRadio("romance", "Romance", "romantic smooth soulful love songs, warm vocals", 0xFFC2416B to 0xFF4D0F2A),
)

/** The sound search behind the sleep timer's wind down: like Chill, but slower and quieter. */
const val WIND_DOWN_QUERY = "calm slow quiet soft gentle peaceful relaxing sleep music, ambient, soft piano, mellow acoustic"

/** At most [max] of [items], spread evenly through the list. */
fun <T> evenly(items: List<T>, max: Int): List<T> =
    if (items.size <= max) items else List(max) { i -> items[i * items.size / max] }

/** A radio's songs, and which of them AudioMuse picked ([picked], marked in the queue like a song radio's). */
data class RadioSongs(val songs: List<Song>, val picked: Set<String> = emptySet())

/** Builds radio queues. Every function returns songs ready to play, or throws with a readable reason. */
class Radios(private val graph: AppGraph) {

    suspend fun mood(radio: MoodRadio): List<Song> {
        AppLog.i("Radio", "Mood radio ${radio.name}")
        val songs = graph.generator.soundSearch(radio.query, 120)
        if (songs.isEmpty()) throw MixException("AudioMuse found nothing for ${radio.name}")
        return varied(songs, 60)
    }

    /** Calm, slow songs for the sleep timer's wind down. */
    suspend fun windDown(): List<Song> {
        AppLog.i("Radio", "Wind down")
        val songs = graph.generator.soundSearch(WIND_DOWN_QUERY, 80)
        if (songs.isEmpty()) throw MixException("AudioMuse found no calm songs")
        return varied(songs, 30)
    }

    suspend fun genre(name: String): List<Song> {
        AppLog.i("Radio", "Genre radio $name")
        // The songs Siansa Beats hears in the genre when it's there (a random 300 of them); the songs' tags otherwise.
        val heard = graph.beatsGenres.songIds(name, 300)?.let { ids ->
            graph.library.keepSyncedSongs(graph.library.songs(ids), "Genre radio").distinctBy { it.id }
        }
        val songs = BeatsGenres.pick(name, heard) { graph.library.songsOfGenre(name, 300) }
        if (songs.isEmpty()) throw MixException("No synced songs in $name")
        return varied(songs.shuffled(), 80)
    }

    /** Songs similar to [song]: Navidrome's sonic similarity, AudioMuse, or Navidrome's similar songs (issue #208). */
    suspend fun songMore(song: Song): List<Song> {
        AppLog.i("Radio", "Song radio ${song.id} ${song.title}")
        val hits = graph.sonic.similar(song.id, 60).filter { it.id != song.id }
        return graph.library.keepSyncedSongs(graph.generator.resolveHits(hits), "Radio")
    }

    /** A radio around one of AudioMuse's own mood centres (issue #208). */
    suspend fun centre(centre: MoodCentre): List<Song> {
        AppLog.i("Radio", "Mood centre radio ${centre.mood} ${centre.index} (${centre.tags.joinToString()})")
        val hits = graph.audioMuse.moodRadio(centre, 120)
        val songs = graph.generator.resolveHits(hits)
        if (songs.isEmpty()) throw MixException("AudioMuse found nothing for this mood")
        return varied(songs, 60)
    }

    /**
     * Song Alchemy (issue #208): songs that sound like a blend of [songs] (at most 20 of them, picked evenly),
     * leaving out the songs themselves.
     */
    suspend fun blend(songs: List<Song>): List<Song> {
        val seeds = evenly(songs.map { it.id }.distinct(), 20)
        if (seeds.isEmpty()) throw MixException("No songs to blend")
        AppLog.i("Radio", "Song alchemy of ${seeds.size} songs")
        val hits = graph.audioMuse.alchemy(seeds, count = 80).filter { it.id !in seeds }
        val found = graph.generator.resolveHits(hits)
        if (found.isEmpty()) throw MixException("AudioMuse found nothing like these songs")
        return varied(found, 60)
    }

    /**
     * The songs of the radio whose page is [route] (a favourite radio's, for its card on Home, the owner, 10 Nov): a
     * sound or mood radio, a genre's radio (some of its songs, as its radio starts), or "More like" a playlist. Throws
     * with a readable reason for a radio that can't be made now.
     */
    suspend fun ofRoute(route: String): List<Song> {
        val parts = route.split('/').map { android.net.Uri.decode(it) }
        return when {
            parts.size == 3 && parts[0] == "radio" && parts[1] == "mood" ->
                mood(moodRadios.firstOrNull { it.id == parts[2] } ?: throw MixException("This radio isn't in Siansa any more"))
            parts.size == 3 && parts[0] == "radio" && parts[1] == "centre" ->
                centre(graph.audioMuse.moodCentres().firstOrNull { app.siansa.net.AudioMuseApi.centreKey(it) == parts[2] }
                    ?: throw MixException("AudioMuse doesn't have this mood any more"))
            parts.size == 3 && parts[0] == "radio" && parts[1] == "genre" -> {
                val all = graph.genreRadio.songsOf(parts[2])
                if (all.isEmpty()) throw MixException("No songs of ${parts[2]} on the phone")
                GenreRadio.firstPicks(all, null, GenreRadio.PICKS, kotlin.random.Random.Default, graph.beatsGenres.weightsOf(parts[2]))
            }
            parts.size == 2 && parts[0] == "blend" -> {
                val id = parts[1]
                val songs = if (app.siansa.data.LocalPlaylists.isLocal(id)) {
                    graph.localPlaylists.get(id)?.songs ?: throw MixException("This playlist isn't on this phone any more")
                } else graph.playlistCache.get(id).songs
                blend(songs)
            }
            else -> throw MixException("Not a radio Siansa can make a card of")
        }
    }

    /** A few of the artist's own songs, from the phone, so the radio starts at once. */
    suspend fun artistStart(artist: Artist): List<Song> = graph.library.songsOf(artist).shuffled().take(6)

    /**
     * The rest of an artist radio, added after the start: songs that sound like the artist from AudioMuse-AI when
     * it's set up, with the artist's own songs among them; otherwise the music server's similar songs (Navidrome's
     * from Last.fm and the like); otherwise the artist's own songs.
     */
    suspend fun artistMore(artist: Artist, already: List<Song>): RadioSongs {
        AppLog.i("Radio", "Artist radio ${artist.id} ${artist.name}")
        val have = already.map { it.id }.toSet()
        val own = graph.library.songsOf(artist).shuffled()
        if (graph.settings.current.soundReady && own.isNotEmpty()) {
            val alike = runCatching { soundsLike(own) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; AppLog.w("Radio", "AudioMuse couldn't make the artist radio: ${it.message}; asking the music server") }
                .getOrDefault(emptyList())
            if (alike.isNotEmpty()) {
                val songs = ArtistRadioMix.mix(own, alike, have, 60)
                val ownIds = own.map { it.id }.toSet()
                AppLog.i("Radio", "Artist radio ${artist.name}: ${songs.size} songs, ${songs.count { it.id in ownIds }} of theirs, the rest sound like them (AudioMuse, from ${minOf(own.size, ArtistRadioMix.SEEDS)} of their songs)")
                // AudioMuse's picks are marked in the queue; the artist's own songs aren't.
                return RadioSongs(songs, ArtistRadioMix.picked(songs, own))
            }
        }
        val similar = runCatching { graph.source.artistRadio(artist.id, 60) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; AppLog.w("Radio", "The music server's similar songs failed", it) }
            .getOrDefault(emptyList())
        if (similar.isNotEmpty()) {
            AppLog.i("Radio", "Artist radio ${artist.name}: the music server's similar songs (${similar.size})")
            return RadioSongs(varied(local(similar), 60).filter { it.id !in have })
        }
        AppLog.i("Radio", "Artist radio ${artist.name}: no similar songs anywhere; their own songs")
        return RadioSongs(own.filter { it.id !in have }.take(60))
    }

    /** Songs that sound like a blend of [own] (some of an artist's songs), from AudioMuse, best first. */
    private suspend fun soundsLike(own: List<Song>): List<Song> {
        val seeds = evenly(own.map { it.id }.distinct(), ArtistRadioMix.SEEDS)
        val hits = graph.audioMuse.alchemy(seeds, count = 120).filter { it.id !in seeds }
        return graph.library.keepSyncedSongs(graph.generator.resolveHits(hits), "Radio")
    }

    /** The synced copies of songs from the server, which carry hearts and ReplayGain as stored; none from unchecked libraries. */
    private suspend fun local(songs: List<Song>): List<Song> {
        val found = graph.library.songs(songs.map { it.id }).associateBy { it.id }
        return graph.library.keepSyncedSongs(songs.map { found[it.id] ?: it }, "Radio")
    }

    /** At most two songs per artist in a row of picks, then shuffled lightly. */
    private fun varied(songs: List<Song>, size: Int): List<Song> {
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<Song>()
        for (song in songs) {
            if (out.size >= size) break
            val key = song.artist.lowercase()
            val n = perArtist[key] ?: 0
            if (n >= 3) continue
            perArtist[key] = n + 1
            out += song
        }
        return out.shuffled()
    }
}
