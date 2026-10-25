package app.siansa.mix

import app.siansa.data.Song
import kotlin.random.Random

/**
 * A daily mix picked from the music shown, for when AudioMuse can't make it (beta #25): no AudioMuse on this
 * server, or the music shown isn't what AudioMuse analysed (kids' own library). Songs whose genre fits the vibe's
 * words ("folk", "funk", "ambient") come first, then genres usually played at this hour, then the rest, with a
 * little chance mixed in so each day differs. Recently played, skipped and most played songs stay out while there's
 * enough else, and no artist fills the mix.
 */
object LibraryMix {
    /** Words too common in a vibe's text, or in genre names, to say anything about the sound. */
    private val STOP = setOf(
        "music", "songs", "song", "and", "the", "with", "for", "from", "that", "this", "into", "very", "some", "more",
        "less", "feel", "feeling", "vocals", "vocal", "male", "female", "voice", "voices", "sound", "sounds", "track",
        "tracks", "mix", "playlist", "your", "you", "are", "not", "but", "like", "all", "any", "other", "misc",
    )

    /** The words of a text that may name a genre: lower case, three letters or more, common words left out. */
    fun words(text: String?): Set<String> =
        text.orEmpty().lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.toSet()

    /** How well [song] fits: 2 for a genre the vibe names, 1 for one usually played at this hour, else 0. */
    fun fit(song: Song, vibe: Set<String>, hourGenres: Set<String>): Int {
        val genre = song.genre?.trim()?.lowercase().orEmpty()
        if (genre.isEmpty()) return 0
        return when {
            words(genre).any { it in vibe } -> 2
            genre in hourGenres -> 1
            else -> 0
        }
    }

    /**
     * Up to [length] of [candidates] for a vibe described by [vibeText]. [avoid] (recent, skipped, most played) is
     * only used when nothing else is left; [exclude] (another mix's songs today) never; [otherArtists] (the other
     * mixes' artists) weigh less.
     */
    fun pick(
        candidates: List<Song>,
        vibeText: String,
        hourGenres: Collection<String>,
        length: Int,
        avoid: Set<String> = emptySet(),
        exclude: Set<String> = emptySet(),
        otherArtists: Set<String> = emptySet(),
        random: Random = Random,
    ): List<Song> {
        if (length <= 0) return emptyList()
        val vibe = words(vibeText)
        val hour = hourGenres.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val pool = candidates.distinctBy { it.id }.filter { it.id !in exclude }
        val artists = pool.map { it.artist.lowercase() }.distinct().size
        val perArtist = if (artists >= length / 2) 2 else maxOf(2, (length + artists - 1) / maxOf(artists, 1))
        val scored = pool.map { song ->
            val other = if (song.artist.lowercase() in otherArtists) 0.5 else 0.0
            song to fit(song, vibe, hour) + random.nextDouble() * 1.2 - other
        }
        val (fresh, tired) = scored.partition { it.first.id !in avoid }
        val counts = HashMap<String, Int>()
        val out = ArrayList<Song>(length)
        for ((song, _) in fresh.sortedByDescending { it.second } + tired.sortedByDescending { it.second }) {
            if (out.size >= length) break
            val key = song.artist.lowercase()
            val n = counts[key] ?: 0
            if (n >= perArtist) continue
            counts[key] = n + 1
            out += song
        }
        return out
    }
}
