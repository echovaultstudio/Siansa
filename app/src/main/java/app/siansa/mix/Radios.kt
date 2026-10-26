package app.siansa.mix

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
        val songs = graph.library.songsOfGenre(name, 300)
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

    /** A few of the artist's own songs, from the phone, so the radio starts at once. */
    suspend fun artistStart(artist: Artist): List<Song> = graph.library.songsOf(artist).shuffled().take(6)

    /** Songs by the artist and similar artists from Navidrome, added after the start. */
    suspend fun artistMore(artist: Artist, already: List<Song>): List<Song> {
        AppLog.i("Radio", "Artist radio ${artist.id} ${artist.name}")
        val have = already.map { it.id }.toSet()
        val similar = runCatching { graph.source.artistRadio(artist.id, 60) }
            .onFailure { AppLog.w("Radio", "getSimilarSongs2 failed", it) }
            .getOrDefault(emptyList())
        if (similar.isNotEmpty()) return varied(local(similar), 60).filter { it.id !in have }
        return graph.library.songsOf(artist).shuffled().filter { it.id !in have }.take(60)
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
