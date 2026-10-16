package app.musix.mix

import app.musix.AppGraph
import app.musix.data.Artist
import app.musix.data.Song
import app.musix.log.AppLog

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

/** Builds radio queues. Every function returns songs ready to play, or throws with a readable reason. */
class Radios(private val graph: AppGraph) {

    suspend fun mood(radio: MoodRadio): List<Song> {
        AppLog.i("Radio", "Mood radio ${radio.name}")
        val songs = graph.generator.soundSearch(radio.query, 120)
        if (songs.isEmpty()) throw MixException("AudioMuse found nothing for ${radio.name}")
        return varied(songs, 60)
    }

    suspend fun genre(name: String): List<Song> {
        AppLog.i("Radio", "Genre radio $name")
        val songs = graph.library.songsOfGenre(name, 300)
        if (songs.isEmpty()) throw MixException("No synced songs in $name")
        return varied(songs.shuffled(), 80)
    }

    /** Songs similar to [song], from Navidrome (sonic matches when the AudioMuse plugin is installed). */
    suspend fun song(song: Song): List<Song> {
        AppLog.i("Radio", "Song radio ${song.id} ${song.title}")
        val similar = runCatching { graph.subsonic.similarSongs(song.id, 60) }
            .onFailure { AppLog.w("Radio", "getSimilarSongs failed", it) }
            .getOrDefault(emptyList())
        val list = listOf(song) + local(similar).filter { it.id != song.id }
        if (list.size < 2) throw MixException("Navidrome has no similar songs for this one")
        return list
    }

    suspend fun artist(artist: Artist): List<Song> {
        AppLog.i("Radio", "Artist radio ${artist.id} ${artist.name}")
        val similar = runCatching { graph.subsonic.artistRadio(artist.id, 60) }
            .onFailure { AppLog.w("Radio", "getSimilarSongs2 failed", it) }
            .getOrDefault(emptyList())
        if (similar.size >= 5) return varied(local(similar), 60)
        val own = graph.library.songsOf(artist)
        if (own.isEmpty()) throw MixException("No songs found for ${artist.name}")
        return own.shuffled().take(60)
    }

    /** The synced copies of songs from the server, which carry hearts and ReplayGain as stored. */
    private suspend fun local(songs: List<Song>): List<Song> {
        val found = graph.library.songs(songs.map { it.id }).associateBy { it.id }
        return songs.map { found[it.id] ?: it }
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
